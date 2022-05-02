package com.messagenetsystems.evolutionflasherlights.bluetooth;

/* BluetoothGattCallback
 * Defines GATT client and callbacks.
 * This is where we actually send a light command to the light controller device.
 * There is a specific workflow that must occur for the command to actually be written to the device....
 *
 * NOTE: This is intended to be closely integrated with this app, and depends on MainService's
 *       publicly-available and static assets and resources for it to operate.
 *
 * Here is the order things happen....
 * Basically, this will wait for the connection, and then cascade toward finally sending the command, and then disconnecting once it's sent.
 *      1. Connect to GATT (see usage example below)
 *      2. onConnectionStateChange: if connected, discover all device's services (gatt.discoverServices)
 *      3. onServicesDiscovered: write the characteristic when this fires
 *      4. onCharacteristicWrite (fires when the characteristic is written to the device)
 *
 * *** SO BASICALLY.... Just invoke BluetoothGatt.connectGatt (passing in an instance of this class, which includes the command) to make the lights do something! ***
 *
 * What we can get from the ScanResult object:
 *  - BluetoothDevice object (which is the main guy we'll use)
 *  - Various other bits of information (e.g. signal strength, etc.)
 *
 * To use:
 *  private BluetoothGattCallback bluetoothGattCallback;                                                 // Declare a local instance
 *  bluetoothGattCallback = new BluetoothGattCallback(lightCmd, lightCmdAddit, currLightCmd_asDecimal);  // Instantiate the local instance  TODO-redocument these
 *  bluetoothGattCallback.connectGatt(this, true, gattClientCallback).connect();                         // Pass the instance to the device's connectGatt method (2nd arg is autoconnect - true is best?)
 *
 * Revisions:
 *  2020.05.29      Chris Rider     Created (originally subclass in BluetoothFlasherLightsService).
 *  2020.06.10      Chris Rider     Updated/minor-refactoring to fit into the overall app refactoring.
 *  2020.06.11      Chris Rider     Refactoring again to make this actually do the command sending and live as an instance per command.
 *  2020.06.21      Chris Rider     Up until now, numerous tweaks and stuff to try to get commands more stable/reliable, and to work around 133 errors, etc.
 *  2020.06.22      Chris Rider     Added broadcast to trigger bluetooth device re-acquisition (to try to remedy error 133, and supposed Android BT stack issues).
 *  2020.06.25      Chris Rider     Refactored whole app to shift work from BluetoothService to MainApplication.
 *  2020.06.29      Chris Rider     Updating notifications with light status.
 *  2020.07.01      Chris Rider     Added some extra logic to try to check for and clear out old connections before beginning a new one - doesn't really prevent our 133 error, but can't hurt.
 *  2020.07.02      Chris Rider     Minor refactoring of onConnectionStateChange callback, also now executing standby light mode if 133 encountered -best we can do for now, at least not indicate wrong message to user -doesn't work every time but better?
 *  2020.07.06      Chris Rider     Trying out some service-discovery retry mechanism (doesn't seem to work any better), also implemented some null-error catches.
 */

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.Constants;
import com.messagenetsystems.evolutionflasherlights.MainApplication;
import com.messagenetsystems.evolutionflasherlights.utilities.ConversionUtils;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;


public class BluetoothGattCallback extends android.bluetooth.BluetoothGattCallback {
    private String TAG = BluetoothGattCallback.class.getSimpleName();

    // Constants...
    private final byte STATUS_CUSTOM_NONE = 0;
    private final byte STATUS_CUSTOM_SERVICE_DISCOVERY_PROBLEM = 1;
    private final byte STATUS_CUSTOM_CHARACTERISTIC_WRITE_PROBLEM = 2;

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    // Local stuff...
    private WeakReference<Context> appContextRef;
    private MainApplication mainApplication;
    private byte flasherLightCodeToDo;
    private List<byte[]> characteristicValuesToWrite;
    private volatile UUID serviceUUID;
    private UUID characteristicUUID;

    //private BluetoothGattService bluetoothGattService;
    //private BluetoothGattCharacteristic bluetoothGattCharacteristic;

    private int characteristicIndexToWrite;

    private byte customStatus;


    /** Constructor */
    public BluetoothGattCallback(Context context, int logMethod, @Nullable List<byte[]> characteristicValuesToWrite) {
        super();

        this.appContextRef = new WeakReference<Context>(context);
        this.logMethod = logMethod;

        try {
            this.mainApplication = ((MainApplication) context.getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        this.flasherLightCodeToDo = MainApplication.flasherLightOmniCommandCodes.CMD_UNKNOWN;

        if (characteristicValuesToWrite == null) {
            this.characteristicValuesToWrite = ConversionUtils.convertCommandCodeToBleCharacteristicValueList(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_STANDBY);
        } else {
            this.characteristicValuesToWrite = characteristicValuesToWrite;
        }

        this.characteristicIndexToWrite = 0;

        //this.bluetoothGattService = null;
        //this.bluetoothGattCharacteristic = null;

        this.serviceUUID = UUID.fromString(MainApplication.lightControllerDeviceModel.getUuidStr_mainService());
        this.characteristicUUID = UUID.fromString(MainApplication.lightControllerDeviceModel.getUuidStr_characteristicForWritingCommands());

        if (this.serviceUUID == null) {
            logE("No service UUID known!");
        }

        this.customStatus = STATUS_CUSTOM_NONE;

        logI("Instance created.");
    }


    /*============================================================================================*/
    /* Class Methods */

    /** Class-wide cleanup method */
    public void cleanup() {
        final String TAGG = "cleanup: ";

        //if (this.bluetoothGattService != null) {
        //    this.bluetoothGattService = null;
        //}

        //if (this.bluetoothGattCharacteristic != null) {
        //    this.bluetoothGattCharacteristic = null;
        //}

        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }

        this.serviceUUID = null;
        this.characteristicUUID = null;

        //BluetoothService.callbackInstanceCount--;
        //BluetoothService.lightCmdIsUnderway = false;

        this.mainApplication.isBluetoothDeviceCommandUnderway = false;

        this.mainApplication.isBluetoothGattConnectionUnderway = false;
    }

    private void refreshDeviceCache(BluetoothGatt gatt) {
        final String TAGG = "refreshDeviceCache: ";

        try {
            Method localMethod = gatt.getClass().getMethod("refresh");
            if(localMethod != null) {
                localMethod.invoke(gatt);
            }
        } catch(Exception localException) {
            Log.d(TAGG+"Exception caught: ", localException.toString());
        }
    }


    /*============================================================================================*/
    /* BluetoothGattCallback Methods */
    /* DEV-NOTE: It seems to be problematic if you rely on the methods' gatt object to close, so invoke close() on the global BluetoothGatt object instead! */

    final byte serviceDiscoveryMaxRetries = 2;
    byte serviceDiscoveryRetries = 0;

    @Override
    public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        final String TAGG = "onConnectionStateChange: ";

        if (status == GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // We're not really bonding in our scheme here, but it's "proper" to handle it in case
                // Take action depending on the bond state (which, if in play, would dictate how/when we continue)
                int bondState = mainApplication.bluetoothDevice.getBondState();
                if (bondState == BOND_NONE || bondState == BOND_BONDED) {
                    // Connected to device, now proceed to discover its services but delay a bit if needed
                    // With Android versions prior to 8 (7 and lower), if the device has the "Service Changed Characteristic",
                    // the Android stack will still be busy handling it and calling discoverServices() w/out a delay would make
                    // it fail, so you have to add a 1000-1500ms delay. The exact time needed depends on the number of
                    // characteristics of your device. Since at this point, you don't know yet if the device has this characteristic,
                    // it is best to simply always do the delay.
                    int delayWhenBonded = 50;
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                        delayWhenBonded = 1000;
                    }
                    final int delay = bondState == BOND_BONDED ? delayWhenBonded : 50;
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            logV(TAGG+"Discovering services with "+delay+"ms delay.");
                            if (!gatt.discoverServices()) {
                                logE(TAGG+"discoverServices failed to start.");
                            }
                        }
                    }, delay);
                }  else if (bondState == BOND_BONDING) {
                    // Bonding process in progress, let it complete
                    // Stack would be busy in this case and service discovery unavailable
                    logW(TAGG+"Waiting for bonding to complete.");
                } else {
                    // Unhandled case
                    logE(TAGG+"Unhandled bond state.");
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // We successfully disconnected on our own request
                mainApplication.replaceNotificationWithLightStatus(flasherLightCodeToDo, " completed and", true);
                gatt.close();
                cleanup();
            } else {
                // We're CONNECTING or DISCONNECTING, ignore for now as it isn't really implemented anyway
            }
        } else {
            // An error happened... figure it out

            if (status == 19) {
                // GATT_CONN_TERMINATE_PEER_USER
                // The device disconnected itself on purpose.
                // For example, all data has been transferred and there is nothing else to do.
                logW(TAGG+"Device has disconnected itself on purpose. Closing.");
                gatt.close();
                cleanup();
            } else if (status == 8) {
                // GATT_CONN_TIMEOUT
                // The connection timed out and device disconnected itself.
                logW(TAGG+"Connection timed-out and device disconnected itself. Closing.");
                gatt.close();
                cleanup();
            } else if (status == 133) {
                // GATT_ERROR (this really means nothing, thanks to Android's poor implementation)
                // There was a low-level error in the communication which led to loss of connection.
                logE(TAGG+"Status 133 (low-level error / loss of connection / failure to connect). ");
                mainApplication.problemCount_status133++;
                mainApplication.replaceNotificationWithText("ERROR: Device connection problem! Executing standby light mode...");

                // Try to at least reset lights to default standby to clear out any old modes
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            mainApplication.executeLightCommand(
                                    mainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_STANDBY,
                                    Long.MAX_VALUE,
                                    null,
                                    true
                            );
                        } catch (Exception e) {
                            logE(TAGG+"Exception caught: "+e.getMessage());
                        }
                    }
                }, Constants.LIGHT_COMMAND_TIMEOUT_MS+1);  //ensure we run this after any delayed independent resource cleanup runnable

                gatt.close();
                cleanup();
            } else {
                logE(TAGG + "An error occurred.");
                //TODO
                gatt.close();
                cleanup();
            }
        }

        /* ORIGINAL
        switch (newState) {
            case BluetoothProfile.STATE_CONNECTED:
                logV(TAGG + "CONNECTED (status " + status + ")");
                this.customStatus = STATUS_CUSTOM_NONE;

                this.mainApplication.isBluetoothGattConnectionUnderway = true;

                // Try to avoid common problems in BLE from discovering immediately after establishing connection
                doSleep(500);

                // Request services available on the device...
                try {
                    gatt.discoverServices();
                    // (now, we wait for onServicesDiscovered to fire.)
                } catch (Exception e) {
                    logE(TAGG + "Exception caught (problem with BluetoothGatt provided in callback?), disconnecting: " + e.getMessage());
                    gatt.disconnect();
                }
                break;
            case BluetoothProfile.STATE_DISCONNECTED:
                logV(TAGG + "DISCONNECTED (status " + status + ")");
                if (status == 19            //peer disconnected us?
                        || status == 133    //infamous 133 (0x85 GATT_ERROR), unclear what it means, probably unreleased prior connection somewhere in the stack
                        || status == 257    //max client connections reached?
                        ) {
                    logE(TAGG + "Unexpected disconnection! Closing GATT, and you should try connecting again elsewhere.");
                    gatt.disconnect();
                    gatt.close();
                    mainApplication.bluetoothGatt.disconnect(); //is this proper? trying it just to try to force things more
                    mainApplication.bluetoothGatt.close();
                    mainApplication.bluetoothDevice = null;
                    mainApplication.replaceNotificationWithText("Device connection problem encountered.");
                } else if (customStatus == STATUS_CUSTOM_SERVICE_DISCOVERY_PROBLEM) {
                    logE(TAGG + "Problem during service discovery! Closing GATT, and you should try connecting again elsewhere.");
                    mainApplication.bluetoothGatt.disconnect(); //is this proper? trying it just to try to force things more
                    mainApplication.bluetoothGatt.close();
                    mainApplication.replaceNotificationWithText("Device service discovery problem encountered.");
                } else {
                    logV(TAGG + "Light command sequence has ended. Closing GATT.\n");
                    mainApplication.bluetoothGatt.disconnect(); //is this proper? trying it just to try to force things more
                    mainApplication.bluetoothGatt.close();
                    mainApplication.replaceNotificationWithLightStatus(flasherLightCodeToDo, true);
                }
                cleanup();
                break;
            default:
                logW(TAGG + "Unhandled newState (" + Integer.toString(newState) + "). Disconnecting GATT.");
                gatt.disconnect();
                break;
        }
        */
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        final String TAGG = "onServicesDiscovered: ";

        switch (status) {
            case GATT_SUCCESS:
                logV(TAGG + "Service discovery responded. Processing...");

                try {

                    if (this.serviceUUID == null) {
                        if (MainApplication.lightControllerDeviceModel.getUuidStr_mainService() == null) {
                            logE(TAGG + "Service UUID unavailable! Must abort. Disconnecting...");
                            gatt.disconnect();
                            return;
                        } else {
                            logW(TAGG + "Service UUID unavailable in this instance, getting from MainApplication...");
                            this.serviceUUID = UUID.fromString(MainApplication.lightControllerDeviceModel.getUuidStr_mainService());
                        }
                    }

                    // Get the service we care about from whatever services the device responded with...
                    BluetoothGattService bluetoothGattService = gatt.getService(this.serviceUUID);

                    // Check if we have our service and can continue...
                    // If not, then try an alternative methods to get it
                    if (bluetoothGattService == null) {
                        logW(TAGG + "Unable to directly get GATT service for UUID \"" + String.valueOf(this.serviceUUID) + "\"");

                        //ORIGINAL SIMPLE...
                        //logE(TAGG + "Unable to find appropriate service. Disconnecting GATT and cancelling this session...");
                        //this.customStatus = STATUS_CUSTOM_SERVICE_DISCOVERY_PROBLEM;    //set our flag that the onDisconnect can catch on
                        //gatt.disconnect();

                        if (gatt.getServices() != null) {
                            logD(TAGG + "Searching all available services...");
                            for (BluetoothGattService gattService : gatt.getServices()) {
                                if (gattService.getUuid() != null) {
                                    logV(TAGG + "  checking available service \"" + gattService.getUuid().toString() + "\"");
                                    if (this.serviceUUID.toString().equals(gattService.getUuid().toString())) {
                                        logV(TAGG + "  Found!");
                                        bluetoothGattService = gattService;
                                        break;
                                    }
                                }
                            }
                        }

                        if (bluetoothGattService == null) {
                            if (serviceDiscoveryRetries < serviceDiscoveryMaxRetries) {
                                logW(TAGG + "Search yielded no appropriate service. Trying again...");
                                serviceDiscoveryRetries++;
                                doSleep(200);
                                gatt.discoverServices();
                            } else {
                                logE(TAGG + "Still not able to find appropriate service. Disconnecting GATT and cancelling this session...");
                                mainApplication.problemCount_serviceDiscovery++;
                                serviceDiscoveryRetries = 0;
                                this.customStatus = STATUS_CUSTOM_SERVICE_DISCOVERY_PROBLEM;    //set our flag that the onDisconnect can catch on
                                gatt.disconnect();
                            }
                        } else {
                            // Now that we have our service, we can actually write something to the device...
                            try {
                                //this.bluetoothGattCharacteristic = this.bluetoothGattService.getCharacteristic(this.characteristicUUID);
                                //this.bluetoothGattCharacteristic.setValue(this.characteristicValuesToWrite.get(characteristicIndexToWrite));
                                //gatt.writeCharacteristic(this.bluetoothGattCharacteristic);
                                BluetoothGattCharacteristic bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(this.characteristicUUID);
                                bluetoothGattCharacteristic.setValue(characteristicValuesToWrite.get(characteristicIndexToWrite));
                                gatt.writeCharacteristic(bluetoothGattCharacteristic);
                            } catch (Exception e) {
                                logE(TAGG + "Exception caught (will reset index counter): " + e.getMessage());
                                characteristicIndexToWrite = 0;
                            }
                        }
                    } else {
                        // Now that we have our service, we can actually write something to the device...
                        try {
                            //this.bluetoothGattCharacteristic = this.bluetoothGattService.getCharacteristic(this.characteristicUUID);
                            //this.bluetoothGattCharacteristic.setValue(this.characteristicValuesToWrite.get(characteristicIndexToWrite));
                            //gatt.writeCharacteristic(this.bluetoothGattCharacteristic);
                            BluetoothGattCharacteristic bluetoothGattCharacteristic = bluetoothGattService.getCharacteristic(this.characteristicUUID);
                            bluetoothGattCharacteristic.setValue(characteristicValuesToWrite.get(characteristicIndexToWrite));
                            gatt.writeCharacteristic(bluetoothGattCharacteristic);
                        } catch (Exception e) {
                            logE(TAGG + "Exception caught (will reset index counter): " + e.getMessage());
                            characteristicIndexToWrite = 0;
                        }
                    }

                } catch (Exception e) {
                    logE(TAGG + "Exception caught, aborting and disconnecting: "+e.getMessage());
                    gatt.disconnect();
                }

                break;
            default:
                logW(TAGG + "Unhandled status (" + Integer.toString(status) + "), disconnecting GATT...");
                this.customStatus = STATUS_CUSTOM_SERVICE_DISCOVERY_PROBLEM;
                gatt.disconnect();
                break;
        }
    }

    @Override
    // Result of a characteristic write operation
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        final String TAGG = "onCharacteristicWrite: ";
        logV(TAGG + "Invoked (status="+Integer.toString(status)+")");

        switch (status) {
            case GATT_SUCCESS:
                //logV(TAGG+"Success! Wrote: "+ Arrays.toString(characteristic.getValue()));                            //print decimal values
                logV(TAGG+"Success! Wrote: "+ ConversionUtils.byteArrayToHexString(characteristic.getValue(), ",")+". Disconnecting GATT...");    //print hex values

                // We only care that we wrote the first command in the potential list of many commands
                if (characteristicIndexToWrite == 0) {
                    mainApplication.mostRecentRootCharacteristicWrittenToDevice_value = characteristic.getValue();
                    mainApplication.mostRecentRootCharacteristicWrittenToDevice_datetime = new Date();
                }

                // Since we had a successful write, we can increment to next potential characteristic to write
                this.characteristicIndexToWrite++;

                // Check if we have another characteristic to write (e.g. to make it flash)
                // If not, just disconnect and finish up
                if (this.characteristicIndexToWrite < this.characteristicValuesToWrite.size()) {
                    doSleep(250);   //give time for the device/LE-protocol to be completely done with the previous command
                    characteristic.setValue(this.characteristicValuesToWrite.get(characteristicIndexToWrite));
                    gatt.writeCharacteristic(characteristic);
                } else {
                    // Trigger a disconnect since we're done
                    this.customStatus = STATUS_CUSTOM_NONE;
                    mainApplication.replaceNotificationWithLightStatus(flasherLightCodeToDo, " written.", false);
                    gatt.disconnect();
                }
                break;
            default:
                //TODO: try again?
                logW(TAGG+"Unhandled write-result status ("+Integer.toString(status)+"), disconnecting GATT...");
                this.customStatus = STATUS_CUSTOM_CHARACTERISTIC_WRITE_PROBLEM;
                gatt.disconnect();
                break;
        }
    }

    /** Triggered by BluetoothGatt.requestMtu */
    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        final String TAGG = "onMtuChanged: ";

        try {
            logV(TAGG + "MTU: " + String.valueOf(mtu));
        } catch (Exception e) {
            logW(TAGG + "Exception caught: "+e.getMessage());
        }
    }

    /** Triggered by BluetoothGatt.readRemoteRssi */
    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        final String TAGG = "onReadRemoteRssi: ";

        try {
            logV(TAGG + "RSSI: " + String.valueOf(rssi));
        } catch (Exception e) {
            logW(TAGG + "Exception caught: "+e.getMessage());
        }
    }

    /** Triggered by BluetoothGatt.readPhy */
    @Override
    public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        final String TAGG = "onPhyRead: ";
        logV(TAGG+"txPhy: "+txPhy+" / rxPhy: "+rxPhy);
    }

    /** Triggered by BluetoothGatt.setPreferredPhy */
    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        final String TAGG = "onPhyUpdate: ";
        logV(TAGG+"txPhy: "+txPhy+" / rxPhy: "+rxPhy);
    }


    /*============================================================================================*/
    /* Local Methods */

    private void doSleep(int ms) {
        final String TAGG = "doSleep("+Integer.toString(ms)+"): ";

        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            logW(TAGG+"Exception caught trying to sleep: "+ie.getMessage());
        }
    }


    /*============================================================================================*/
    /* Getter & Setter Methods */

    private void setCharacteristicValuesToWrite(List<byte[]> characteristicValuesCmdSeq) {
        this.characteristicValuesToWrite = characteristicValuesCmdSeq;
    }

    public void setFlasherLightCommandCodeToDo(Byte flasherLightCommandCodeToDo) {
        this.flasherLightCodeToDo = flasherLightCommandCodeToDo;
        setCharacteristicValuesToWrite(ConversionUtils.convertCommandCodeToBleCharacteristicValueList(flasherLightCommandCodeToDo));
    }

    public byte getFlasherLightCommandCodeToDo() {
        return this.flasherLightCodeToDo;
    }


    /*============================================================================================*/
    /* Logging Methods */

    private void logV(String tagg) {
        log(LOG_SEVERITY_V, tagg);
    }
    private void logD(String tagg) {
        log(LOG_SEVERITY_D, tagg);
    }
    private void logI(String tagg) {
        log(LOG_SEVERITY_I, tagg);
    }
    private void logW(String tagg) {
        log(LOG_SEVERITY_W, tagg);
    }
    private void logE(String tagg) {
        log(LOG_SEVERITY_E, tagg);
    }
    private void log(int logSeverity, String tagg) {
        switch (logMethod) {
            case Constants.LOG_METHOD_LOGCAT:
                switch (logSeverity) {
                    case LOG_SEVERITY_V:
                        Log.v(TAG, tagg);
                        break;
                    case LOG_SEVERITY_D:
                        Log.d(TAG, tagg);
                        break;
                    case LOG_SEVERITY_I:
                        Log.i(TAG, tagg);
                        break;
                    case LOG_SEVERITY_W:
                        Log.w(TAG, tagg);
                        break;
                    case LOG_SEVERITY_E:
                        Log.e(TAG, tagg);
                        break;
                }
                break;
            case Constants.LOG_METHOD_FILELOGGER:
                switch (logSeverity) {
                    case LOG_SEVERITY_V:
                        FL.v(TAG, tagg);
                        break;
                    case LOG_SEVERITY_D:
                        FL.d(TAG, tagg);
                        break;
                    case LOG_SEVERITY_I:
                        FL.i(TAG, tagg);
                        break;
                    case LOG_SEVERITY_W:
                        FL.w(TAG, tagg);
                        break;
                    case LOG_SEVERITY_E:
                        FL.e(TAG, tagg);
                        break;
                }
                break;
        }
    }
}