package com.messagenetsystems.evolutionflasherlights.services;

/* BluetoothLightService Class
 *
 *  A persistent background service that handles requests to do stuff with the flasher lights.
 *  It manages the connection to the LED lights, sends commands, reads status, etc., via the Android BLE API.
 *
 *  At startup, it will do a scan for devices, find the nearest one, and save it locally to the instance.
 *  From there, you should just be able to use that saved device to connect and perform commands as needed.
 *
 *  General BLE connection procedure:
 *      1. Setup & Prep:
 *          - Get bluetooth manager and adapter instances.
 *          - Define a scan callback (this is what gets passed to the start-scan call). It fires gatt stuff next...
 *          - Define a gatt client & connection callback (this is how you get BLE services and ultimately characteristics).
 *      2. Start scanning and let scan callback do its thing (onScanResult). (don't forget to stop it when necessary and when service stops)
 *      3. Allow callbacks to do their thing (primarily onConnectionStateChange -> onServicesDiscovered).
 *
 *  Typical call-stack:
 *      1. Start scanning (includes any filtering).
 *      2. Scan-callback fires upon getting a scan result, and attempts a gatt connection to device.
 *      3. Gatt
 *
 *  Rationale:
 *      We use a service instead of a thread, since it's not user-interactive (which most of this app is not).
 *
 *  Revisions:
 *      2018.08.02      Chris Rider     (as part of main app project)   Creation (refactoring from BluetoothLightService prototype).
 *      2018.11.26      Chris Rider     (as part of main app project)   Added thread for more intelligently setting default light mode when no active messages.
 *      2019.01.14      Chris Rider     Copied over to this dedicated app from main app.
 *      2020.05.28-29   Chris Rider     Implemented file logging capability.
 *      2020.06.02      Chris Rider     Stripped and cleaned up some unnecessary things now that we're starting to use the refactored version.
 */


import android.Manifest;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.Constants;
import com.messagenetsystems.evolutionflasherlights.MainApplication;
import com.messagenetsystems.evolutionflasherlights.R;
import com.messagenetsystems.evolutionflasherlights.activities.StartupActivity;
import com.messagenetsystems.evolutionflasherlights.devices.BluetoothLightController_HY254117V9;
import com.messagenetsystems.evolutionflasherlights.models.FlasherLights;
import com.messagenetsystems.evolutionflasherlights.utilities.ConversionUtils;
import com.messagenetsystems.evolutionflasherlights.utilities.SettingsUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;


public class BluetoothFlasherLightsService extends Service {
    public static final String TAG = BluetoothFlasherLightsService.class.getSimpleName();
    protected Context context;
    public static long pid;
    private MainApplication mainApplication;

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;

    public static final String INTENTFILTER_LIGHTCMD = FlasherLights.Intents.Filters.LIGHTCMD;
    public static final String INTENTEXTRA_SIGNALLIGHTCMD = "com.messagenetsystems.evolutionflasherlights.intentextra.REDACTED";

    // Declare bluetooth hardware resources
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    // Declare bluetooth scanning related resources
    private BluetoothLeScanner mBluetoothLeScanner;
    private ScanCallback mScanCallback;
    private boolean mScanning;
    private Handler mScanningHandler;
    private static long SCAN_PERIOD;                                                                //stops scanning after this many milliseconds

    // Declare bluetooth light controller device related resources
    private int scannedStrongestRssi;
    private static String lightControllerDevice_macAddress;
    private BluetoothDevice mBluetoothDevice;
    private BluetoothLightController_HY254117V9 lightController;

    // Declare bluetooth light communication related resources
    private GattClientCallback mGattClientCallback;
    private BluetoothGatt mBluetoothGatt;

    // Declare interprocess communication stuff
    private BroadcastReceiver broadcastReceiver;

    // Declare any worker threads
    private Thread defaultLightStateManagerThread = null;

    // Declare flags and misc
    private volatile boolean aConnectionHasBeenInitiated = false;
    private volatile boolean aCommandIsTryingToSend = false;
    //private volatile boolean aMsgLightCommandIsActive = false;
    private volatile long mostRecent_getTime_lightConnection = 0;
    private volatile long mostRecent_getTime_lightCommandWritten = 0;
    private volatile boolean lastWriteWasSuccessful = false;
    //private volatile char lastWrittenLightCommand_asASCII;
    //private volatile int lastWrittenLightCommand_asDecimal;
    private volatile byte[] lastWrittenLightCommand_asByteArray;
    public static volatile int currentLightCommand_asDecimal;
    public static volatile byte[] currentLightCommand_asByteArray;
    public static volatile boolean preventSendingAnyLightCommands = false;


    public BluetoothFlasherLightsService(Context appContext) {
        super();
    }
    public BluetoothFlasherLightsService() {

    }


    /***********************************************************************************************
     * Service Stuff ******************************************************************************/

    @Override
    public void onCreate() {
        // The system invokes this method to perform one-time setup procedures when the service is
        // initially created (before it calls either onStartCommand or onBind). If the service is
        // already running, this method is not invoked.
        super.onCreate();
        this.logMethod = Constants.LOG_METHOD_FILELOGGER;
        final String TAGG = "onCreate: ";
        logV(TAGG+"Invoked.");

        try {
            this.mainApplication = ((MainApplication) getApplicationContext());
        } catch (Exception e) {
            logE(TAGG+"Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // The system invokes this method by calling startService() when another component (such as
        // an activity) requests that the service be started. When this method executes, the service
        // is started and can run in the background indefinitely. If you implement this, it is your
        // responsibility to stop the service by calling stopSelf() or stopService(). If you want to
        // implement binding, then you don't need to implement this method.
        super.onStartCommand(intent, flags, startId);

        final String TAGG = "onStartCommand: ";
        logD(TAGG+TAGG + "Invoked.");

        // Begin startup...
        this.mainApplication.replaceNotificationWithText("Starting Service");
        //startForeground(0, null);                                                                   //running in foreground better ensures Android won't kill us    TODO: (is that right?)
        startForeground(this.mainApplication.getmNotifID(), this.mainApplication.getmNotification());
        try {
            // Get our service's process ID (this is the earliest we can get one)
            pid = (long) android.os.Process.myPid();
        } catch (Exception e) {
            logE(TAGG+"Exception caught trying to get app process ID: "+e.getMessage());
        }

        // Initialize everything
        initialize();

        // Start scanning to find our strongest/nearest light controller
        // (note: this kicks off the series of events needed to form a connection to the light
        //  controller and enable us to send it commands.)
        startScanForDevices();

        // Start any threads
        if (defaultLightStateManagerThread != null) {
            defaultLightStateManagerThread.start();
        } else {
            defaultLightStateManagerThread = new LightStateManagerThread(context);
            defaultLightStateManagerThread.start();
        }

        // Finish startup...
        logI(TAGG+"Service started.");
        this.mainApplication.replaceNotificationWithText("BluetoothFlasherLightsService started (App PID "+Long.toString(pid)+").");
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        // The system invokes this method by calling bindService() when another component wants to
        // bind with this service (such as to perform RPC). In your implmentation of this method,
        // you must provide an interface that clients use to communicate with the service by
        // returning an IBinder. You must always implement this method; however, if you don't want
        // to allow binding, you should return null;
        final String TAGG = "onBind: ";
        logI(TAGG+TAGG + "Invoked.");

        return null;
    }

    @Override
    public void onDestroy() {
        // The system invokes this method when the service is no longer used and is being destroyed.
        // Your service should implement this to clean up any resources such as threads, registered
        // listeners, or receivers. This is the last call that the service receives.
        final String TAGG = "onDestroy: ";
        logI(TAGG+TAGG + "Invoked.");

        // Update notification
        mainApplication.replaceNotificationWithText("MainService destroyed!");

        // Clean-up everything before service dies
        cleanup();

        super.onDestroy();
    }


    /***********************************************************************************************
     * Misc. Stuff ********************************************************************************/

    private void initialize() {
        final String TAGG = "initialize: ";
        logD(TAGG+TAGG + "Called.");

        context = getApplicationContext();

        // Initialize some default initial values
        mScanning = false;                  //flag whether a scan is happening or not
        scannedStrongestRssi = -100;        //an unrealistically low strength value
        SCAN_PERIOD = 3000;                 //milliseconds after which to stop scanning (should get overridden by read in of strings.xml value?)
        currentLightCommand_asDecimal = MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_NONE;
        aConnectionHasBeenInitiated = false;

        // Create an instance of our lightController class (which has all the stuff unique to our particular light controller device)
        lightController = new BluetoothLightController_HY254117V9(logMethod);

        // Get shared-prefs values
        lightControllerDevice_macAddress = SettingsUtils.getSharePrefsFlasherLightControllerMacAddress();
        if (!SettingsUtils.isThisMacAddressValid(lightControllerDevice_macAddress)) {
            logW(TAGG+TAGG+"Configured flasher light MAC address is invalid. Won't use it.");
            lightControllerDevice_macAddress = null;
        }

        // Initialize hardware
        initializeBleAdapter();

        mBluetoothGatt = null;

        // Initialize and register Message Handler
        broadcastReceiver = new LightCommandBroadcastReceiver();
        registerReceiver(broadcastReceiver, new IntentFilter(INTENTFILTER_LIGHTCMD));

        // Initialize any worker threads
        defaultLightStateManagerThread = new LightStateManagerThread(context);
    }

    private void cleanup() {
        final String TAGG = "cleanup: ";
        logD(TAGG+TAGG + "Called.");

        // Handle shutting down any bluetooth activities
        stopScan();

        // Reset lights
        initiateLightCommand(getResources().getString(R.string.SMAJAX_MESSAGES_NONE));

        // Clean up any bluetooth-related connections
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
            mBluetoothGatt = null;
        }
        if (mBluetoothAdapter != null) {
            if (mBluetoothAdapter.isDiscovering()) {
                mBluetoothAdapter.cancelDiscovery();
            }
        }

        // Clean up any threads
        if (defaultLightStateManagerThread != null)
            defaultLightStateManagerThread.interrupt();

        // Misc.
        if (broadcastReceiver != null)
            unregisterReceiver(broadcastReceiver);

        // Mark objects for garbage collection (yeah, not strictly necessary, but to be safe)
        lightController = null;
        mBluetoothAdapter = null;
        mBluetoothManager = null;
        broadcastReceiver = null;
        mGattClientCallback = null;
        defaultLightStateManagerThread = null;
    }

    /**
     * Initialize the BLE adapter hardware
     */
    private void initializeBleAdapter() {
        final String TAGG = "initializeAdapter: ";

        logD(TAGG+TAGG + "Initializing mBluetoothManager and getting mBluetoothAdapter.");
        mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        // Ensure Bluetooth is available on the device and it is enabled
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            logW(TAGG+TAGG + "Bluetooth adapter is either unavailable or not enabled. Attempting to enable...");
            for (int tries = 1; tries <= 10; tries++) {
                SettingsUtils.enableBluetoothAdapter();
                try {
                    logD(TAGG+TAGG + "Waiting for bluetooth to come online (" + tries + " seconds).");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logW(TAGG+TAGG + "Exception caught trying to wait for Bluetooth to enable.");    //just let it keep trying
                }
                if (SettingsUtils.isBluetoothEnabled()) {
                    logD(TAGG+TAGG + "Bluetooth is now enabled, restarting initialization routine.");
                    initializeBleAdapter();
                    break;
                }
            }
        } else {
            logD(TAGG+TAGG + "Bluetooth adapter initialized.");
            logD(TAGG+TAGG + "Bluetooth adapter state = " + mBluetoothAdapter.getState() + "\n" +
                    "STATE_OFF = 10, STATE_TURNING_ON = 11, STATE_ON = 12, STATE_TURNING_OFF = 13");             //STATE_OFF = 10, STATE_TURNING_ON = 11, STATE_ON = 12, STATE_TURNING_OFF = 13
        }

        // Make sure we have access coarse location enabled
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                logW(TAGG+TAGG + "Coarse location permission is not granted.");
                /*
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect peripherals.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
                */
            }
        }
    }

    /**
     * Scan for devices and update the mScanResults
     */
    private void startScanForDevices() {
        final String TAGG = "startScanForDevices: ";

        // First, check if a scan is already happening
        if (mScanning) {
            logW(TAGG+TAGG + "A scan is already occuring. Aborting so as not to start another.");
            return;
        }

        // Setup our ScanSettings configuration
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();

        // Setup our ScanFilter configuration (so we can only scan for devices we care about)
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter scanFilter;
        scanFilter = new ScanFilter.Builder()
                /*.setServiceUuid(new ParcelUuid([UUID HERE]))*/        /* NOTE: on some older versions of android (maybe newer too?), this might be problematic? */
                .setDeviceName(lightController.DEVICE_NAME)
                .build();
        filters.add(scanFilter);

        // Reinitialize an instance of our scan-callback (which saves strongest scan results to class-global vars)
        mScanCallback = new BtleScanCallback();

        // Now grab hold of the BluetoothLeScanner to start the scan, and set our scanning boolean to true.
        if (mBluetoothAdapter != null) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            logD(TAGG+TAGG + "Scanning for " + Math.round(SCAN_PERIOD / 1000) + " seconds...");
            mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
            mScanning = true;
        } else {
            logE(TAGG+TAGG + "No mBluetoothAdapter object, cannot start scanning. Make sure it's instantiated first.");
            return;
        }

        // At this point, we have a Bluetooth scan that will save all ScanResults into a map (and/or class-global variables)
        // (that should have happened in the appropriate callback)

        // Since scanning will go forever on its own, setup a handler to stop it after some time (it doesn't need to be long)
        mScanningHandler = new Handler();
        mScanningHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                logD(TAGG+TAGG + "mScanningHandler Runnable executing to call stopScan.");
                stopScan();
            }
        }, SCAN_PERIOD);
    }

    /**
     * Stop the scan, using the same ScanCallback we used earlier (just for simplicity? not sure why it's needed).
     * NOTE: This will fire after SCAN_PERIOD has elapsed, via a Handler.postDelayed call.
     * After that, we continue the sequence of events, by calling scanComplete.
     */
    private void stopScan() {
        final String TAGG = "stopScan: ";

        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            logD(TAGG+TAGG + "Scanning will now be commanded to stop.");

            mBluetoothLeScanner.stopScan(mScanCallback);
            mScanning = false;

            try {
                logV(TAGG+TAGG + "Waiting a brief time for scan to stop.");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                logW(TAGG+TAGG + "Exception caught waiting for scan to stop; should be alright, so continuing.");
            }

            scanComplete(); //continue onward! scanning is done and we should have the device we need, saved to class-global varibles!
        }

        //cleanup
        mScanCallback = null;
        mScanningHandler = null;
    }

    /**
     * Perform any actions using whatever scan results are in class-global scope.
     */
    private void scanComplete() {
        final String TAGG = "scanComplete: ";

        if (mBluetoothDevice != null) {
            logI(TAGG+TAGG + "Device we will use is: " + mBluetoothDevice.getAddress() + " with signal strength of " + scannedStrongestRssi + ".");

            StartupActivity.lightControllerMacAddress = mBluetoothDevice.getAddress();

            //logI(TAGG+TAGG+"Connecting GATT...");
            //connectGattDevice(mBluetoothDevice);

            // Check if it seems right (typically anything stronger than -50dbm
            if (scannedStrongestRssi < -68) {
                logW(TAGG+TAGG+"WARNING: light controller seems weak... is it the right one?");
            }

            // Send initial command to light hardware
            initiateLightCommand(String.valueOf((char) MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_NONE));
        } else {
            logE(TAGG+TAGG + "Scanned device is null... perhaps it's connected to another device?");

            stopSelf();

            //TODO: Try resetting bluetooth chipset? (manually turning on and off fixes this)
        }
    }

    /**
     * Conversion methods
     */


    // NOTE: following is a work in progress, since it will be very extensive!
    public int decodeCharacteristicByteArray_toBannerLightCommand_asDecimal(byte[] bytes) {
        final String TAGG = "decodeCharacteristicByteArray_toBannerLightCommand_asDecimal("+ ConversionUtils.byteArrayToHexString(bytes)+"): ";
        logV(TAGG+TAGG+"Invoked.");

        int ret = -1;

        //ex. take "B8013645430008" (0xb8 0x01 0x36 0x45 0x43 0x00 0x08) and return SIGNALLIGHT_CMD_NONE    <-- header, write, red, green, blue, brightness, speed

        if (bytes[0] == lightController.DATAGRAM_W_HEADER) {
            if (bytes[1] == lightController.DATAGRAM_W_CMD_COLOR) {
                logV(TAGG+TAGG+"Characteristic was a write-color ("+ConversionUtils.byteArrayToHexString(new byte[]{bytes[2],bytes[3],bytes[4]})+") with brightness "+ConversionUtils.byteArrayToHexString(new byte[]{bytes[5]})+".");
            } else if (bytes[1] == lightController.DATAGRAM_W_CMD_WHITE) {
                logV(TAGG+TAGG+"Characteristic was a write-white.");
            } else {
                logW(TAGG+TAGG+"Unhandled datagram header for writing commands for this device.");
            }
        } else {
            logW(TAGG+TAGG+"Unknown datagram header for writing to this device.");
        }

        logV(TAGG+TAGG+"Returning "+ret);
        return ret;
    }

    /**
     * Static methods to call in order to request stuff happen
     */
    public static void requestLightAction(Context context, String dbb_light_signal, boolean doForceAction, boolean doPreventFurtherCommands) {
        final String TAGG = "requestLightAction: ";
        Log.v(TAG, "Invoked.");

        Intent myIntent = new Intent(INTENTFILTER_LIGHTCMD);
        myIntent.putExtra(LightCommandBroadcastReceiver.INTENTEXTRA_LIGHTCMD, LightCommandBroadcastReceiver.CMD_LIGHTS_DO_SOMETHING);
        myIntent.putExtra(INTENTEXTRA_SIGNALLIGHTCMD, dbb_light_signal);
        myIntent.putExtra(LightCommandBroadcastReceiver.INTENTEXTRA_LIGHTCMD_DOFORCE, doForceAction);
        myIntent.putExtra(LightCommandBroadcastReceiver.INTENTEXTRA_LIGHTCMD_DOPREVENTFURTHERCOMMANDS, doPreventFurtherCommands);
        context.sendBroadcast(myIntent);
    }
    public static void requestLightAction_default(Context context, boolean doForceAction, boolean doPreventFurtherCommands) {
        requestLightAction(context, String.valueOf((char)MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_NONE), doForceAction, doPreventFurtherCommands);
    }
    public static void requestLightAction_off(Context context, boolean doForceAction, boolean doPreventFurtherCommands) {
        requestLightAction(context, String.valueOf((char)MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_OFF), doForceAction, doPreventFurtherCommands);
    }


    /***********************************************************************************************
     * Bluetooth Stuff ****************************************************************************/

    /** Define what happens during scanning.
     * Essentially, we just handle a scan result by saving the scanned device's signal strength
     * to the global variable if it's stronger than anything there already. By this method, we
     * are ensured to get the strongest in-range device by the time all devices are scanned for. */
    private class BtleScanCallback extends ScanCallback {
        private final String TAGG = BtleScanCallback.class.getSimpleName()+": ";

        boolean notYetFoundOurAssociatedDevice = true;

        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            if (notYetFoundOurAssociatedDevice)
                addScanResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                if (notYetFoundOurAssociatedDevice)
                    addScanResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            final String TAGGG = "onScanFailed("+errorCode+"): ";
            switch (errorCode) {
                case SCAN_FAILED_INTERNAL_ERROR:
                    logE(TAGG+TAGG+TAGGG+"BLE scan failed with internal error!");
                    break;
                default:
                    logE(TAGG+TAGG+TAGGG+"BLE scan failed!");
                    break;
            }
        }

        private void addScanResult(ScanResult result) {
            final String TAGGG = "addScanResult("+result.getDevice().getAddress()+"): ";
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();
            int deviceRssi = result.getRssi();
            String deviceMac = device.getAddress();

            logV(TAGG+TAGG+TAGGG+deviceRssi+"db device found: \""+deviceName+"\" ("+device.toString()+").");

            // If we have an associated light controller defined, and this device matches it, use it;
            if (lightControllerDevice_macAddress != null
                    && deviceMac.equalsIgnoreCase(lightControllerDevice_macAddress)) {
                logI(TAGG+TAGG+TAGGG+"Found device that we're defined to be associated with. Using it!");
                notYetFoundOurAssociatedDevice = false;
                mBluetoothDevice = device;
                scannedStrongestRssi = deviceRssi;
                return;
            }

            // (If we got to here, then the found device is not (yet?) a defined one)
            if (notYetFoundOurAssociatedDevice) {
                // Disregard obviously weak/far devices
                if (deviceRssi < -65) {
                    logV(TAGG+TAGG + TAGGG + "Device is weaker than expected. Likely not a local device. Skipping.");
                    return;
                }

                // If this device is the strongest we've found yet (and thus more likely to be nearest), remember it.
                // At the end of the callbacks, we should have the strongest/nearest device ready to go.
                if (deviceRssi > scannedStrongestRssi) {
                    mBluetoothDevice = device;
                    scannedStrongestRssi = deviceRssi;
                }
            }
        }
    }

    /** Define what happens when doing GATT stuff (connecting, writing, etc.).
     * This is setup to be instantiated whenever you have a discrete command to send, and to live only during that command. */
    private class GattClientCallback extends BluetoothGattCallback {
        final String TAGG = GattClientCallback.class.getSimpleName() + ": ";

        byte[] lightCommand;
        int lightCommand_asDecimal;

        byte[] lightCommandAdditional = null;

        List<BluetoothGattService> services = null;

        boolean saveWrittenCommand = true;

        GattClientCallback(byte[] lightCommand, byte[] lightCommandAdditional, int lightCommand_asDecimal) {
            this.lightCommand = lightCommand;
            this.lightCommand_asDecimal = lightCommand_asDecimal;
            this.lightCommandAdditional = lightCommandAdditional;

            lastWriteWasSuccessful = false; //reset flag before we start to do write stuff
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            final String TAGG = this.TAGG+"onConnectionStateChange: ";
            logV(TAGG+TAGG + "Invoked (status=" + status + ", newState=" + newState + ").");


            // if these conditions == true, then we have a disconnect
            if ( status == BluetoothGatt.GATT_FAILURE
                    || status != BluetoothGatt.GATT_SUCCESS
                    || newState == BluetoothProfile.STATE_DISCONNECTED) {
                logD(TAGG+"Disconnected (status = "+status+", state = "+newState+")");

                /* The following causes an occasional "Unhandled exception in callback / NullPointerException in BluetoothGatt: onClientConnectionState" warning
                   But it seems necessary in order to prevent Error 257 */
                try {
                    gatt.close();
                } catch (Exception e) {
                    logW(TAGG+TAGG+"Exception caught (problem with BluetoothGatt provided in callback?): "+e.getMessage());
                }

                aCommandIsTryingToSend = false;
                aConnectionHasBeenInitiated = false;
            }
            // if these conditions == true, then we have a successful connection
            else if (newState == BluetoothProfile.STATE_CONNECTED) {
                // DOES NOT WORK... (seems you must discover services)
                //logI(TAGG+TAGG+"Connected. Writing command...");
                //writeCommandToLights(lightCommand);

                mostRecent_getTime_lightConnection = new Date().getTime();

                // Try to avoid common problems in BLE from discovering immediately after establishing connection
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    logW(TAGG+TAGG+"Exception caught trying to sleep: "+ie.getMessage());
                }

                logI(TAGG+TAGG+"Connected. Discovering services...");
                try {
                    gatt.discoverServices();
                } catch (Exception e) {
                    logW(TAGG+TAGG+"Exception caught (problem with BluetoothGatt provided in callback?): "+e.getMessage());
                    aCommandIsTryingToSend = false;

                    // Since we failed, setup to try again
                    /* TODO: just an idea (seems unnecessary) probably would fail due to looper.prepare problem?
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            logD(TAGG+TAGG + "Exception was caught trying to call discoverServices. Delayed Runnable now re-calling initiateLightCommand("+String.valueOf(currentLightCommand_asASCII)+")...");
                            initiateLightCommand(String.valueOf(currentLightCommand_asASCII));
                        }
                    }, 2000);
                    */
                }
            } else {
                logW(TAGG+TAGG+"Unhandled state, disconnecting...");
                try {
                    gatt.disconnect();
                    aConnectionHasBeenInitiated = false;
                } catch (Exception e) {
                    logW(TAGG+TAGG+"Exception caught (problem with BluetoothGatt provided in callback?): "+e.getMessage());
                }
                aCommandIsTryingToSend = false;

                // Since we failed, setup to try again
                /* TODO: just an idea (seems unnecessary) probably would fail due to looper.prepare problem?
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        logD(TAGG+TAGG + "GATT connection state unhandled. Delayed Runnable now re-calling initiateLightCommand("+String.valueOf(currentLightCommand_asASCII)+")...");
                        initiateLightCommand(String.valueOf(currentLightCommand_asASCII));
                    }
                }, 2000);
                */
            }

            if (status == 19) {
                logV(TAGG+TAGG+"Disconnected by device.");
            } else if (status == 133) {
                logV(TAGG+TAGG+"Unknown error 133 (too many connections?)");
                //TODO: Might be some relationship to "No connection for..." error thrown by BtGatt.GattService discoverServices() -- try waiting about 500ms then try to connect again (works for some guy)

                // Restart the core stuff in this class
                cleanup();
                initialize();
            } else if (status == 257) {
                /* The following may not actually work... (CR 2018.11.30)
                logI(TAGG+TAGG+"Unknown error 257 (max client connections reached?). Trying to disconnect and close gatt.");
                try {
                    //gatt.disconnect();
                    //gatt.close();
                    disconnectGattDevice();
                } catch (Exception e) {
                    logW(TAGG+TAGG+"Exception caught (problem with BluetoothGatt provided in callback?): "+e.getMessage());
                }
                */

                // Restart the core stuff in this class
                logI(TAGG+TAGG+"Unknown error 257 (max client connections reached?). Trying to cleanup and re-initialize core class bluetooth stuff...");
                cleanup();
                initialize();

                aCommandIsTryingToSend = false;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            final String TAGG = this.TAGG+"onServicesDiscovered: ";
            logV(TAGG+TAGG + "Invoked (status="+status+")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                services = gatt.getServices();
                if (services == null) {
                    logE(TAGG+TAGG+"No services available, disconnecting and aborting.");
                    lastWriteWasSuccessful = false; //reset flag
                    gatt.disconnect();

                    // Since we failed, setup to try again
                    /* TODO: just an idea (seems unnecessary) probably would fail due to looper.prepare problem?
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            logD(TAGG+TAGG + "GATT service discovery succeeded, but could not getServices. Delayed Runnable now re-calling initiateLightCommand("+String.valueOf(currentLightCommand_asASCII)+")...");
                            initiateLightCommand(String.valueOf(currentLightCommand_asASCII));
                        }
                    }, 2000);
                    */

                    return;
                }
                logD(TAGG+TAGG+"Success. "+services.size()+" services available. Writing command...");
                //for (BluetoothGattService service : services)
                //    logV(TAGG+TAGG+" Found-service UUID: "+String.valueOf(service.getUuid()));
                writeCommandToLights(gatt, services, lightCommand);
            } else {
                //logW(TAGG+TAGG+"Unhandled status.");
                logD(TAGG+TAGG+"Unhandled status, disconnecting...");
                gatt.disconnect();
                aCommandIsTryingToSend = false;
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            final String TAGG = this.TAGG+"onCharacteristicRead: ";
            logV(TAGG+TAGG + "Invoked (status="+status+")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                logD(TAGG+TAGG+"Success.");
                logV(TAGG+TAGG+"Characteristic value = \""+ConversionUtils.byteArrayToHexString(characteristic.getValue())+"\".");
            } else {
                logW(TAGG+TAGG+"Unhandled status.");
            }
        }

        @Override
        // Result of a characteristic write operation
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            final String TAGG = this.TAGG+"onCharacteristicWrite: ";
            logV(TAGG+TAGG + "Invoked (status="+status+")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                logD(TAGG+TAGG+"Success (bytes that were written: \"" + ConversionUtils.byteArrayToHexString(characteristic.getValue()) + "\").");

                //Log potential issue if this write comes very soon after another previous write
                if (new Date().getTime() - mostRecent_getTime_lightCommandWritten < 500) {
                    logW(TAGG+TAGG+"Notice! This light command written unusually soon after last one. Consider looking into this!");
                }

                //Write operation successful
                lastWriteWasSuccessful = true;
                mostRecent_getTime_lightCommandWritten = new Date().getTime();

                if (saveWrittenCommand)
                    lastWrittenLightCommand_asByteArray = characteristic.getValue();

                //lastWrittenLightCommand_asDecimal = currentLightCommand_asDecimal;
                //lastWrittenLightCommand_asASCII = (char) lastWrittenLightCommand_asDecimal;

                //Check if we have another characteristic to write
                if (lightCommandAdditional != null) {
                    //Wait a bit to make sure previous write makes it to the light
                    try {
                        Thread.sleep(600);
                    } catch (InterruptedException ie) {
                        logW(TAGG+TAGG + "Exception caught trying to sleep: " + ie.getMessage());
                    }

                    //Make sure the flash command won't get saves as a last-successful-command
                    saveWrittenCommand = false;

                    writeCommandToLights(gatt, services, lightCommandAdditional);
                    lightCommandAdditional = null;  //reset so we don't keep executing
                    //lastWrittenLightCommand_asDecimal = currentLightCommand_asDecimal;      //save off our last actual command (which is right now our "current" command) -we don't care to save the additional command
                }

            } else {
                logW(TAGG+TAGG+"Unhandled status.");
                //lastWrittenLightCommand_asDecimal = SIGNALLIGHT_CMD_NONE;

                // Since we failed, setup to try again
                /* TODO: just an idea (seems unnecessary) probably would fail due to looper.prepare problem?
                new Handler().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        logD(TAGG+TAGG + "GATT characteristic write failure... Delayed Runnable now re-calling initiateLightCommand("+String.valueOf(currentLightCommand_asASCII)+")...");
                        initiateLightCommand(String.valueOf(currentLightCommand_asASCII));
                    }
                }, 2000);
                */
            }

            logD(TAGG+TAGG+"Disconnecting.");
            gatt.disconnect();

            aCommandIsTryingToSend = false;
            aConnectionHasBeenInitiated = false;
        }
    }

    /** Bond with the BLE device...
     * This is not needed, but may help in creating the GATT cache file?
     * NOTE: It will require use of passcode/PIN to do the pairing. */
    /*
    private void bondGattDevice() {
        mBluetoothDevice.createBond();
    }
    */

    /** Connect with BLE device's GATT server...
     * Once we have a mBluetoothGatt instance, we're essentially connected to the device and can do things with it.
     * The BluetoothGattCallback does most of the handling of those things we do, once that happens.
     * NOTE: A connection doesn't last forever, we're at the mercy of the device. So only connect when you want to do stuff. */
    private void connectGattDevice(GattClientCallback gattClientCallback, boolean doForce) {
        final String TAGG = "connectGattDevice: ";
        logV(TAGG+TAGG+"Invoked.");

        //TODO: check for existing active connection first?
        if (aConnectionHasBeenInitiated) {
            if (doForce) {
                logW(TAGG+TAGG + "Connection has already been initiated; but force-flag set, so allowing connection anyway.");
            } else {
                logW(TAGG+TAGG + "Connection has already been initiated, aborting to prevent a pile up.");
                return;
            }
        }
        if (mBluetoothGatt != null && mBluetoothGatt.getConnectionState(mBluetoothDevice) == BluetoothProfile.STATE_CONNECTED) {
            if (doForce) {
                logW(TAGG+TAGG+"Connection already exists; but force-flag set, so allowing connection anyway.");
            } else {
                logW(TAGG+TAGG+"Connection already exists, aborting to prevent a pile up.");
                return;
            }
            //mBluetoothGatt.disconnect();    //can't hurt to disconnect first?
        }

        int msAvoidRapidFireConnectsUnder = 1000;
        if (new Date().getTime() - mostRecent_getTime_lightConnection < msAvoidRapidFireConnectsUnder) {
            if (doForce) {
                logW(TAGG+TAGG + "Connection was last made < " + msAvoidRapidFireConnectsUnder + "ms ago; but force-flag set, so allowing connection anway.");
            } else {
                logW(TAGG+TAGG + "Connection was last made < " + msAvoidRapidFireConnectsUnder + "ms ago, aborting.");
                return;
            }
        }

        // Initiate a connection to our device's GATT server, passing in our callback
        // Note: You may use this global object after it's connected for various post-connection tasks.
        aConnectionHasBeenInitiated = true;
        aCommandIsTryingToSend = true;
        //mBluetoothGatt = mBluetoothDevice.connectGatt(this, true, gattClientCallback);
        //mBluetoothGatt.connect();
        try {
            mBluetoothDevice.connectGatt(this, Constants.GATT_AUTOCONNECT, gattClientCallback).connect();
        } catch (NullPointerException npe) {
            logE(TAGG+TAGG+"Null Pointer Exception caught trying to invoke .connectGatt method on mBluetoothDevice: "+npe.getMessage());
        }
    }

    /** Disconnect device */
    private void disconnectGattDevice() {
        final String TAGG = "disconnectGattDevice: ";

        //mConnected = false;
        if (mBluetoothGatt != null) {
            mBluetoothGatt.disconnect();
            mBluetoothGatt.close();
        }

        aConnectionHasBeenInitiated = false;
    }

    /** Read current light mode characteristic from the light controller's GATT server.
     * This should be the last command the light successfully received and set.
     * You may use this to directly verify what the light controller is doing with your command, for instance.
     * NOTE: Remember that the value is returned via callback (onCharacteristicRead).
     * NOTE: Created 2018.11.28, not really working yet! */
    private Boolean readCharacteristicValue_lightCommand(BluetoothGatt gatt, List<BluetoothGattService> gattServices) {
        final String TAGG = "readCharacteristicValue_lightCommand: ";
        logV(TAGG+TAGG+"Invoked.");

        boolean ret = false;
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic;

        //TODO: check for current connection active first?

        try {
            /*
            characteristic = gatt
                    .getService(UUID.fromString(lightController.getUuidStr_mainService()))
                    .getCharacteristic(UUID.fromString(lightController.getUuidStr_characteristicForWritingCommands()));
                    */

            //service = gatt.getService(UUID.fromString(lightController.getUuidStr_mainService()));
            if (gattServices != null) {
                for (BluetoothGattService iService : gattServices) {
                    if (iService.getUuid() != null
                            && iService.getUuid().toString().equals(lightController.getUuidStr_mainService())) {
                        service = iService;
                        break;
                    }
                }
            }

            if (service == null) {
                logD(TAGG+TAGG+"Could not get service from any provided list of gattServices, getting from gatt object...");
                service = gatt.getService(UUID.fromString(lightController.getUuidStr_mainService()));
            }

            if (service == null) {
                logW(TAGG+TAGG+"Failed to get service. Throwing exception.");
                throw new Exception();
            } else {
                characteristic = service.getCharacteristic(UUID.fromString(lightController.getUuidStr_characteristicForWritingCommands()));
            }

            if (characteristic == null) {
                logW(TAGG+TAGG+"Failed to get characteristic from provided gatt and its service. Throwing exception.");
                throw new Exception();
            }

            ret = gatt.readCharacteristic(characteristic);
        } catch (Exception e) {
            logE(TAGG+TAGG+"Exception caught with getting characteristic: "+e.getMessage());
        }

        return ret;
    }

    /** Write a characteristic to GATT with the provided data/command.
     * Returns whether any write command sent (refer to onCharacteristicWrite callback for better result). */
    private boolean writeCommandToLights(BluetoothGatt gatt, List<BluetoothGattService> gattServices, byte[] command) {
        final String TAGG = "writeCommandToLights: ";
        logV(TAGG+TAGG+"Invoked.");

        boolean ret = false;
        BluetoothGattService service = null;
        BluetoothGattCharacteristic characteristic;

        //TODO: check for current connection active first?

        try {
            /*
            characteristic = gatt
                    .getService(UUID.fromString(lightController.getUuidStr_mainService()))
                    .getCharacteristic(UUID.fromString(lightController.getUuidStr_characteristicForWritingCommands()));
                    */

            //service = gatt.getService(UUID.fromString(lightController.getUuidStr_mainService()));
            if (gattServices != null) {
                for (BluetoothGattService iService : gattServices) {
                    if (iService.getUuid() != null
                            && iService.getUuid().toString().equals(lightController.getUuidStr_mainService())) {
                        service = iService;
                        break;
                    }
                }
            }

            if (service == null) {
                logD(TAGG+TAGG+"Could not get service from any provided list of gattServices, getting from gatt object...");
                service = gatt.getService(UUID.fromString(lightController.getUuidStr_mainService()));
            }

            if (service == null) {
                logW(TAGG+TAGG+"Failed to get service. Throwing exception.");
                throw new Exception();
            } else {
                characteristic = service.getCharacteristic(UUID.fromString(lightController.getUuidStr_characteristicForWritingCommands()));
            }

            if (characteristic == null) {
                logW(TAGG+TAGG+"Failed to get characteristic from provided gatt and its service. Throwing exception.");
                throw new Exception();
            }

            characteristic.setValue(command);
            logV(TAGG+TAGG+"Writing characteristic value \""+ConversionUtils.byteArrayToHexString(characteristic.getValue())+"\"...");
            ret = gatt.writeCharacteristic(characteristic);
        } catch (Exception e) {
            logE(TAGG+TAGG+"Exception caught with creating/setting/writing characteristic: "+e.getMessage());
        }

        return ret;
    }

    /** Encode a light command...
     * Takes a banner light command (decimal version) and generates the gatt byte array.
     * NOTE: Does not account for additional commands (like flashing). */
    private byte[] encodeLightCommandBytesFromBannerLightCommand(int dbb_light_signal_asInt) {
        final String TAGG = "encodeLightCommandBytesFromBannerLightCommand(\""+dbb_light_signal_asInt+"\") : ";
        logD(TAGG+TAGG+"Invoked.");

        byte[] lightCommand;

        try {
            // Translate the signal-light command from message into a light controller command
            if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_OFF) {
                lightCommand = lightController.constructLightCommandByteSequence_turnOff();
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_RED_BRI) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_red(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_RED_MED) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMedBrightness(lightController.constructDataBytes_color_red());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_RED_DIM) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMinBrightness(lightController.constructDataBytes_color_red());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_GREEN_BRI) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_green(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_GREEN_MED) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMedBrightness(lightController.constructDataBytes_color_green());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_GREEN_DIM) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMinBrightness(lightController.constructDataBytes_color_green());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_BLUE_BRI) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_blue(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_BLUE_MED) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMedBrightness(lightController.constructDataBytes_color_blue());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_BLUE_DIM) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMinBrightness(lightController.constructDataBytes_color_blue());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_ORANGE_BRI) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_orange(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_ORANGE_MED) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMedBrightness(lightController.constructDataBytes_color_orange());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_ORANGE_DIM) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMinBrightness(lightController.constructDataBytes_color_orange());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PINK_BRI) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_pink(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PINK_MED) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMedBrightness(lightController.constructDataBytes_color_pink());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PINK_DIM) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMinBrightness(lightController.constructDataBytes_color_pink());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PURPLE_BRI) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_purple(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PURPLE_MED) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMedBrightness(lightController.constructDataBytes_color_purple());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PURPLE_DIM) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMinBrightness(lightController.constructDataBytes_color_purple());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_YELLOW_BRI) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_yellow(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_YELLOW_MED) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMedBrightness(lightController.constructDataBytes_color_yellow());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_YELLOW_DIM) {
                lightCommand = lightController.constructLightCommandByteSequence_colorMinBrightness(lightController.constructDataBytes_color_yellow());
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITECOOL_BRI
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEPURE_BRI
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEWARM_BRI) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_whiteMaxBrightnessSteady();
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITECOOL_MED
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEPURE_MED
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEWARM_MED) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_whiteMedBrightness();
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITECOOL_DIM
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEPURE_DIM
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEWARM_DIM) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_whiteMinBrightness();
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_RED
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_RED) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_red(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_GREEN
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_GREEN) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_green(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_BLUE
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_BLUE) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_blue(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_ORANGE
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_ORANGE) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_orange(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_PINK
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_PINK) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_pink(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_PURPLE
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_PURPLE) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_purple(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_YELLOW
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_YELLOW) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController.constructDataBytes_color_yellow(lightController.COLOR_BRIGHTNESS_MAX));
            } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITECOOL
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITECOOL
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITEPURE
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITEPURE
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITEWARM
                    || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITEWARM) {   //TODO: differentiate
                lightCommand = lightController.constructLightCommandByteSequence_whiteMaxBrightnessSteady();
            } else {
                //default
                lightCommand = lightController.constructLightCommandByteSequence_whiteRgbMinBrightness();
            }
        } catch (Exception e) {
            logE(TAGG+TAGG+"Exception caught: "+e.getMessage());
            lightCommand = null;
        }

        return lightCommand;
    }

    /** Initiate a command to the lights.
     * Returns whether light command was initiated or not. */
    private boolean initiateLightCommand(String dbb_light_signal) {
        //initiateLightCommand(dbb_light_signal, true);
        return initiateLightCommand(dbb_light_signal, false);
    }
    private boolean initiateLightCommand(String dbb_light_signal, boolean doForceSend) {
        final String TAGG = "initiateLightCommand(\""+dbb_light_signal+"\"): ";
        logD(TAGG+TAGG+"Invoked.");

        int dbb_light_signal_asInt;
        byte[] lightCommand;
        byte[] lightCommandAdditional = null;

        // Make sure we're not currently locked out
        // (WARNING: this is a high-level flag and you should be careful with it! Designed to be used when preparing for reboot, etc.)
        if (preventSendingAnyLightCommands) {
            logW(TAGG+TAGG+"Light command send lockout is in effect. Will not send any command.");
            return false;
        }

        // Translate the signal-light command from Banner into a command we can use
        // Convert the ASCII character given in the message record field to decimal so we can compare strings values
        dbb_light_signal_asInt = (int) dbb_light_signal.charAt(0);

        // Check if we need to send a command
        /*
        if (dbb_light_signal_asInt == currentLightCommand_asDecimal
                && !doForceSend) {
            //no need to send another identical command?
            logI(TAGG+TAGG+"This command is same as a currently active command. No need to send again. Aborting!");
            return;
        }
        */

        lightCommand = encodeLightCommandBytesFromBannerLightCommand(dbb_light_signal_asInt);

        if (lightCommand == null) {
            logW(TAGG+TAGG+"Could not get a light command value, aborting.");
            return false;
        }

        // Translate the signal-light command from message into any additional light controller command that may be needed
        if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_RED
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_RED) {   //TODO: differentiate
            lightCommandAdditional = lightController.constructLightCommandByteSequence_flashingOn();
        } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_GREEN
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_GREEN) {   //TODO: differentiate
            lightCommandAdditional = lightController.constructLightCommandByteSequence_flashingOn();
        } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_BLUE
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_BLUE) {   //TODO: differentiate
            lightCommandAdditional = lightController.constructLightCommandByteSequence_flashingOn();
        } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_ORANGE
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_ORANGE) {   //TODO: differentiate
            lightCommandAdditional = lightController.constructLightCommandByteSequence_flashingOn();
        } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_PINK
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_PINK) {   //TODO: differentiate
            lightCommandAdditional = lightController.constructLightCommandByteSequence_flashingOn();
        } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_PURPLE
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_PURPLE) {   //TODO: differentiate
            lightCommandAdditional = lightController.constructLightCommandByteSequence_flashingOn();
        } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_YELLOW
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_YELLOW) {   //TODO: differentiate
            lightCommandAdditional = lightController.constructLightCommandByteSequence_flashingOn();
        } else if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITECOOL
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITECOOL
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITEPURE
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITEPURE
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITEWARM
                || dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITEWARM) {   //TODO: differentiate
            lightCommandAdditional = lightController.constructLightCommandByteSequence_flashingOn();
        }

        // Set flags depending on whether a signal-light message has been dispatched
        if (dbb_light_signal_asInt == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_NONE) {
            //aMsgLightCommandIsActive = false;
            currentLightCommand_asDecimal = MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_NONE;
        } else {
            //aMsgLightCommandIsActive = true;
            currentLightCommand_asDecimal = dbb_light_signal_asInt;
        }
        currentLightCommand_asByteArray = lightCommand;

        // Check for an existing call so we don't do multiple times at once
        if (aCommandIsTryingToSend && !doForceSend) {
            //abort
            logW(TAGG+TAGG + "A command is already trying to send, and we're not supposed to force it, aborting.");
            return false;
        } else if (thisCommandWouldBeRepeatOfLastSuccessfulCommand(dbb_light_signal) && !doForceSend) {
            logI(TAGG+TAGG+"This command seems unnecessary, as a repeat of the last successful command. Not sending.");
            return false;
        } else {
            //start send

            // Instantiate a GATT callback containing the stuff we want to do
            //BluetoothGattCallback gattClientCallbackForCommand = new BluetoothGattCallback(lightCommand, lightCommandAdditional);
            mGattClientCallback = new GattClientCallback(lightCommand, lightCommandAdditional, currentLightCommand_asDecimal);

            // Initiate connection to device's GATT server, passing in our callback (which takes care of sending the command when it's ready)
            //connectGattDevice(gattClientCallbackForCommand);
            connectGattDevice(mGattClientCallback, doForceSend);

            return true;
        }
    }


    private boolean thisCommandWouldBeRepeatOfLastSuccessfulCommand(String dbb_light_signal) {
        final String TAGG = "thisCommandWouldBeRepeatOfLastSuccessfulCommand: ";
        boolean ret = false;

        if (dbb_light_signal == null) {
            logW(TAGG+TAGG + "Provided light signal is null. That should not be.");
        } else if (aCommandIsTryingToSend) {
            logW(TAGG+TAGG+"A command is currently trying to send. Comparison may be unreliable.");
        } else if (!lastWriteWasSuccessful) {
            logW(TAGG+TAGG+"Last command was not successful. Comparison may be unreliable.");
        } else {
            /*
            String lastSuccessfulCommand = Character.toString(lastWrittenLightCommand_asASCII);
            logD(TAGG+TAGG+"Last successful command: \""+lastSuccessfulCommand+"\".");

            if (dbb_light_signal.equals(lastSuccessfulCommand)) {
                //yes, it would be a repeat
                ret = true;
            }
            */

            // Convert the string-version light signal character to an actual char type and then a byte array of the actual light-controller command
            char tempChar = dbb_light_signal.toCharArray()[0];
            byte[] cmdBytes = encodeLightCommandBytesFromBannerLightCommand((int) tempChar);

            // Convert the raw commands into strings we can use to compare easily
            String thisCmdString = ConversionUtils.byteArrayToHexString(cmdBytes);
            String lastCmdString = ConversionUtils.byteArrayToHexString(lastWrittenLightCommand_asByteArray);
            logV(TAGG+TAGG+"This command: \""+thisCmdString+"\".");
            logV(TAGG+TAGG+"Last successful command: \""+lastCmdString+"\".");

            if (thisCmdString.equalsIgnoreCase(lastCmdString)) {
                //yes, it would be a repeat
                ret = true;
            }
        }

        logV(TAGG+TAGG+"Returning "+ String.valueOf(ret));
        return ret;
    }

    /***********************************************************************************************
     * Subclass for communication to this service from another process.
     * This is the main mechanism for actually making the lights do stuff!
     * NOTE: Initially tried Handler, but doesn't seem to work with Service.
     *
     * Revisions:
     *  2018.10.31  Chris Rider     Created.
     */
    public class LightCommandBroadcastReceiver extends BroadcastReceiver {
        final String TAGG = LightCommandBroadcastReceiver.class.getSimpleName();

        public static final String INTENTEXTRA_LIGHTCMD = "com.messagenetsystems.evolutionflasherlights.intentextra.lightcmd";
        public static final String INTENTEXTRA_LIGHTCMD_DOFORCE = "com.messagenetsystems.evolutionflasherlights.intentextra.lightcmd.doforce";
        public static final String INTENTEXTRA_LIGHTCMD_DOPREVENTFURTHERCOMMANDS = "com.messagenetsystems.evolutionflasherlights.intentextra.lightcmd.dopreventfurthercommands";
        public static final int CMD_LIGHTS_UNKNOWN = 0;
        public static final int CMD_LIGHTS_DO_SOMETHING = 1;
        public static final boolean CMD_LIGHTS_FORCE_SEND_NO = false;
        public static final boolean CMD_LIGHTS_FORCE_SEND_YES = true;
        public static final boolean CMD_LIGHTS_DO_PREVENT_FURTHER_COMMANDS_NO = false;
        public static final boolean CMD_LIGHTS_DO_PREVENT_FURTHER_COMMANDS_YES = true;

        private Bundle intentExtras;
        private int lightCmd;
        private boolean lightCmdDoForce;
        private boolean lightCmdDoPreventFurtherCommands;

        private String dbb_light_signal;
        private int dbb_light_duration;

        // Constructor
        LightCommandBroadcastReceiver() {
            final String TAGG = this.TAGG+" (constructor): ";
            logD(TAGG+TAGG+"Invoked.");
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            final String TAGG = this.TAGG+": onReceive: ";
            logD(TAGG+TAGG+"Invoked.");

            try {
                // Get the extras object from the broadcast's intent
                intentExtras = intent.getExtras();
                if (intentExtras == null) {
                    logW(TAGG+TAGG+"No extras provided by intent, so cannot get data. Aborting any TTS functions for this attempt.");
                    return;
                }

                // Get the purpose of this broadcast from the extras
                lightCmd = intentExtras.getInt(INTENTEXTRA_LIGHTCMD, CMD_LIGHTS_UNKNOWN);           //default to unknown if nothing available
                if (lightCmd == 0) {
                    logW(TAGG+TAGG+"No purpose provided by intent, so don't know what to do. Aborting.");
                    return;
                } else {
                    dbb_light_signal = intentExtras.getString(INTENTEXTRA_SIGNALLIGHTCMD);
                }

                // Get whether we're supposed to force send (default to NO if nothing)
                lightCmdDoForce = intentExtras.getBoolean(INTENTEXTRA_LIGHTCMD_DOFORCE, CMD_LIGHTS_FORCE_SEND_NO);

                // Get whether we're supposed to set the prevent further commands flag (default to NO if nothing)
                lightCmdDoPreventFurtherCommands = intentExtras.getBoolean(INTENTEXTRA_LIGHTCMD_DOPREVENTFURTHERCOMMANDS, CMD_LIGHTS_DO_PREVENT_FURTHER_COMMANDS_NO);

                // Validate light signal command
                if (dbb_light_signal == null
                        || dbb_light_signal.isEmpty()
                        || dbb_light_signal.length() < 1
                        || dbb_light_signal.equals("")) {
                    //dbb_light_signal = String.valueOf((char) SIGNALLIGHT_CMD_NONE);
                    logW(TAGG+TAGG+"No light signal from intent extras. Aborting.");
                    return;
                }

                // Check for existing command attempt to avoid stomping all over each other
                if (aCommandIsTryingToSend && !lightCmdDoForce) {
                    logW(TAGG+TAGG+"A command is already trying to send to the lights and hasn't finished, and not forced to send. Aborting.");
                    return;
                }

                // Take appropriate action, depending on what the purpose is
                switch (lightCmd) {
                    case CMD_LIGHTS_DO_SOMETHING:
                        //if (thisCommandWouldBeRepeatOfLastSuccessfulCommand(dbb_light_signal) && !lightCmdDoForce) {
                        //    logI(TAGG+TAGG+"This command seems unnecessary. Not sending.");
                        //} else {
                            //call method to send command to lights
                            if (initiateLightCommand(dbb_light_signal, lightCmdDoForce)) {
                                //set flag to prevent any further commands, if needed
                                //(we do it only if a light command was initiated in the first place)       //TODO: maybe unset the prevention flag if gatt was unsuccessful, so as to prevent total forever lockout?
                                if (lightCmdDoPreventFurtherCommands) {
                                    BluetoothFlasherLightsService.preventSendingAnyLightCommands = true;
                                }
                            } else {
                                logW(TAGG+TAGG+"Call to initiateLightCommand reports nothing happened. Trying again...");

                                if (initiateLightCommand(dbb_light_signal, lightCmdDoForce)) {
                                    //set flag to prevent any further commands, if needed
                                    //(we do it only if a light command was initiated in the first place)       //TODO: maybe unset the prevention flag if gatt was unsuccessful, so as to prevent total forever lockout?
                                    if (lightCmdDoPreventFurtherCommands) {
                                        BluetoothFlasherLightsService.preventSendingAnyLightCommands = true;
                                    }
                                } else {
                                    logW(TAGG+TAGG+"Retry-call to initiateLightCommand reports nothing happened, again. Trying again after a delay...");

                                    new Handler().postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            logD(TAGG+TAGG + "Delayed Runnable now re-calling initiateLightCommand("+dbb_light_signal+")...");
                                            initiateLightCommand(dbb_light_signal);
                                        }
                                    }, 3000);
                                }
                            }
                        //}
                        break;
                    default:
                        logW(TAGG+TAGG+"Unhandled case (cmd = "+ String.valueOf(lightCmd)+").");
                        break;
                }
            } catch (Exception e) {
                logE(TAGG+TAGG+"Exception caught: "+e.getMessage());
            }
        }
    }

    /***********************************************************************************************
     * Subclass for thread to ensure that lights are in correct state.
     * This allows us to be more intelligent instead of brute-forcing the initiate calls.
     *  If no messages, then ensure lights are in default state.
     *  If some message that needs lights, ensure lights are correct.
     *
     * Revisions:
     *  2018.11.26  Chris Rider     Created (initially for just default state when no messages).
     *  2018.11.27  Chris Rider     Modified to handle all states, even for messages.
     *  2019.01.14  Chris Rider     Modified to check for running main app instead of active messages, and command default light state if it's invalid somehow.
     */
    public class LightStateManagerThread extends Thread {
        private String TAGG = LightStateManagerThread.class.getSimpleName();
        private long pid;
        private int initialWaitPeriodMS;
        private int workCycleRestPeriodMS;
        private String defaultSignalLightValue;
        private int periodicSoftForceFactor;        //soft-force is doing a gatt read to verify the actual mode
        private int periodicHardForceFactor;        //hard-force is doing a gatt write to make sure it gets the mode
        private boolean forceDoForceThisCycle;

        LightStateManagerThread(Context appContext) {
            initialWaitPeriodMS = 10000;
            workCycleRestPeriodMS = 2000;
            //defaultSignalLightValue = appContext.getResources().getString(R.string.SIGNALLIGHT_CMD_NONE);
            defaultSignalLightValue = String.valueOf((char) MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_NONE);
            periodicSoftForceFactor = 10;           //every Nth work cycle to read from the controller the actual mode and maybe do a write to correct it
            periodicHardForceFactor = 30;           //every Nth work cycle to force a send (just to make sure light controller gets the command)
            forceDoForceThisCycle = false;
        }

        /** Main runnable routine (executes once whenever the initialized thread is commanded to start running) */
        @Override
        public void run() {
            final String TAGG = "run: ";
            long cycleNumber = 0;
            logD(TAGG+this.TAGG + TAGG + "Invoked.");

            pid = Thread.currentThread().getId();
            this.TAGG = this.TAGG + " #"+pid+": ";
            logI(TAGG+this.TAGG + TAGG + "Thread now running.");

            // As long as our thread is supposed to be running, start doing work-cycles until it's been flagged to interrupt (rest period happens at the end of the cycle)...
            while (!Thread.currentThread().isInterrupted()) {

                // Take a rest before beginning our first iteration (to give time for the things we're monitoring to potentially come online)...
                if (cycleNumber == 0) {
                    try {
                        Thread.sleep(initialWaitPeriodMS);
                    } catch (InterruptedException e) {
                        logE(TAGG+this.TAGG + TAGG + "Exception caught trying to sleep for first-run (" + workCycleRestPeriodMS + "ms). Broadcasting this error status and stopping.\n" + e.getMessage());
                        Thread.currentThread().interrupt();
                    }
                }

               /* START MAIN THREAD-WORK
                * Note: you don't need to exit or break for normal work; instead, only continue (so cleanup and rest can occur at the end of the iteration/cycle) */

                // Indicate that this thread is beginning work tasks (may be useful for outside processes to know this)
                cycleNumber++;
                logV(TAGG+this.TAGG + TAGG + "=======================================(start)");
                logV(TAGG+this.TAGG + TAGG + "BEGINNING WORK CYCLE #" + cycleNumber + "...");

                // If StartupActivity is running, then don't stomp all over what the user might be doing
                if (StartupActivity.activityActive) {
                    //don't do anything
                } else {

                    // Figure out whether we force-send this cycle or not
                /*
                if (cycleNumber % periodicHardForceFactor == 0) {
                    logD(TAGG+this.TAGG + TAGG + "Forcing send this time!");
                    forceDoForceThisCycle = LightCommandBroadcastReceiver.CMD_LIGHTS_FORCE_SEND_YES;
                } else {
                    forceDoForceThisCycle = LightCommandBroadcastReceiver.CMD_LIGHTS_FORCE_SEND_NO;
                }
                */
                /*
                if (cycleNumber % periodicSoftForceFactor == 0) {
                    logD(TAGG+this.TAGG + TAGG + "Asynchronously reading actual light controller state this time...");
                    readCharacteristicValue_lightCommand(mBluetoothGatt, null);         //refer to onReadCharacteristic for saving the actual value
                }
                */

                    // If no messages, request default light state
                    //if (SmmonService.activeBannerMessages != null && SmmonService.activeBannerMessages.messages != null && SmmonService.activeBannerMessages.messages.size() == 0) {
                    //    logD(TAGG+this.TAGG + TAGG + "No active banner messages, initiating default light mode...");
                    //    initiateLightCommand(defaultSignalLightValue, forceDoForceThisCycle);
                    //} else if (SmmonService.activeBannerMessages != null && SmmonService.activeBannerMessages.messages != null && SmmonService.activeBannerMessages.messages.size() > 0) {
                    //    // Figure out if currently-delivering message needs a light mode
                    //    /*
                    //    if (ScrollMsgWithDetails.isInFront && ScrollMsgWithDetails.msgIsScrolling && ScrollMsgWithDetails.currentlyDisplayingMessageUUID != null) {
                    //        //show this message's light mode
                    //        BannerMessage bmo = SmmonService.activeBannerMessages.getMsgAsBannerMessageObjectForUUID(ScrollMsgWithDetails.currentlyDisplayingMessageUUID);
                    //        if (bmo != null) {
                    //            logD(TAGG+this.TAGG + TAGG + "Initiating light mode ("+bmo.dbb_light_signal+") for currently delivering message ("+ScrollMsgWithDetails.currentlyDisplayingMessageUUID.toString()+")...");
                    //            initiateLightCommand(bmo.dbb_light_signal, forceDoForceThisCycle);
                    //        }
                    //    } else {
                    //        logI(TAGG+this.TAGG + TAGG+"Unhandled delivering-message light mode. Nothing to do.");
                    //    }
                    //    */
                    //    logD(TAGG+this.TAGG + TAGG + "Active banner messages, allowing delivery activity to control light mode...");
                    // If no main app running, then do default light state
                    if (SettingsUtils.isMainAppRunningOK()) {
                        logD(TAGG + this.TAGG + TAGG + "Main app is running ok, leaving lights alone.");
                    } else if (mainApplication.numOfDeliveringMsgsInMainApp == 0) {
                        logD(TAGG + this.TAGG + TAGG + "There are NO messages delivering. Initiating default light mode...");
                        initiateLightCommand(defaultSignalLightValue, forceDoForceThisCycle);
                    } else if (mainApplication.numOfDeliveringMsgsInMainApp > 0) {
                        logD(TAGG + this.TAGG + TAGG + "There are "+Integer.toString(mainApplication.numOfDeliveringMsgsInMainApp)+" messages delivering, leaving lights alone.");
                    } else {
                        logW(TAGG+this.TAGG + TAGG + "Problem accessing active banner messages data. Initiating default light mode...");
                        initiateLightCommand(defaultSignalLightValue, forceDoForceThisCycle);
                    }
                }

               /* END MAIN THREAD-WORK */

                // Take a rest before next iteration (to make sure this thread doesn't run full tilt)...
                try {
                    Thread.sleep(workCycleRestPeriodMS);
                } catch (InterruptedException e) {
                    logE(TAGG+this.TAGG + TAGG + "Exception caught trying to sleep for interval (" + workCycleRestPeriodMS + "ms). Thread stopping.\n" + e.getMessage());
                    Thread.currentThread().interrupt();
                }

                // Thread has been flagged to interrupt, so let's end it and clean up...
                if (Thread.currentThread().isInterrupted()) {
                    logD(TAGG+this.TAGG + TAGG + "Stopping.");    //just log it, as the loop's conditional will break out and actually halt the thread
                }
            }//end while
        }//end run()
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
