package com.messagenetsystems.evolutionflasherlights.v3;

/* GattCallback_HY254117
 * This class is the core BluetoothGattCallback stuff for the HY-254117 light controller.
 * Instantiate this and provide that to the BluetoothDevice's connectGatt method.
 * This will then handle making the lights do stuff.
 *
 * Per manufacturer documentation, here is how the light peripheral works:
 *  1. Scan for advertisement (or get device directly via MAC).
 *  2. Request GATT connection.
 *  3. Enable NOTIFY (probably not required, but why not.. and if so, mfg app does it before handshake.
 *  4. Send handshake signal within 5 seconds.
 *  5. After handshake signal OK, send password within 25 seconds.
 *  6. Device is now connected indefinitely, and you can do what you want with it.
 *
 * Revisions:
 *  2020.07.20      Chris Rider     Created.
 */

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.Constants;
import com.messagenetsystems.evolutionflasherlights.devices.BluetoothLightController_REDACTED;
import com.messagenetsystems.evolutionflasherlights.utilities.ConversionUtils;
import com.messagenetsystems.evolutionflasherlights.utilities.SettingsUtils;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;


public class GattCallback_HY254117 extends BluetoothGattCallback {
    private final String TAG = GattCallback_HY254117.class.getSimpleName();

    // Configuration...
    private final int MAX_RETRIES_SERVICE_DISCOVERY = 3;
    private final int RETRY_INTERVAL_MS_SERVICE_DISCOVERY = 100;


    // Local stuff...
    private BluetoothLightController_HY254117V9 lightControllerModel;

    private UUID uuid_service;
    private UUID uuid_char1001, uuid_char1002, uuid_char1003;

    private byte[] charValue_handshake, charValue_password;

    private String lightControllerMAC;

    private int retryCount_serviceDiscovery;
    private boolean retryIsUnderway_serviceDiscovery;

    private byte flasherLightCodeToDo;
    private List<byte[]> characteristicValuesToWrite;


    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;


    /** Constructor */
    public GattCallback_HY254117(int logMethod) {
        this.logMethod = logMethod;

        lightControllerModel = new BluetoothLightController_HY254117V9(logMethod);

        uuid_service = UUID.fromString(lightControllerModel.getUuidStr_mainService());
        uuid_char1001 = UUID.fromString(lightControllerModel.getUuidStr_characteristicForWritingCommands());
        uuid_char1002 = UUID.fromString(lightControllerModel.getUuidStr_characteristicForNotify());
        uuid_char1003 = UUID.fromString(lightControllerModel.getUuidStr_characteristicForOverhead());

        charValue_handshake = lightControllerModel.CHARACTERISTIC_VALUE_BYTES_HANDSHAKE;
        charValue_password = lightControllerModel.CHARACTERISTIC_VALUE_BYTES_PASSWORD_000000;

        lightControllerMAC = SettingsUtils.getProvFileFlasherLightControllerMacAddress();

        retryCount_serviceDiscovery = 0;
        retryIsUnderway_serviceDiscovery = false;
    }

    /** Cleanup */
    public void cleanup() {
        if (lightControllerModel != null) {
            lightControllerModel = null;
        }
    }

    /*============================================================================================*/
    /* BluetoothGattCallback methods */

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
        super.onConnectionStateChange(gatt, status, newState);
        final String TAGG = "onConnectionStateChange: ";

        if (status == GATT_SUCCESS) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logI(TAGG + "Connected. Proceeding with connection routine...");
                if (!gatt.discoverServices()) {
                    logE(TAGG+"discoverServices failed to start. Closing GATT connection.");
                    gatt.close();
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // We successfully disconnected on our own request
                logI(TAGG + "Disconnected. Closing GATT connection.");
                gatt.close();
            }
        } else {
            // An error happened... figure it out
            if (status == 19) {
                // GATT_CONN_TERMINATE_PEER_USER
                // The device disconnected itself on purpose.
                // For example, all data has been transferred and there is nothing else to do.
                logE(TAGG+"Device has disconnected itself on purpose. Closing GATT connection.");
                gatt.close();
            } else if (status == 8) {
                // GATT_CONN_TIMEOUT
                // The connection timed out and device disconnected itself.
                logE(TAGG+"Connection timed-out and device disconnected itself. Closing GATT connection.");
                gatt.close();
            } else if (status == 133) {
                // GATT_ERROR (this really means nothing, thanks to Android's poor implementation)
                // There was a low-level error in the communication which led to loss of connection.
                logE(TAGG+"Status 133 (low-level error / loss of connection / failure to connect). Closing GATT connection.");
                gatt.close();
            } else {
                logE(TAGG + "An error (status "+status+") occurred. Closing GATT connection.");
                gatt.close();
            }
        }
    }

    @Override
    public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
        super.onServicesDiscovered(gatt, status);
        final String TAGG = "onServicesDiscovered: ";

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Set high priority connection
            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

            // Get the service we care about
            BluetoothGattService service = gatt.getService(uuid_service);

            // Check if we got it, and do anything to retry or whatever, if not. If no beans, disconnect and abort.
            if (service == null) {
                if (retryCount_serviceDiscovery < MAX_RETRIES_SERVICE_DISCOVERY) {
                    // Retry service discovery
                    retryCount_serviceDiscovery++;
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!gatt.discoverServices()) {
                                logE(TAGG+"discoverServices failed to start.");
                            }
                        }
                    }, RETRY_INTERVAL_MS_SERVICE_DISCOVERY);
                    gatt.discoverServices();
                } else {
                    // Max retries reached, give up
                    logE(TAGG+"Service not found after "+retryCount_serviceDiscovery+" attempts! Disconnecting and aborting.");
                    retryCount_serviceDiscovery = 0;
                    gatt.disconnect();
                }
                return;
            }

            // If we got here, then we should be good to go!
            // We assume that all subsequent service acquisitions from here on will succeed (so no further retries necessary).

            // Enable notify
            // NOTE: This should result in the invocation of "onDescriptorWrite" where you may continue with handshake!
            enableNotify(gatt);
        } else {
            logE(TAGG+"Non-success GATT status, disconnecting...");
            gatt.disconnect();
        }
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicRead(gatt, characteristic, status);
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
        super.onCharacteristicWrite(gatt, characteristic, status);
        final String TAGG = "onCharacteristicWrite: ";

        if (status == BluetoothGatt.GATT_SUCCESS) {
            if (Arrays.equals(characteristic.getValue(), charValue_handshake)) {
                logV(TAGG + "Handshake successfully sent to device. Sending password...");
                sendCharacteristicValue(gatt, uuid_service, uuid_char1003, charValue_password);
            } else if (Arrays.equals(characteristic.getValue(), charValue_password)) {
                logV(TAGG + "Password successfully sent to device.");
            } else {
                logV(TAGG + "Characteristic value successfully sent to device: \"" + ConversionUtils.byteArrayToHexString(characteristic.getValue(), " ") + "\".");
            }
        } else {
            logE(TAGG+"Non-success GATT status.");
        }
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
        super.onCharacteristicChanged(gatt, characteristic);
        final String TAGG = "onCharacteristicChanged: ";

        logV(TAGG+"Characteristic changed: "+characteristic.getUuid().toString()+" ["+ConversionUtils.byteArrayToHexString(characteristic.getValue(), " ")+"]");
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorRead(gatt, descriptor, status);
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        super.onDescriptorWrite(gatt, descriptor, status);
        final String TAGG = "onDescriptorWrite: ";

        if (status == BluetoothGatt.GATT_SUCCESS) {
            // Send handshake signal
            // NOTE: This should result in the invocation of "onCharacteristicWrite" where you may continue
            sendCharacteristicValue(gatt, uuid_service, uuid_char1003, charValue_handshake);
        } else {
            logE(TAGG+"Non-success GATT status.");
        }
    }

    @Override
    public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyUpdate(gatt, txPhy, rxPhy, status);
    }

    @Override
    public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
        super.onPhyRead(gatt, txPhy, rxPhy, status);
    }

    @Override
    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        super.onReliableWriteCompleted(gatt, status);
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        super.onReadRemoteRssi(gatt, rssi, status);
    }

    @Override
    public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
        super.onMtuChanged(gatt, mtu, status);
    }


    /*============================================================================================*/
    /* Utility methods */

    /** Enable GATT NOTIFY capability.
     * @param gatt GATT client reference
     */
    private void enableNotify(BluetoothGatt gatt) {
        final String TAGG = "enableNotify: ";

        try {
            // Get our notify characteristic and set it to enabled
            BluetoothGattCharacteristic notifyCharacteristic = gatt.getService(uuid_service).getCharacteristic(uuid_char1002);
            gatt.setCharacteristicNotification(notifyCharacteristic, true);

            // Now that notify is enabled, it will have descriptor with handle 0x2902, so we need
            // to write BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE to it. First, convert 0x2902
            // to 128 bit UUID (00002902 + BASE-96 BLE UUID).
            UUID notifyDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

            // Get the notify characteristic's descriptor and set it to enabled
            BluetoothGattDescriptor descriptor = notifyCharacteristic.getDescriptor(notifyDescriptorUUID);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);

            // Finally, we can write the descriptor...
            // From here, do whatever you may need in the "onDescriptorWrite" method.
            gatt.writeDescriptor(descriptor);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
        }
    }

    /** Send (write) a GATT characteristic value.
     * @param gatt GATT client instance
     * @param serviceUUID GATT service UUID the characteristic belongs to
     * @param characteristicUUID GATT characteristic UUID to write to
     * @param characteristicValue GATT characteristic value to write
     * @return Whether write operation was attempted
     */
    private boolean sendCharacteristicValue(BluetoothGatt gatt, UUID serviceUUID, UUID characteristicUUID, byte[] characteristicValue) {
        final String TAGG = "sendPassword: ";

        try {
            // Get service
            BluetoothGattService gattService = gatt.getService(serviceUUID);
            if (gattService == null) {
                logE(TAGG+"Failed to get service, aborting.");
                return false;
            }

            // Get characteristic from that service
            BluetoothGattCharacteristic gattCharacteristic = gattService.getCharacteristic(characteristicUUID);
            if (gattCharacteristic == null) {
                logE(TAGG+"Failed to get characteristic, aborting.");
                return false;
            }

            // Set value of that characteristic
            gattCharacteristic.setValue(characteristicValue);

            // Write the updated characteristic back to GATT
            logV(TAGG+"Sending ["+ConversionUtils.byteArrayToHexString(characteristicValue, " ")+"] to characteristic "+characteristicUUID.toString()+" in service "+serviceUUID.toString()+"...");
            return gatt.writeCharacteristic(gattCharacteristic);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            return false;
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
