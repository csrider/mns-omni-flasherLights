package com.messagenetsystems.evolutionflasherlights;

/* MainApplication
 * Application class for global stuff.
 * This gets executed when the app starts, before anything else (even though StartupActivity invokes it).
 * The primary advantage is to have a one-place/one-time initialization of data that all other classes can use, without them needing to init the data.
 * There may also be advantages in memory management and graceful handling of stuff?
 *
 * Flow (remember this particular class happens implicitly with/before StartupActivity)
 *  o Entry (activities.StartupActivity via .receivers.BootReceiver, or manual launch of StartupActivity)
 *  |
 *  o Initializations and loads
 *
 * Usage:
 *  private MainApplication mainApplication;
 *  mainApplication = ((MainApplication) getApplicationContext());
 *
 * Info:
 *  - https://github.com/codepath/android_guides/wiki/Understanding-the-Android-Application-Class
 *
 * Revisions:
 *  2020.05.28      Chris Rider     Created (used latest version of main app's OmniApplication class file).
 *  2020.06.09      Chris Rider     Added methods to replace only matched portions of notification text, instead of the whole thing.
 *  2020.06.25      Chris Rider     Refactored whole app - moved bluetooth device and GATT resources into this class so they can be global.
 *  2020.06.29      Chris Rider     Ability to update notification with light status.
 *  2020.06.30      Chris Rider     More logging for connection process when executing light command
 *  2020.07.01      Chris Rider     More stuff to try to clear out error 133 or similar issues.. not really fixed, but can't hurt.
 *                                  Got notification bar action item to turn activate light standby mode working.
 *  2020.07.02      Chris Rider     Implemented doForce flag in executeLightCommand method, notification text tweaks, and optimized delayed cleanup Runnable/Handler operations.
 *  2020.07.06      Chris Rider     Trying out a scan routine (instead of direct acquisition of device), didn't really work, but keeping it around in a deactivated state.
 */

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Notification;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.bosphere.filelogger.FLConfig;
import com.bosphere.filelogger.FLConst;
import com.messagenetsystems.evolutionflasherlights.activities.StartupActivity;
import com.messagenetsystems.evolutionflasherlights.bluetooth.BluetoothGattCallback;
import com.messagenetsystems.evolutionflasherlights.devices.BluetoothLightController_HY254117V9;
import com.messagenetsystems.evolutionflasherlights.models.FlasherLights;
import com.messagenetsystems.evolutionflasherlights.receivers.NotificationActionReceiver;
import com.messagenetsystems.evolutionflasherlights.services.BluetoothFlasherLightsService;
import com.messagenetsystems.evolutionflasherlights.utilities.ConversionUtils;
import com.messagenetsystems.evolutionflasherlights.utilities.FileUtils;
import com.messagenetsystems.evolutionflasherlights.utilities.SettingsUtils;
import com.messagenetsystems.evolutionflasherlights.v3.GattCallback_HY254117;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


public class MainApplication extends Application {
    private final String TAG = this.getClass().getSimpleName();

    // Constants...
    public static final int NOTIF_REPLACE = 1;
    public static final int NOTIF_APPEND = 2;


    // Global data variables (private ones are available via getter methods)...
    private String appPackageName;
    public static String appPackageNameStatic;
    private String appVersion;
    private Date appStartedDate;
    private volatile boolean allowAppToDie;

    public static String definedLightControllerMAC;
    public static FlasherLights.OmniCommandCodes flasherLightOmniCommandCodes;
    public static BluetoothLightController_HY254117V9 lightControllerDeviceModel;

    public BluetoothDevice bluetoothDevice;
    public GattCallback_HY254117 bluetoothGattCallback;
    public BluetoothGatt bluetoothGatt;

    public volatile int numOfDeliveringMsgsInMainApp;
    public static volatile UUID msgCurrentlyDelivering_UUID;
    public static volatile long msgCurrentlyDelivering_exectedDeliveryDurationMS;
    public static volatile long msgCurrentlyDelivering_lightDurationMS;

    public static volatile Date mainAppHeartbeat_appStartedTimestamp;
    public static volatile Date mainAppLastCommunicationTimestamp;
    public static volatile Date mainAppLastCommunicationTimestamp_previous;

    public volatile boolean isBluetoothDeviceCommandUnderway;
    public volatile byte[] mostRecentRootCharacteristicWrittenToDevice_value;
    public volatile Date mostRecentRootCharacteristicWrittenToDevice_datetime;
    public volatile boolean isBluetoothGattConnectionUnderway;

    private LightTimeoutForceCleanupRunnable lightTimeoutForceCleanupRunnable;
    public static Handler lightTimeoutForceCleanupHandler;

    public volatile boolean bluetoothProblemExists_needDeviceReset = false;
    public long problemCount_status133 = 0;
    public long problemCount_serviceDiscovery = 0;


    // Local class stuff...
    private boolean isProvFileAvailable;            //we just save this so we don't have to call expensive I/O every time we want to know
    private NotificationCompat.Builder mNotifBuilder;
    private int mNotifID;
    private Notification mNotification;

    Intent stopMainServiceIntent;
    PendingIntent stopMainServicePendingIntent;

    Intent turnLightsStandbyIntent;
    PendingIntent turnLightsStandbyPendingIntent;

    private Intent mainServiceIntent;
    private BluetoothFlasherLightsService mainService;


    /*============================================================================================*/
    /* Application class methods */

    // Called when the application is starting, before any other application objects have been created.
    @Override
    public void onCreate() {
        super.onCreate();
        final String TAGG = "onCreate: ";
        Log.v(TAG, TAGG+"Invoked.");

        // First, before anything, get our logger setup...
        initLoggingUtility();   //logging utility (this needs to be done ASAP before anything else)

        // Initialize stuff...
        initLocalStuff();       //local stuff for the inner workings of this class (do this before globals)
        initGlobalData();       //global (via getters) data variables (requires locals to be initialized first)

        // Setup the initial notification and show it
        // We do this here after inits, since it may contain information that needs initialized first
        try {
            String notifText = "Starting";
            mNotifBuilder = createNotificationBuilderObject(notifText);
            showNotif(mNotifBuilder);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to setup notification: "+e.getMessage());
        }

        // Register Activity Life Cycle callback, so we can get currently running activity in our app from currentVisibleActivity
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                final String TAGG = "onActivityCreated("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }

            @Override
            public void onActivityStarted(Activity activity) {
                final String TAGG = "onActivityStarted("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }

            @Override
            public void onActivityResumed(Activity activity) {
                final String TAGG = "onActivityResumed("+activity.getClass().getSimpleName()+"): ";
                FL.i(TAG, TAGG+"Invoked.");

                //setCurrentVisibleActivity(activity);
            }

            @Override
            public void onActivityPaused(Activity activity) {
                final String TAGG = "onActivityPaused("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");

                //setCurrentVisibleActivity(null);
            }

            @Override
            public void onActivityStopped(Activity activity) {
                final String TAGG = "onActivityStopped("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                final String TAGG = "onActivitySaveInstanceState("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
                final String TAGG = "onActivityDestroyed("+activity.getClass().getSimpleName()+"): ";
                FL.d(TAG, TAGG+"Invoked.");
            }
        });
    }

    // Called by the system when the device configuration changes while your app is running.
    // At the time that this function has been called, your Resources object will have been updated to return resource values matching the new configuration.
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        final String TAGG = "onConfigurationChanged: ";
        FL.i(TAG, TAGG+"Invoked.");

        //TODO? Depending on what "configuration" means, you may need to reload/reinit stuff?
    }

    // Called when the overall system is running low on memory, and would like actively running processes to tighten their belts.
    // Actively running processes should trim their memory usage when this is invoked.
    // You should implement this method to release any caches or other unnecessary resources you may be holding onto.
    // The system will perform a garbage collection for you after returning from this method.
    // PREFERABLY, you should implement ComponentCallbacks2 to incrementally unload your resources based on various levels of memory demands (API 14 and higher).
    // This method should, then, be a fallback for older versions (which can be treated same as ComponentCallbacks2#onTrimMemory with the ComponentCallbacks2#TRIM_MEMORY_COMPLETE level)
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        final String TAGG = "onLowMemory: ";
        FL.i(TAG, TAGG+"Invoked.");
    }

    // Called when the OS has determined that it's a good time for a process to trim unneeded memory from its processes.
    // This will happen for example when it goes to background and there's not enough memory to keep as many background processes running as desired.
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        final String TAGG = "onTrimMemory: ";
        FL.i(TAG, TAGG+"Invoked.");
    }

    // This method is for use in emulated process environments.
    // It will never be called on a production Android device!
    @Override
    public void onTerminate() {
        super.onTerminate();
        final String TAGG = "onTerminate: ";
        FL.d(TAG, TAGG+"Invoked.");
    }


    /*============================================================================================*/
    /* Supporting methods */

    // Load up the logging utility for the entire app to use.
    // NOTE: THIS SHOULD BE INVOKED AS EARLY AS POSSIBLE!
    // Note: this will do a short thread sleep before returning!
    private void initLoggingUtility() {
        final String TAGG = "initLoggingUtility: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            // Ensure we have storage-access permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, TAGG+"Permission WRITE_EXTERNAL_STORAGE not granted. Logger cannot function!");
                return;
            }

            // Configure prerequisites to feed into logging service configurator
            File logDirectory = new File(Environment.getExternalStorageDirectory(), "logs_"+String.valueOf(getPackageName()));

            // Configure and initialize logging service
            FL.init(new FLConfig.Builder(this)
                    //.logger(...)                                                                    //customize how to hook up with logcat
                    .defaultTag(getResources().getString(R.string.app_name))                         //customize default tag
                    .minLevel(FLConst.Level.V)                                                      //customize minimum logging level
                    .logToFile(true)                                                                //enable logging to file
                    .dir(logDirectory)                                                              //customize directory to hold log files
                    //.formatter(...)                                                                 //customize log format and file name
                    .retentionPolicy(FLConst.RetentionPolicy.TOTAL_SIZE)                            //customize retention strategy
                    //.maxFileCount(FLConst.DEFAULT_MAX_FILE_COUNT)                                   //customize how many log files to keep if retention strategy is by file count (defaults to 168)
                    .maxTotalSize(FLConst.DEFAULT_MAX_TOTAL_SIZE)                                   //customize how much space log files can occupy if strategy is by total size (in bytes... defaults to 32MB)
                    .build());

            // Overall toggle to enable/disable logging!
            FL.setEnabled(true);

            // Give a second for things to finish and become ready
            // We do this in case other stuff starts to log right away
            try {
                Thread.sleep(1000);
            } catch (Exception e) {
                Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught initializing logging utility: "+e.getMessage());
        }
    }

    // Initialize local resources
    private void initLocalStuff() {
        final String TAGG = "initLocalStuff: ";

        this.isProvFileAvailable = isProvFileAvailable();

        flasherLightOmniCommandCodes = new FlasherLights.OmniCommandCodes(FlasherLights.PLATFORM_MNS); //TODO: Make this not hard-coded!

        // Dev-Note: We shouldn't have to bother with this kind of thing, since our only service is sticky
        //stopMainServiceIntent = new Intent(this, MainServiceStopRequestReceiver.class);
        //stopMainServicePendingIntent = PendingIntent.getBroadcast(this, 0, stopMainServiceIntent, 0);

        turnLightsStandbyIntent = new Intent(this, NotificationActionReceiver.class)
                .setAction(FlasherLights.Intents.Actions.DO_LIGHT_COMMAND)
                .putExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD, flasherLightOmniCommandCodes.CMD_LIGHT_STANDBY);
        turnLightsStandbyPendingIntent = PendingIntent.getBroadcast(this, 0, turnLightsStandbyIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        this.mNotifID = Integer.parseInt(new SimpleDateFormat("ddHHmmss", Locale.US).format(new Date()));

        this.lightTimeoutForceCleanupRunnable = new LightTimeoutForceCleanupRunnable();
    }

    // Initialize "global" data
    // Remember, this gets made available globally by getter methods!
    // WARNING: You must call initLocalStuff first!
    private void initGlobalData() {
        final String TAGG = "initGlobalData: ";

        this.appPackageName = loadAppPackageName(getApplicationContext());
        appPackageNameStatic = appPackageName;
        this.appVersion = loadAppVersion(getApplicationContext());
        this.appStartedDate = new Date();
        this.allowAppToDie = false;

        this.mainService = new BluetoothFlasherLightsService();
        this.mainServiceIntent = new Intent(getApplicationContext(), mainService.getClass());

        // Initialize our configured/defined light controller MAC address
        //definedLightControllerMAC = SettingsUtils.getSharePrefsFlasherLightControllerMacAddress();
        definedLightControllerMAC = SettingsUtils.getProvFileFlasherLightControllerMacAddress();
        if (!SettingsUtils.isThisMacAddressValid(definedLightControllerMAC)) {
            FL.w(TAGG+TAGG+"Configured flasher light MAC address is invalid. Won't use it.");
            definedLightControllerMAC = null;
        }

        //flasherLightOmniCommandCodes = new FlasherLights.OmniCommandCodes(FlasherLights.PLATFORM_MNS); //TODO: Make this not hard-coded!
        lightControllerDeviceModel = new BluetoothLightController_HY254117V9(Constants.LOG_METHOD_FILELOGGER);

        this.bluetoothDevice = null;
        this.bluetoothGattCallback = new GattCallback_HY254117(Constants.LOG_METHOD_FILELOGGER);
        if (this.bluetoothGatt != null) {
            this.bluetoothGatt.close();
            this.bluetoothGatt = null;
        }

        this.numOfDeliveringMsgsInMainApp = 0;

        msgCurrentlyDelivering_UUID = null;
        msgCurrentlyDelivering_exectedDeliveryDurationMS = 0;
        msgCurrentlyDelivering_lightDurationMS = 0;

        mainAppLastCommunicationTimestamp = null;
        mainAppLastCommunicationTimestamp_previous = null;
        mainAppHeartbeat_appStartedTimestamp = null;

        this.isBluetoothDeviceCommandUnderway = false;
        mostRecentRootCharacteristicWrittenToDevice_value = null;
        mostRecentRootCharacteristicWrittenToDevice_datetime = null;
        this.isBluetoothGattConnectionUnderway = false;

        lightTimeoutForceCleanupHandler = new Handler(Looper.getMainLooper());
    }

    // Load app version and return it
    private String loadAppPackageName(Context appContext) {
        final String TAGG = "loadAppPackageName: ";
        String ret = "";

        try {
            ret = String.valueOf(appContext.getPackageName());
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    // Load app version and return it
    private String loadAppVersion(Context appContext) {
        final String TAGG = "loadAppVersion: ";
        String ret = "";

        try {
            PackageInfo pInfo = appContext.getPackageManager().getPackageInfo(getPackageName(), 0);
            ret = String.valueOf(pInfo.versionName);
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning \""+ret+"\".");
        return ret;
    }

    /// Check if provisioning file is available
    private boolean isProvFileAvailable() {
        final String TAGG = "isProvFileAvailable: ";
        boolean ret = false;

        try {
            FileUtils fileUtils = new FileUtils(getApplicationContext(), FileUtils.LOG_METHOD_FILELOGGER);
            ret = fileUtils.doesFileExist(FileUtils.FILE_PATH_EXTERNAL_STORAGE, getResources().getString(R.string.provfile_filename));
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }

        FL.d(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }

    // Check if specified Service is running
    public boolean isServiceRunning(Class<?> serviceClass) {
        final String TAGG = "isServiceRunning("+serviceClass.getSimpleName()+"): ";

        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                FL.i(TAG, TAGG+"Returning true.");
                return true;
            }
        }
        FL.i(TAG, TAGG+"Returning false.");
        return false;
    }

    // Stop the MainService
    // You may call this from anywhere with an OmniApplication instance! Convenient, huh?
    public void stopMainService() {
        final String TAGG = "stopMainService: ";

        try {
            // Stop the MainService
            setAllowAppToDie(true);
            stopService(mainServiceIntent);

            startActivity(new Intent(this, StartupActivity.class));
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught!", e);
        }
    }

    //private int retries = 0;      //DEV-NOTE: retry idea doesn't fix the issues
    public boolean executeLightCommand(byte flasherLightCommandCode, long durationS, UUID msgUUID, boolean doForce) {
        final String TAGG = "executeLightCommand: ";
        FL.d(TAGG+"Invoked for command: "+Byte.toString(flasherLightCommandCode)+" ("+flasherLightOmniCommandCodes.codeToEnglish(flasherLightCommandCode)+")");

        if (doForce) {
            this.bluetoothDevice = null;
            if (this.bluetoothGatt != null) {
                this.bluetoothGatt.close();
                this.bluetoothGatt = null;
            }
            if (this.bluetoothGattCallback != null) {
                this.bluetoothGattCallback.cleanup();
                this.bluetoothGattCallback = null;
            }
        }

        // Check whether we even need to execute a command (don't need to keep sending repeats of the same command, for instance)
        if (doForce || mostRecentRootCharacteristicWrittenToDevice_value == null) {
            //nothing has ever been written yet, so we may allow it
        } else if (Arrays.equals(mostRecentRootCharacteristicWrittenToDevice_value, ConversionUtils.convertCommandCodeToBleCharacteristicValueList(flasherLightCommandCode).get(0))) {
            FL.i(TAGG+"This light command would be a repeat of the most recently written command, so it's unnecessary to send it again, aborting.");
            return false;
        }

        // Decide how we proceed if there is a pending command that hasn't finished executing
        if (!doForce && this.isBluetoothDeviceCommandUnderway) {
            FL.w(TAGG+"A BLE command is pending, aborting.");
            return false;
        }

        this.isBluetoothDeviceCommandUnderway = true;

        try{
            // Ensure any previous GATT clients are cleared out before we proceed (to help avoid 133 status errors)
            if (this.bluetoothGatt != null) {
                FL.w(TAGG+"BluetoothGatt client instance not null. Perhaps we're still waiting on client device to let us go. Attempting resource reset and aborting so you may try again.");
                this.bluetoothGatt.disconnect();
                this.bluetoothGatt.close();
                this.bluetoothGatt = null;
                this.bluetoothGattCallback.cleanup();
                this.bluetoothGattCallback = null;
                this.bluetoothDevice = null;

                this.isBluetoothDeviceCommandUnderway = false;
                return false;

            /*
            if (retries < 2) {
                FL.w(TAGG + "BluetoothGatt client instance not null. Perhaps we're still waiting on client device to let us go. Retrying...");
                try {
                    Thread.sleep(1000);
                } catch (Exception e) {
                    FL.w(TAGG+"Exception caught sleeping on retry: "+e.getMessage());
                }
                retries++;
                executeLightCommand(flasherLightCommandCode, durationS, msgUUID, doForce);  //recurse
            } else {
                FL.w(TAGG+"BluetoothGatt client instance not null and retries did not succeed. Perhaps we're still waiting on client device to let us go. Aborting.");
                retries = 0;
                return false;
            }
            */
            }

            //---- DEVICE --------------------------------------------------------------------------
            //DEV-NOTE: Reinit every single time no matter what? Seems to exhibit good behavior doing so. Probably right way.
            if (!bluetoothDeviceInit(BLUETOOTH_DEVICE_INIT_METHOD_DIRECT)) {
                FL.w(TAGG+"Failed to initialize BluetoothDevice, aborting!");
                this.isBluetoothDeviceCommandUnderway = false;      //reinit
                return false;
            }
            /*
            bluetoothDeviceInit(BLUETOOTH_DEVICE_INIT_METHOD_SCAN);
            //while (isScanInProgress) {
            while (bluetoothDevice == null) {
                try {
                    FL.v(TAGG + "Waiting a brief time for scan to finish.");
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    FL.w(TAGG + "Exception caught waiting for scan to stop; should be alright, so continuing.");
                }
            }
            */

            //---- GATT CALLBACKS ------------------------------------------------------------------
            // Check if we have a callback instance and create it if we don't (should never happen)
            if (this.bluetoothGattCallback == null) {
                this.bluetoothGattCallback = new GattCallback_HY254117(Constants.LOG_METHOD_FILELOGGER);
            }
            if (this.bluetoothGattCallback != null) {
                FL.v(TAGG+"Created BluetoothGattCallback instance.");
            } else {
                FL.w(TAGG + "Failed to create BluetoothGattCallback instance, aborting.");
                this.isBluetoothDeviceCommandUnderway = false;
                return false;
            }

            // Set the BLE command that the callback instance should send to the lights when it establishes a connection
            // DEV-NOTE: Since we avoid duplicate/concurrent calls with the checks at the beginning of this method, this should not result
            // in any race conditions or anything like that, so probably no need to use synchronize or anything.
            this.bluetoothGattCallback.setFlasherLightCommandCodeToDo(flasherLightCommandCode);
            if (this.bluetoothGattCallback.getFlasherLightCommandCodeToDo() != flasherLightCommandCode) {
                FL.w(TAGG + "Failed to set command code in BluetoothGattCallback instance, aborting.");
                this.isBluetoothDeviceCommandUnderway = false;
                return false;
            }


            //---- GATT CLIENT/CONNECTION ----------------------------------------------------------
            // Clean up any old gatt client instances (to avoid any from piling up when we invoke connect)
            if (this.bluetoothGatt != null) {
                FL.v(TAGG+"Old BluetoothGatt client instance exists, closing and cleaning it up before continuing...");
                try {
                    this.bluetoothGatt.close();
                } catch (Exception e) {
                    FL.w(TAGG+"Exception caught closing GATT client. Continuing.");
                }
            }

            // Update notification just for FYI
            replaceNotificationWithLightStatus(flasherLightCommandCode, false);

            // Invoke connect and kick off the callback sequence
            this.bluetoothGatt = this.bluetoothDevice.connectGatt(getApplicationContext(),
                    Constants.GATT_AUTOCONNECT,
                    this.bluetoothGattCallback,
                    BluetoothDevice.TRANSPORT_LE);
            this.isBluetoothGattConnectionUnderway = true;

            // On delay, completely abort the operation if it doesn't respond back or complete in a reasonable amount of time...
            // We do this no matter whether the GATT process succeeds or not, just to make sure resources are cleared.
            lightTimeoutForceCleanupHandler.postDelayed(lightTimeoutForceCleanupRunnable, Constants.LIGHT_COMMAND_TIMEOUT_MS);

            return true;
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            return false;
        }
    }

    public static final byte BLUETOOTH_DEVICE_INIT_METHOD_DIRECT = 1;
    public static final byte BLUETOOTH_DEVICE_INIT_METHOD_SCAN = 2;
    private final int scanMaxSeconds = 1;
    private boolean isScanInProgress = false;
    private BtleScanCallback btleScanCallback;
    private BluetoothLeScanner bluetoothLeScanner;
    //private Handler scanStopHandler;
    public boolean bluetoothDeviceInit(byte deviceAcquisitionMethod) {
        final String TAGG = "bluetoothDeviceInit: ";
        boolean ret;

        try {
            final BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            bluetoothDevice = null;

            switch (deviceAcquisitionMethod) {
                case BLUETOOTH_DEVICE_INIT_METHOD_DIRECT:
                    // Directly acquire our device by its MAC address
                    bluetoothDevice = bluetoothAdapter.getRemoteDevice(definedLightControllerMAC.toUpperCase());
                    break;
                case BLUETOOTH_DEVICE_INIT_METHOD_SCAN:
                default:
                    // First, check if a scan is already happening
                    if (isScanInProgress) {
                        Log.w(TAG, TAGG + "A scan is already occurring. Aborting so as not to start another.");
                        break;
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
                            .setDeviceName(BluetoothLightController_HY254117V9.DEVICE_NAME)
                            .build();
                    filters.add(scanFilter);

                    // Initialize an instance of our scan-callback (which saves strongest scan result to class-global)
                    btleScanCallback = new BtleScanCallback();

                    // Now grab hold of the BluetoothLeScanner to start the scan, and set our scanning boolean to true.
                    Log.d(TAG, TAGG + "Scanning for " + scanMaxSeconds + " seconds...");
                    bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
                    bluetoothLeScanner.startScan(filters, settings, btleScanCallback);
                    isScanInProgress = true;

                    // At this point, we have a Bluetooth scan that will save all ScanResults into a map (and/or class-global variables)
                    // (that happens in the scan callback)

                    // Since scanning will go forever on its own, setup a handler to stop it after some time (it doesn't need to be long)
                    final HandlerThread handlerThread = new HandlerThread("background-thread");
                    handlerThread.start();
                    new Handler(handlerThread.getLooper()).postDelayed(new Runnable() {
                    //scanStopHandler = new Handler();
                    //scanStopHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            FL.d(TAGG + "Runnable executing to stop scan.");
                            try {
                                if (isScanInProgress) {
                                    FL.d(TAGG + "Scanning will now be commanded to stop.");

                                    bluetoothLeScanner.stopScan(btleScanCallback);
                                    isScanInProgress = false;

                                    try {
                                        FL.v(TAGG + "Waiting a brief time for scan to finish stopping.");
                                        Thread.sleep(100);
                                    } catch (InterruptedException e) {
                                        FL.w(TAGG + "Exception caught waiting for scan to stop; should be alright, so continuing.");
                                    }
                                } else {
                                    FL.i(TAGG+"Scan not in progress, unable to stop what's not happening.");
                                }

                                //handlerThread.interrupt();
                            } catch (Exception e) {
                                FL.w(TAGG+"Exception caught stopping scan: "+e.getMessage());
                            }
                        }
                    }, scanMaxSeconds*1000);

                    break;
            }
        } catch (Exception e) {
            FL.w(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        // Validate whether we succeeded and set return value as such
        if (deviceAcquisitionMethod != BLUETOOTH_DEVICE_INIT_METHOD_SCAN) {
            if (bluetoothDevice != null) {
                FL.v(TAGG + "Acquired BluetoothDevice from BluetoothAdapter.");
                ret = true;
            } else {
                FL.w(TAGG + "Failed to acquire BluetoothDevice from BluetoothAdapter.");
                ret = false;
            }
        } else {
            ret = false;
        }

        // Reinit anything dependent on this, since they would have been obliterated by this operation
        isBluetoothDeviceCommandUnderway = false;
        isBluetoothGattConnectionUnderway = false;

        FL.v(TAG, TAGG+"Returning: "+Boolean.toString(ret));
        return ret;
    }

    private class LightTimeoutForceCleanupRunnable implements Runnable {
        final String TAGG = LightTimeoutForceCleanupRunnable.class.getSimpleName()+": ";

        @Override
        public void run() {
            try {
                if (bluetoothGatt != null) {
                    FL.d(TAGG+"Light command timeout reached. Closing and cleaning up GATT, independently from callbacks.");

                    bluetoothGatt.close();
                    bluetoothGatt = null;

                    bluetoothGattCallback.cleanup();
                    bluetoothGattCallback = null;
                } else {
                    FL.d(TAG, TAGG+"GATT already closed and cleaned up (probably via callbacks). Nothing to do.");
                }
            } catch (Exception e) {
                FL.w(TAG, TAGG+"Exception caught explicitly closing GATT independently from callbacks: "+e.getMessage());
            }
        }
    }

    private class BtleScanCallback extends ScanCallback {
        private final String TAGG = BtleScanCallback.class.getSimpleName()+": ";

        boolean notYetFoundOurAssociatedDevice = true;
        int scannedStrongestRssi = Integer.MIN_VALUE;

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
                    Log.e(TAG, TAGG+TAGGG+"BLE scan failed with internal error!");
                    break;
                default:
                    Log.e(TAG, TAGG+TAGGG+"BLE scan failed!");
                    break;
            }
        }

        private void addScanResult(ScanResult result) {
            final String TAGGG = "addScanResult("+result.getDevice().getAddress()+"): ";
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();
            int deviceRssi = result.getRssi();

            Log.v(TAG, TAGG+TAGGG+deviceRssi+"db device found: \""+deviceName+"\" ("+device.toString()+").");

            // If we have an associated light controller defined, and this device matches it, use it and disregard any other
            if (definedLightControllerMAC != null && definedLightControllerMAC.equalsIgnoreCase(device.getAddress())) {
                Log.i(TAG, TAGG+TAGGG+"Found device that we're defined to be associated with. Using it!");
                notYetFoundOurAssociatedDevice = false;
                bluetoothDevice = device;
                return;
            }

            // If we got to here, then the found device is not (yet?) a defined one, so we just keep
            // looking at whatever device the scan gives us and saving aside the strongest one.
            // *** NOTE: This will keep occurring until the scan is stopped!
            if (notYetFoundOurAssociatedDevice) {
                // Disregard obviously weak/far devices
                if (deviceRssi < -65) {
                    Log.v(TAG, TAGG + TAGGG + "Device is weaker than expected. Likely not a local device. Skipping.");
                    return;
                }

                // If this device is the strongest we've found yet (and thus more likely to be nearest), remember it.
                // At the end of the callbacks, we should have the strongest/nearest device ready to go.
                if (deviceRssi > scannedStrongestRssi) {
                    bluetoothDevice = device;
                    scannedStrongestRssi = deviceRssi;
                }
            }
        }
    }


    /*============================================================================================*/
    /* Notification Methods */

    /** Setup and return a Notification Builder object.
     * Remember: After getting what this returns, you'll need to supply to NotificationManager to actually show it. */
    private NotificationCompat.Builder createNotificationBuilderObject(String contentText) {
        final String TAGG = "createNotificationBuilderObject: ";
        Log.v(TAG, TAGG+"Invoked.");

        NotificationCompat.Builder notifBuilder = null;
        final String notifTitle = getApplicationContext().getResources().getString(R.string.notification_title)+" v"+getAppVersion();

        try {
            notifBuilder = new NotificationCompat.Builder(getApplicationContext());
            notifBuilder.setSmallIcon(R.drawable.ic_stat_messagenet_logo_200x200_trans);
            notifBuilder.setContentTitle(notifTitle);
            notifBuilder.setContentText(contentText);
            notifBuilder.setOngoing(true);
            notifBuilder.setPriority(NotificationCompat.PRIORITY_MAX);
            notifBuilder.addAction(0, "Lights Standby", turnLightsStandbyPendingIntent);
            notifBuilder.setStyle(new NotificationCompat.BigTextStyle());

            // The following is just so we can save and retrieve notification text (in case we want to append later)
            Bundle bundle = new Bundle();
            bundle.putString(NotificationCompat.EXTRA_TEXT, contentText);
            notifBuilder.setExtras(bundle);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(notifBuilder));
        return notifBuilder;
    }

    /** Update the specified notification object's text.
     * DEV-NOTE: Originally developed with idea to update existing notifcation, but never got it to work. Not using for now, but keeping around just in case. */
    private NotificationCompat.Builder updateNotificationBuilderObjectText(NotificationCompat.Builder notifBuilder, String contentText, int updateMethod) {
        final String TAGG = "updateNotificationBuilderObjectText: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            synchronized (notifBuilder) {
                switch (updateMethod) {
                    case NOTIF_REPLACE:
                        notifBuilder.setContentText(contentText);
                        break;
                    case NOTIF_APPEND:
                        String existingText = notifBuilder.getExtras().getString(NotificationCompat.EXTRA_TEXT, "");
                        notifBuilder.setContentText(existingText+"\n"+contentText);
                }
                notifBuilder.notify();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        return notifBuilder;
    }

    /** Finalize and show the provided notification. */
    public void showNotif(NotificationCompat.Builder notifBuilder) {
        final String TAGG = "showNotif: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());
            mNotification = notifBuilder.build();
            //notificationManager.notify(mNotifID, notifBuilder.build());
            notificationManager.notify(mNotifID, mNotification);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Replace the notification with specified text. */
    public void replaceNotificationWithText(String text) {
        final String TAGG = "replaceNotificationWithText: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            mNotifBuilder = createNotificationBuilderObject(text);
            showNotif(mNotifBuilder);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Replace the notification with specified light status. */
    public void replaceNotificationWithLightStatus(Byte flasherLightCode, String textAfterLightMode, boolean verified) {
        final String TAGG = "replaceNotificationWithLightStatus: ";
        FL.v(TAG, TAGG+"Invoked.");

        final String verifiedText_false = " processing...";
        final String verifiedText_true = " in effect";
        String verifiedTextToUse;
        String humanReadableCode;

        try {
            if (verified) verifiedTextToUse = verifiedText_true;
            else verifiedTextToUse = verifiedText_false;

            humanReadableCode = flasherLightOmniCommandCodes.codeToEnglish(flasherLightCode);

            if (textAfterLightMode == null) {
                replaceNotificationWithText(humanReadableCode + verifiedTextToUse);
            } else {
                replaceNotificationWithText(humanReadableCode + textAfterLightMode + verifiedTextToUse);
            }
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }
    }
    public void replaceNotificationWithLightStatus(Byte flasherLightCode, boolean verified) {
        replaceNotificationWithLightStatus(flasherLightCode, null, verified);
    }

    /** Return the current notification text. */
    public String getCurrentNotificationText() {
        final String TAGG = "getCurrentNotificationText: ";
        Log.v(TAG, TAGG + "Invoked.");
        String ret = "";

        try {
            ret = mNotifBuilder.getExtras().getString(NotificationCompat.EXTRA_TEXT, "");
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        Log.v(TAG, TAGG + "Returning...\n"+ret);
        return ret;
    }

    /** Add the specified text as a new line in the existing notification. */
    public void appendNotificationWithText(String text) {
        final String TAGG = "appendNotificationWithText: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            text = getCurrentNotificationText() +
                    "\n" +
                    text;
            replaceNotificationWithText(text);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Replace only the specified portion of the notification text. */
    public void replaceNotificationTextWithText(String textToReplace, String textToReplaceWith) {
        final String TAGG = "replaceNotificationTextWithText: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            // Get current notification text
            String currentNotificationText = getCurrentNotificationText();

            // Find textToReplace in current notification text
            String newNotificationText = currentNotificationText.replace(textToReplace, textToReplaceWith);

            // Replace it
            replaceNotificationWithText(newNotificationText);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Replace only the specified portion (found via regular expression) of the notification text. */
    public void replaceNotificationRegexWithText(String textToReplaceRegex, String textToReplaceWith) {
        final String TAGG = "replaceNotificationRegexWithText: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            // Get current notification text
            String currentNotificationText = getCurrentNotificationText();

            // Find textToReplace in current notification text
            String newNotificationText = currentNotificationText.replaceFirst(textToReplaceRegex, textToReplaceWith);

            // Replace it
            replaceNotificationWithText(newNotificationText);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Remove only the specified portion of the notification text. */
    public void removeNotificationText(String textToRemove) {
        final String TAGG = "removeNotificationText: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            String currentNotificationText, newNotificationText, stringAfterMatch;
            int startPos, textToRemoveLength;

            // Get current notification text
            currentNotificationText = getCurrentNotificationText();

            // Find textToReplace in current notification text
            startPos = currentNotificationText.indexOf(textToRemove);
            textToRemoveLength = textToRemove.length();
            stringAfterMatch = currentNotificationText.substring(startPos+textToRemoveLength);
            Log.e(TAG, TAGG+"String after match: \""+stringAfterMatch+"\"");
            if (stringAfterMatch.substring(0,2).contains(System.getProperty("line.separator"))) {
                newNotificationText = currentNotificationText.replace(textToRemove+"\n", "");
            } else {
                newNotificationText = currentNotificationText.replace(textToRemove, "");
            }

            // Replace it
            replaceNotificationWithText(newNotificationText);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }

    /** Remove only the specified portion (found via regular expression) of the notification text. */
    public void removeNotificationRegex(String textToRemoveRegex) {
        final String TAGG = "removeNotificationRegex: ";
        Log.v(TAG, TAGG + "Invoked.");

        try {
            String currentNotificationText, newNotificationText, stringAfterMatch;
            int startPos, textToRemoveLength;

            // Get current notification text
            currentNotificationText = getCurrentNotificationText();

            // Find textToReplace in current notification text
            startPos = currentNotificationText.indexOf(textToRemoveRegex);
            textToRemoveLength = textToRemoveRegex.length();
            stringAfterMatch = currentNotificationText.substring(startPos+textToRemoveLength);
            Log.e(TAG, TAGG+"String after match: \""+stringAfterMatch+"\"");
            if (stringAfterMatch.substring(0,2).contains(System.getProperty("line.separator"))) {
                newNotificationText = currentNotificationText.replaceFirst(textToRemoveRegex+"\n", "");
            } else {
                newNotificationText = currentNotificationText.replaceFirst(textToRemoveRegex, "");
            }

            // Replace it
            replaceNotificationWithText(newNotificationText);
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Getter & Setter methods */

    public String getAppPackageName() {
        final String TAGG = "getAppPackageName: ";
        final String ret = this.appPackageName;
        FL.d(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    public static String getAppPackageNameStatic() {
        return appPackageNameStatic;
    }
    public void setAppPackageName(String packageName) {
        final String TAGG = "setAppPackageName: ";
        FL.d(TAG, TAGG+"Setting \""+String.valueOf(packageName)+"\"...");
        this.appPackageName = packageName;
        appPackageNameStatic = packageName;
    }

    public String getAppVersion() {
        final String TAGG = "getAppVersion: ";
        final String ret = this.appVersion;
        FL.d(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    public void setAppVersion(String appVersion) {
        final String TAGG = "setAppVersion: ";
        FL.d(TAG, TAGG+"Setting \""+String.valueOf(appVersion)+"\"...");
        this.appVersion = appVersion;
    }

    public Date getAppStartedDate() {
        final String TAGG = "getAppStartedDate: ";
        final Date ret = this.appStartedDate;
        FL.d(TAG, TAGG+"Returning \""+String.valueOf(ret)+"\".");
        return ret;
    }
    public long getAppRunningHours() {
        final String TAGG = "getAppRunningHours: ";
        long ret = 0;
        try {
            long appStartTime = getAppStartedDate().getTime();
            long currentTime = new Date().getTime();
            long diffMS = currentTime - appStartTime;
            long diffHrs = diffMS / (60 * 60 * 1000);
            ret = diffHrs;
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught calculating app's runtime in hours: "+e.getMessage());
        }
        FL.d(TAG, TAGG+"Returning \""+Long.toString(ret)+"\".");
        return ret;
    }
    public long getAppRunningMinutes() {
        final String TAGG = "getAppRunningMinutes: ";
        long ret = 0;
        try {
            long appStartTime = getAppStartedDate().getTime();
            long currentTime = new Date().getTime();
            long diffMS = currentTime - appStartTime;
            long diffMins = diffMS / (60 * 1000);
            ret = diffMins;
        } catch (Exception e) {
            FL.e(TAG, TAGG+"Exception caught calculating app's runtime in hours: "+e.getMessage());
        }
        FL.d(TAG, TAGG+"Returning \""+Long.toString(ret)+"\".");
        return ret;
    }
    public void setAppStartedDate(Date date) {
        final String TAGG = "setAppStartedDate: ";
        FL.d(TAG, TAGG+"Setting "+String.valueOf(date)+"...");
        this.appStartedDate = date;
    }

    public boolean getAllowAppToDie() {
        final String TAGG = "getAllowAppToDie: ";
        final boolean ret = this.allowAppToDie;
        FL.d(TAG, TAGG+"Returning "+String.valueOf(ret)+".");
        return ret;
    }
    public void setAllowAppToDie(boolean allowAppToDie) {
        final String TAGG = "setAllowAppToDie: ";
        FL.d(TAG, TAGG+"Setting "+String.valueOf(allowAppToDie)+"...");
        this.allowAppToDie = allowAppToDie;
    }

    public Intent getMainServiceIntent() {
        return mainServiceIntent;
    }
    public void setMainServiceIntent(Intent mainServiceIntent) {
        this.mainServiceIntent = mainServiceIntent;
    }

    public BluetoothFlasherLightsService getMainService() {
        return mainService;
    }
    public void setMainService(BluetoothFlasherLightsService mainService) {
        this.mainService = mainService;
    }

    public int getmNotifID() {
        return mNotifID;
    }

    public void setmNotifID(int mNotifID) {
        this.mNotifID = mNotifID;
    }

    public Notification getmNotification() {
        return mNotification;
    }

    public void setmNotification(Notification mNotification) {
        this.mNotification = mNotification;
    }


}
