package com.messagenetsystems.evolutionflasherlights.activities;

/* StartupActivity
 *
 * Revisions:
 *  2020.06.12      Chris Rider     Added flashing option.
 *  2020.06.25      Chris Rider     Refactored whole app to shift work from BluetoothService to MainApplication.
 *  2020.06.30      Chris Rider     Improved runtime permissions approval workflow.
 *                                  Added feature to scan for nearest device and update MAC in provisioning file, when associating.
 *  2020.07.01      Chris Rider     Improvements to testing flows and trying to make more responsive, additional status texts, fixed MAC not showing on initial load, etc.
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.MainApplication;
import com.messagenetsystems.evolutionflasherlights.R;
import com.messagenetsystems.evolutionflasherlights.devices.BluetoothLightController_HY254117V9;
import com.messagenetsystems.evolutionflasherlights.services.BluetoothFlasherLightsService;
import com.messagenetsystems.evolutionflasherlights.services.MainService;
import com.messagenetsystems.evolutionflasherlights.utilities.SettingsUtils;

import java.util.ArrayList;
import java.util.List;

public class StartupActivity extends Activity {
    final String TAG = "StartupActivity";

    private Context appContext;
    private MainApplication mainApplication;
    public static volatile boolean activityActive = false;
    public static volatile String lightControllerMacAddress = null;

    private boolean needToAcceptPerms = true;

    int secondsToAutoFinish = 5;
    private boolean allowAutoFinish = true;
    int secondsToWaitOnServiceStart = 8;
    int doLightCommandWaitSeconds = 6;      //NOTE: +- 1 second

    EditText etMAC;
    TextView tvStatus;
    Button btnCancelAutoFinish;
    Button btnInitLights;
    Button btnAssocLights;
    Button btnTestRed;
    Button btnTestGreen;
    Button btnTestBlue;
    Button btnTestWhite;
    Button btnTurnOnStandby;
    Button btnTurnOff;
    CheckBox checkBoxDoFlashing;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_startup);

        final String TAGG = "onCreate: ";
        Log.v(TAG, TAGG+"Invoked.");

        appContext = getApplicationContext();

        //TODO: TEMP TESTING
        startActivity(new Intent(this, TestActivity.class));
        finish();

        try {
            this.mainApplication = ((MainApplication) getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught instantiating "+TAG+": "+e.getMessage());
            return;
        }

        // Check permissions
        // Note: You only need a few, for example write also gives you read, fine also gives you coarse, etc.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            String[] permissions = {
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            ActivityCompat.requestPermissions(this, permissions, 304);  //304 is just an arbitrary number
        } else {
            Log.d(TAG, "All permissions have been granted.");
            needToAcceptPerms = false;
        }

        activityActive = true;

        // Setup views
        etMAC = (EditText) findViewById(R.id.editText_MAC);

        tvStatus = (TextView) findViewById(R.id.textView_status);
        tvStatus.setText("Loading...");
        tvStatus.setVisibility(View.VISIBLE);

        checkBoxDoFlashing = (CheckBox) findViewById(R.id.checkbox_doFlashing);

        btnCancelAutoFinish = (Button) findViewById(R.id.button_cancelAutoFinish);
        btnCancelAutoFinish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                allowAutoFinish = false;
                btnCancelAutoFinish.setEnabled(false);
                if (MainService.isServiceReady) {
                    tvStatus.setText("Services Ready");
                    etMAC.setEnabled(true);
                    enableButtons();
                } else {
                    tvStatus.setText("Waiting For Service To Start...");
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            while (!MainService.isServiceReady) {
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    FL.w(TAGG+"Exception caught sleeping: "+e.getMessage());
                                }
                            }
                            tvStatus.setText("Services Ready");
                            etMAC.setEnabled(true);
                            enableButtons();
                        }
                    }, 1000);
                }
            }
        });
        btnInitLights = (Button) findViewById(R.id.button_initLights);
        btnInitLights.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doLightCommand(appContext.getResources().getString(R.string.SIGNALLIGHT_CMD_STANDBY), 3, "Lights Should be Safe to Connect");
            }
        });
        btnAssocLights = (Button) findViewById(R.id.button_associateLights);
        btnAssocLights.setOnClickListener(new View.OnClickListener() {
            final String TAGG = "btnAssocLights.OnClickListener: ";
            @Override
            public void onClick(View v) {
                if (doAssociation()) {
                    Log.v(TAG, TAGG+"Association all succeeded.");
                } else {
                    Log.w(TAG, TAGG+"Association some/all failed.");
                }
            }
        });
        btnTestRed = (Button) findViewById(R.id.button_testRed);
        btnTestRed.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnTestRed.isEnabled()) {
                    if (checkBoxDoFlashing.isChecked())
                        executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_RED);
                    else
                        executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_RED_BRI);
                }
            }
        });
        btnTestGreen = (Button) findViewById(R.id.button_testGreen);
        btnTestGreen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnTestGreen.isEnabled()) {
                    if (checkBoxDoFlashing.isChecked())
                        executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_GREEN);
                    else
                        executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_GREEN_BRI);
                }
            }
        });
        btnTestBlue = (Button) findViewById(R.id.button_testBlue);
        btnTestBlue.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnTestBlue.isEnabled()) {
                    if (checkBoxDoFlashing.isChecked())
                        executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_BLUE);
                    else
                        executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_BLUE_BRI);
                }
            }
        });
        btnTestWhite = (Button) findViewById(R.id.button_testWhite);
        btnTestWhite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnTestWhite.isEnabled()) {
                    if (checkBoxDoFlashing.isChecked())
                        executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITEPURE);
                    else
                        executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEPURE_BRI);
                }
            }
        });
        btnTurnOnStandby = (Button) findViewById(R.id.button_turnStandby);
        btnTurnOnStandby.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnTurnOnStandby.isEnabled())
                    executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_STANDBY);
            }
        });
        btnTurnOff = (Button) findViewById(R.id.button_turnOff);
        btnTurnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btnTurnOff.isEnabled())
                    executeButtonLightCommand(MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_OFF);
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        final String TAGG = "onPostCreate: ";
        Log.v(TAG, TAGG+"Invoked.");

        // Disable buttons right away (will enable them after service is up and running)
        disableButtons();
        btnCancelAutoFinish.setEnabled(true);

        // Start our MainService
        startService(new Intent(this, MainService.class));

        // If no user interaction required or desired, make the startup activity go away
        if (needToAcceptPerms) {
            //don't auto-finish activity
            //don't auto-finish activity
            Log.v(TAG, TAGG+"Need to accept runtime permissions, so won't auto-finish activity.");
        } else {
            //setup auto-finish of activity after short time
            tvStatus.setText(String.valueOf(secondsToAutoFinish) + " Second Delay Before Auto Activity Finish...");
            tvStatus.setVisibility(View.VISIBLE);

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (allowAutoFinish) {
                        tvStatus.setVisibility(View.INVISIBLE);
                        doFinish();
                    }
                }
            }, secondsToAutoFinish*1000);
        }

        // Give time for service to get up and running, then enable buttons
        /*
        final Handler handler2 = new Handler();
        handler2.postDelayed(new Runnable() {
            @Override
            public void run() {

                tvStatus.setVisibility(View.INVISIBLE);
                etMAC.setText(String.valueOf(lightControllerMacAddress));
                etMAC.setEnabled(true);
                enableButtons();
            }
        }, secondsToWaitOnServiceStart*1000);
        */
    }

    /** The following pair of functions handle what happens when this activity is visible or not.
     * NOTE: This might happen when an overlay or some other element takes focus, even if activity is visible. */
    @Override
    public void onResume() {
        // This will also fire if singleInstance flag is set and you start the activity
        super.onResume();
        Log.v(TAG, "Running onResume (activity should now be visible and in-front)");

        etMAC.setText(String.valueOf(SettingsUtils.getProvFileFlasherLightControllerMacAddress()));

        if (allowAutoFinish) {
            btnCancelAutoFinish.setEnabled(true);
        } else {
            btnCancelAutoFinish.setEnabled(false);
        }
    }
    @Override
    public void onPause() {
        super.onPause();
        Log.v(TAG, "Running onPause (activity is no longer visible or in-front)");

        //let's not let it be complex, just finish the activity (service should remain running)
        if (!this.isFinishing())
            doFinish();
    }

    private void executeButtonLightCommand(final byte command) {
        final String TAGG = "executeButtonLightCommand: ";

        //disable buttons
        disableButtons();

        //dispatch command
        if (mainApplication.executeLightCommand(command, Integer.MAX_VALUE, null, false)) {
            tvStatus.setText("Sending command to light device, please wait...");
            //run loop-checker for re-enabling buttons
            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    while (mainApplication.isBluetoothDeviceCommandUnderway) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            FL.w(TAGG + "Exception caught sleeping: " + e.getMessage());
                        }
                    }
                    tvStatus.setText(MainApplication.flasherLightOmniCommandCodes.codeToEnglish(command)+" Done.");
                    enableButtons();
                }
            });
        } else {
            tvStatus.setText("Light device not ready yet, try again!");
            enableButtons();
        }
    }

    void doFinish() {
        final String TAGG = "doFinish: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            activityActive = false;
            finish();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to finish the activity. Already finished? "+e.getMessage());
        }
    }

    /** Returns true if ALL of the calls are true */
    boolean doAssociation() {
        final String TAGG = "doAssociation: ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        boolean da1;
        boolean da2;

        startScanForDevices();
        try {
            Thread.sleep(2500);
        } catch (Exception e) {
            FL.e(TAGG+"Exception caught waiting for scan to complete: "+e.getMessage());
        }

        da1 = doAssociation_provFile();
        da2 = doAssociation_mainAppSharedPrefs();

        if (da1 && da2) {
            ret = true;
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    @SuppressLint("SetTextI18n")
    boolean doAssociation_provFile() {
        final String TAGG = "doAssociation: ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        tvStatus.setText("Associating Light Controller MAC Address...");
        tvStatus.setVisibility(View.VISIBLE);

        if (lightControllerMacAddress == null || lightControllerMacAddress.equalsIgnoreCase("null")) {
            tvStatus.setText("Error: No Light Controller MAC Address!");
            tvStatus.setVisibility(View.VISIBLE);
        } else {
            // Save to data file on sdcard for perpetuity
            if (SettingsUtils.saveLightControllerMacAddressToProvFile(lightControllerMacAddress)) {
                tvStatus.setText("Updated Provisioning File with \"" + lightControllerMacAddress + "\".");
                tvStatus.setVisibility(View.VISIBLE);
                ret = true;
            } else {
                Log.w(TAG, TAGG + "Update prov-file failed somehow.");
                tvStatus.setText("Couldn't Update MAC in Provisioning File");
                tvStatus.setVisibility(View.VISIBLE);
            }
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    @SuppressLint("SetTextI18n")
    boolean doAssociation_mainAppSharedPrefs() {
        final String TAGG = "doAssociation: ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        tvStatus.setText("Associating Light Controller MAC Address...");
        tvStatus.setVisibility(View.VISIBLE);

        if (lightControllerMacAddress == null || lightControllerMacAddress.equalsIgnoreCase("null")) {
            tvStatus.setText("Error: No Light Controller MAC Address!");
            tvStatus.setVisibility(View.VISIBLE);
        } else {
            // Associate with main app's shared-prefs file (note: this won't work if main app not installed or configured yet)
            if (SettingsUtils.setSharedPrefsFlasherLightControllerMacAddress(lightControllerMacAddress)) {
                tvStatus.setText("Associated \"" + lightControllerMacAddress + "\" with main app.");
                tvStatus.setVisibility(View.VISIBLE);
                etMAC.setText(lightControllerMacAddress);
                MainApplication.definedLightControllerMAC = lightControllerMacAddress;
                ret = true;
            } else {
                Log.w(TAG, TAGG + "Set shared prefs method failed somehow.");
                //tvStatus.setText("Error: Failed To Associate MAC In Main App Shared Prefs!");
                tvStatus.setText("Couldn't Associate MAC in Main-App Shared-Prefs");
                tvStatus.setVisibility(View.VISIBLE);
            }
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    @SuppressLint("SetTextI18n")
    void doLightCommand(final String lightCmd, final int numToRepeatCmd, final String postCmdStatusToShow) {
        final String TAGG = "doLightCommand(\""+lightCmd+"\","+String.valueOf(numToRepeatCmd)+",\""+String.valueOf(postCmdStatusToShow)+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        disableButtons();

        tvStatus.setText("Sending Command, Please Wait...");
        tvStatus.setVisibility(View.VISIBLE);

        Log.d(TAG, TAGG+"Requesting light action.");
        BluetoothFlasherLightsService.requestLightAction(appContext,
                lightCmd,
                BluetoothFlasherLightsService.LightCommandBroadcastReceiver.CMD_LIGHTS_FORCE_SEND_YES,
                BluetoothFlasherLightsService.LightCommandBroadcastReceiver.CMD_LIGHTS_DO_PREVENT_FURTHER_COMMANDS_NO);

        if (numToRepeatCmd > 0) {
            Log.v(TAG, TAGG+"Repeating command (numToRepeatCmd="+numToRepeatCmd+")");

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doLightCommand(lightCmd, numToRepeatCmd-1, postCmdStatusToShow);
                }
            }, (doLightCommandWaitSeconds-1) * 1000);
        } else {
            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (postCmdStatusToShow == null || postCmdStatusToShow.isEmpty()) {
                        tvStatus.setVisibility(View.INVISIBLE);
                    } else {
                        tvStatus.setText(postCmdStatusToShow);
                    }
                    enableButtons();
                }
            }, (doLightCommandWaitSeconds+1) * 1000);
        }
    }

    void enableButtons() {
        final String TAGG = "enableButtons: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            btnInitLights.setEnabled(true);
            btnAssocLights.setEnabled(true);
            btnTestRed.setEnabled(true);
            btnTestGreen.setEnabled(true);
            btnTestBlue.setEnabled(true);
            btnTestWhite.setEnabled(true);
            btnTurnOnStandby.setEnabled(true);
            btnTurnOff.setEnabled(true);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to enable buttons: "+e.getMessage());
        }
    }

    void disableButtons() {
        final String TAGG = "disableButtons: ";
        Log.v(TAG, TAGG+"Invoked.");

        try {
            btnInitLights.setEnabled(false);
            btnAssocLights.setEnabled(false);
            btnTestRed.setEnabled(false);
            btnTestGreen.setEnabled(false);
            btnTestBlue.setEnabled(false);
            btnTestWhite.setEnabled(false);
            btnTurnOnStandby.setEnabled(false);
            btnTurnOff.setEnabled(false);
        } catch (Exception e) {
            Log.w(TAG, TAGG+"Exception caught trying to disable buttons: "+e.getMessage());
        }
    }

    private boolean mScanning = false;
    private ScanCallback mScanCallback = null;
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothLeScanner mBluetoothLeScanner = null;
    private Handler mScanningHandler = null;
    private BluetoothDevice mBluetoothDevice = null;
    private int scannedStrongestRssi = Integer.MIN_VALUE;
    private void startScanForDevices() {
        final String TAGG = "startScanForDevices: ";

        // First, check if a scan is already happening
        if (mScanning) {
            FL.w(TAGG+TAGG + "A scan is already occurring. Aborting so as not to start another.");
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
                .setDeviceName(BluetoothLightController_HY254117V9.DEVICE_NAME)
                .build();
        filters.add(scanFilter);

        // Reinitialize an instance of our scan-callback (which saves strongest scan results to class-global vars)
        mScanCallback = new BtleScanCallback();

        // Init adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // Now grab hold of the BluetoothLeScanner to start the scan, and set our scanning boolean to true.
        if (mBluetoothAdapter != null) {
            mBluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();
            FL.d(TAGG+TAGG + "Scanning for " + Math.round(2000 / 1000) + " seconds...");
            mBluetoothLeScanner.startScan(filters, settings, mScanCallback);
            mScanning = true;
        } else {
            FL.e(TAGG+TAGG + "No mBluetoothAdapter object, cannot start scanning. Make sure it's instantiated first.");
            return;
        }

        // At this point, we have a Bluetooth scan that will save all ScanResults into a map (and/or class-global variables)
        // (that should have happened in the appropriate callback)

        // Since scanning will go forever on its own, setup a handler to stop it after some time (it doesn't need to be long)
        mScanningHandler = new Handler();
        mScanningHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                FL.d(TAGG+TAGG + "mScanningHandler Runnable executing to call stopScan.");
                stopScan();
            }
        }, 2000);
    }

    /**
     * Stop the scan, using the same ScanCallback we used earlier (just for simplicity? not sure why it's needed).
     * NOTE: This will fire after SCAN_PERIOD has elapsed, via a Handler.postDelayed call.
     * After that, we continue the sequence of events, by calling scanComplete.
     */
    private void stopScan() {
        final String TAGG = "stopScan: ";

        if (mScanning && mBluetoothAdapter != null && mBluetoothAdapter.isEnabled() && mBluetoothLeScanner != null) {
            FL.d(TAGG+TAGG + "Scanning will now be commanded to stop.");

            mBluetoothLeScanner.stopScan(mScanCallback);
            mScanning = false;

            try {
                FL.v(TAGG+TAGG + "Waiting a brief time for scan to stop.");
                Thread.sleep(100);
            } catch (InterruptedException e) {
                FL.w(TAGG+TAGG + "Exception caught waiting for scan to stop; should be alright, so continuing.");
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
            FL.i(TAGG+TAGG + "Device we will use is: " + mBluetoothDevice.getAddress() + " with signal strength of " + scannedStrongestRssi + ".");

            lightControllerMacAddress = mBluetoothDevice.getAddress();


            //logI(TAGG+TAGG+"Connecting GATT...");
            //connectGattDevice(mBluetoothDevice);

            // Check if it seems right (typically anything stronger than -50dbm
            if (scannedStrongestRssi < -68) {
                FL.w(TAGG+TAGG+"WARNING: light controller seems weak... is it the right one?");
            }

            // Send initial command to light hardware
            //initiateLightCommand(String.valueOf((char) MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_NONE));
        } else {
            FL.e(TAGG+TAGG + "Scanned device is null... perhaps it's connected to another device?");

            //stopSelf();

            //TODO: Try resetting bluetooth chipset? (manually turning on and off fixes this)
        }
    }

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
                    FL.e(TAGG+TAGG+TAGGG+"BLE scan failed with internal error!");
                    break;
                default:
                    FL.e(TAGG+TAGG+TAGGG+"BLE scan failed!");
                    break;
            }
        }

        private void addScanResult(ScanResult result) {
            final String TAGGG = "addScanResult("+result.getDevice().getAddress()+"): ";
            BluetoothDevice device = result.getDevice();
            String deviceName = device.getName();
            int deviceRssi = result.getRssi();
            String deviceMac = device.getAddress();

            FL.v(TAGG+TAGG+TAGGG+deviceRssi+"db device found: \""+deviceName+"\" ("+device.toString()+").");

            // If we have an associated light controller defined, and this device matches it, use it;
            if (lightControllerMacAddress != null
                    && deviceMac.equalsIgnoreCase(lightControllerMacAddress)) {
                FL.i(TAGG+TAGG+TAGGG+"Found device that we're defined to be associated with. Using it!");
                notYetFoundOurAssociatedDevice = false;
                mBluetoothDevice = device;
                scannedStrongestRssi = deviceRssi;
                return;
            }

            // (If we got to here, then the found device is not (yet?) a defined one)
            if (notYetFoundOurAssociatedDevice) {
                // Disregard obviously weak/far devices
                if (deviceRssi < -65) {
                    FL.v(TAGG+TAGG + TAGGG + "Device is weaker than expected. Likely not a local device. Skipping.");
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
}
