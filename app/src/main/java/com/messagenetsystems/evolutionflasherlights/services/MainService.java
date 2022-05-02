package com.messagenetsystems.evolutionflasherlights.services;

/* MainService Class
 * A persistent service that hosts persistent processes and hooks.
 *  -
 *
 * Revisions:
 *  2020.05.29      Chris Rider     Created - used v2 main app's MainService as template. (effort to improve the app and bring it up to speed with Omni v2)
 *  2020.06.01      Chris Rider     Added BluetoothService (a copy of the original BluetoothFlasherLightsService which was originally started from StartupActivity).
 *  2020.06.23      Chris Rider     Migrated globals to MainApplication.
 *  2020.06.25      Chris Rider     Refactored whole app to shift work from BluetoothService to MainApplication.
 *  2020.06.28      Chris Rider     Ability to monitor and restart LightStateManager thread if needed.
 *  2020.07.01      Chris Rider     Added flag for when service is started and probably ready (initially just used by StartupActivity for status text on screen).
 *  2020.07.04      Chris Rider     Added HealthMonitorThread.
 */

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.Constants;
import com.messagenetsystems.evolutionflasherlights.models.FlasherLights;
import com.messagenetsystems.evolutionflasherlights.MainApplication;
import com.messagenetsystems.evolutionflasherlights.receivers.CommandReceiver;
import com.messagenetsystems.evolutionflasherlights.receivers.MainAppDataReceiver;
import com.messagenetsystems.evolutionflasherlights.threads.HealthMonitorThread;
import com.messagenetsystems.evolutionflasherlights.threads.LightStateManagerThread;

import java.lang.ref.WeakReference;

public class MainService extends Service {
    private static final String TAG = MainService.class.getSimpleName();

    // Constants...

    // Globals...
    public static boolean isServiceReady = false;

    // Local stuff...
    private WeakReference<Context> appContextRef;                                                   //since this thread is very long running, we prefer a weak context reference
    private MainApplication mainApplication;
    private long appPID;

    // Receivers...
    private CommandReceiver commandReceiver;
    private IntentFilter commandReceiverIntentFilter;
    private MainAppDataReceiver mainAppDataReceiver;
    private IntentFilter mainAppDataReceiverIntentFilter;

    // Threads & Services...
    private MonitorChildProcesses monitorChildProcesses;
    private LightStateManagerThread lightStateManagerThread;
    public volatile boolean isThreadAlive_lightStateManager;
    private HealthMonitorThread healthMonitorThread;
    public volatile boolean isThreadAlive_healthMonitorThread;

    // Handlers...

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;


    /** Constructor */
    public MainService(Context appContext) {
        super();
    }
    public MainService() {
    }


    /*============================================================================================*/
    /* Service methods */

    /** Service onCreate handler **/
    @Override
    public void onCreate() {
        super.onCreate();
        final String TAGG = "onCreate: ";
        logV(TAGG + "Invoked.");

        this.appContextRef = new WeakReference<Context>(getApplicationContext());
        this.logMethod = Constants.LOG_METHOD_FILELOGGER;
        this.appPID = 0;

        try {
            this.mainApplication = ((MainApplication) getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught instantiating " + TAG + ": " + e.getMessage());
            return;
        }

        // Initialize our receiver stuff
        this.commandReceiver = new CommandReceiver(getApplicationContext(), logMethod);
        this.commandReceiverIntentFilter = new IntentFilter();
        this.commandReceiverIntentFilter.addAction(FlasherLights.Intents.Actions.DO_LIGHT_COMMAND);
        this.commandReceiverIntentFilter.addAction(FlasherLights.Intents.Actions.DO_LIGHT_COMMAND_LEGACY);
        this.commandReceiverIntentFilter.addAction(FlasherLights.Intents.Actions.ASSOCIATE_NEAREST_LIGHT_CONTROLLER);
        this.commandReceiverIntentFilter.addAction(FlasherLights.Intents.Actions.POPULATE_LIGHT_CONTROLLER_ASSOCIATION_FILE);

        this.mainAppDataReceiver = new MainAppDataReceiver(getApplicationContext(), logMethod);
        this.mainAppDataReceiverIntentFilter = new IntentFilter();
        this.mainAppDataReceiverIntentFilter.addAction(Constants.Intents.Actions.UPDATE_NUMBER_DELIVERING_MSGS);
        this.mainAppDataReceiverIntentFilter.addAction(Constants.Intents.Actions.REGISTER_MAIN_APP_HEARTBEAT);

        // Initialize our monitoring process
        this.monitorChildProcesses = new MonitorChildProcesses();

        // Prepare all our processes and threads
        this.lightStateManagerThread = new LightStateManagerThread(getApplicationContext(), logMethod);
        this.healthMonitorThread = new HealthMonitorThread(getApplicationContext(), logMethod);
    }

    /** Service onBind handler
     * This invokes when you call bindService().
     * Returns an IBinder object that defines the programming interface that clients can use to interact with this service.
     *
     * When a client binds to this service by calling bindService(), it must provide an implementation of ServiceConnection (which monitors the connection with this service.
     * The return value of bindService() indicates whether the requested service exists and whether the client is permitted access to it.
     * When Android creates the connection, it calls onServiceConnected() on the ServiceConnection. That method includes an IBinder arg, which the client then uses to communicate with the bound service.
     **/
    @Override
    public IBinder onBind(Intent intent) {
        //throw new UnsupportedOperationException("Not yet implemented");
        //return binder;
        return null;
    }

    /** Service onStart handler
     * This invokes when you call startService(). **/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        final String TAGG = "onStartCommand: ";
        logV(TAGG + "Invoked.");

        // Begin startup...
        mainApplication.replaceNotificationWithText("Starting MainService");
        startForeground(mainApplication.getmNotifID(), mainApplication.getmNotification());
        try {
            appPID = (long) android.os.Process.myPid();
        } catch (Exception e) {
            logE(TAGG + "Exception caught trying to get app process ID: " + e.getMessage());
        }


        ////////////////////////////////////////////////////////////////////////////////////////////
        // Register receivers...

        // Light command receiver
        registerReceiver(commandReceiver, commandReceiverIntentFilter);

        // Message data & delivery status information receiver
        registerReceiver(mainAppDataReceiver, mainAppDataReceiverIntentFilter);


        ////////////////////////////////////////////////////////////////////////////////////////////
        // DEV-NOTE: Services...
        // Remember that any Service you start here (startService) will exist in the same thread as MainService!
        // So, if you want to avoid that, you should wrap them in new-Thread (with nested new-Runnable to easily catch issues upon compile rather than runtime)

        // Bluetooth/GATT service
        //new Thread(new Runnable() {
        //    @Override
        //    public void run() {
                //startService(bluetoothServiceIntent);
        //        startService(bluetoothServiceIntent);
        //    }
        //}).start();


        ////////////////////////////////////////////////////////////////////////////////////////////
        // Start any other threads
        // NOTE: For Thread classes, you should use .start() to ensure each thread executes on its own spawned thread.
        //       If you use .run(), the thread will run on this MainService thread, rather than on its own new thread.
        this.lightStateManagerThread.start();
        this.healthMonitorThread.start();

        // Start our child-monitoring process
        this.monitorChildProcesses.start();


        // Finish startup...
        isServiceReady = true;
        logI(TAGG+"MainService started.");
        mainApplication.replaceNotificationWithText("MainService started (App PID "+Long.toString(appPID)+").");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        // This gets invoked when the app is killed either by Android or the user.
        // To absolutely ensure it gets invoked, it's best-practice to call stopService somewhere if you can.
        final String TAGG = "onDestroy: ";
        logV(TAGG+"Invoked.");

        // Update notification
        mainApplication.replaceNotificationWithText("MainService destroyed!");

        // Unregister any receivers
        unregisterReceiver(commandReceiver);
        this.commandReceiver.cleanup();
        this.commandReceiver = null;

        unregisterReceiver(mainAppDataReceiver);
        this.mainAppDataReceiver.cleanup();
        this.mainAppDataReceiver = null;

        // Stop any services (you should take care of implicit cleanup in the Service class' onDestroy method)

        // Stop and cleanup any threads
        monitorChildProcesses.cleanup();
        lightStateManagerThread.cleanup();
        healthMonitorThread.cleanup();

        // Send our broadcast that we're about to die
        //Intent broadcastIntent = new Intent(this, MainServiceStoppedReceiver.class);
        //sendBroadcast(broadcastIntent);

        // Explicitly release variables (not strictly necessary, but can't hurt to force garbage collection)
        this.monitorChildProcesses = null;
        this.lightStateManagerThread = null;
        healthMonitorThread = null;
        this.mainApplication = null;
        this.commandReceiverIntentFilter = null;
        //this.bluetoothFlasherLightsServiceIntent = null;

        // Clear up anything else
        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }

        super.onDestroy();
    }


    private static final int doLightCommandWaitSeconds = 6;      //NOTE: +- 1 second
    public static void doLightCommand_legacy(final Context appContext, final String legacyLightCmd, final int numToRepeatCmd) {
        final String TAGG = "doLightCommand_legacy(\""+legacyLightCmd+"\","+String.valueOf(numToRepeatCmd)+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        Log.d(TAG, TAGG+"Requesting light action.");
        BluetoothFlasherLightsService.requestLightAction(
                appContext,
                legacyLightCmd,
                BluetoothFlasherLightsService.LightCommandBroadcastReceiver.CMD_LIGHTS_FORCE_SEND_YES,
                BluetoothFlasherLightsService.LightCommandBroadcastReceiver.CMD_LIGHTS_DO_PREVENT_FURTHER_COMMANDS_NO);

        if (numToRepeatCmd > 0) {
            Log.v(TAG, TAGG+"Repeating command (numToRepeatCmd="+numToRepeatCmd+")");

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doLightCommand_legacy(appContext, legacyLightCmd, numToRepeatCmd-1);
                }
            }, (doLightCommandWaitSeconds-1) * 1000);
        } else {
            /*
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
            */
        }
    }


    /*============================================================================================*/
    /* Utilities */

    private void restartThread_lightStateManager() {
        final String TAGG = "restartThread_lightStateManager: ";
        logV(TAGG+"Trying to restart LightStateManager...");

        int maxWaitForStart = 10;

        try {
            if (this.lightStateManagerThread != null) {
                this.lightStateManagerThread.cleanup();
            }

            this.lightStateManagerThread = new LightStateManagerThread(appContextRef.get(), logMethod);
            this.lightStateManagerThread.start();

            while (!this.lightStateManagerThread.isThreadRunning()) {
                //wait here while thread starts up
                logV(TAGG+"Waiting for thread to start.");

                maxWaitForStart--;
                if (maxWaitForStart < 0) {
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }

    private void restartThread_healthMonitorThread() {
        final String TAGG = "restartThread_healthMonitorThread: ";
        logV(TAGG+"Trying to restart HealthMonitorThread...");

        int maxWaitForStart = 10;

        try {
            if (this.healthMonitorThread != null) {
                this.healthMonitorThread.cleanup();
            }

            this.healthMonitorThread = new HealthMonitorThread(appContextRef.get(), logMethod);
            this.healthMonitorThread.start();

            while (!this.healthMonitorThread.isThreadRunning()) {
                //wait here while thread starts up
                logV(TAGG+"Waiting for thread to start.");

                maxWaitForStart--;
                if (maxWaitForStart < 0) {
                    break;
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Thread to monitor child processes, and restart them if necessary. */
    private class MonitorChildProcesses extends Thread {
        private final String TAGG = MonitorChildProcesses.class.getSimpleName()+": ";

        private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
        private volatile boolean isThreadRunning;           //just a status flag
        private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

        private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
        private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

        private long loopIterationCounter;

        /** Constructor */
        public MonitorChildProcesses() {
            // Initialize values
            this.isStopRequested = false;
            this.isThreadRunning = false;
            this.pauseProcessing = false;
            this.activeProcessingSleepDuration = 5000;
            this.pausedProcessingSleepDuration = 10000;
            this.loopIterationCounter = 1;
        }

        /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .start() or .execute() method call.
         * Remember that .start() implicitly spawns a thread and calls .execute() to invoke this run() method.
         * If you directly call .run(), this run() method will invoke on the same thread you call it from. */
        @Override
        public void run() {
            final String TAGG = this.TAGG+"run: ";
            logV(TAGG + "Invoked.");

            // As long as our thread is supposed to be running...
            while (!Thread.currentThread().isInterrupted()) {

                // Our thread has started or is still running
                isThreadRunning = true;

                // Either do nothing (if paused) or allow work to happen (if not paused)...
                logV(TAGG + "-------- Iteration #" + loopIterationCounter + " ------------------------");
                if (pauseProcessing) {
                    doSleepPaused();
                    logD(TAGG + "Processing is paused. Thread continuing to run, but no work is occurring.");
                } else {
                    // Do a short delay to help prevent the thread loop from eating cycles
                    doSleepActive();

                    try {
                        ////////////////////////////////////////////////////////////////////////////////
                        // DO THE BULK OF THE ACTUAL WORK HERE...

                        if (!lightStateManagerThread.isAlive()) {
                            isThreadAlive_lightStateManager = false;
                            logW(TAGG+"LightStateManager is not alive! Restarting it...");
                            restartThread_lightStateManager();
                        } else {
                            isThreadAlive_lightStateManager = true;
                        }

                        if (!healthMonitorThread.isAlive()) {
                            isThreadAlive_healthMonitorThread = false;
                            logW(TAGG+"HealthMonitorThread is not alive! Restarting it...");
                            restartThread_healthMonitorThread();
                        } else {
                            isThreadAlive_healthMonitorThread = true;
                        }

                        // END THE BULK OF THE ACTUAL WORK HERE...
                        ////////////////////////////////////////////////////////////////////////////////
                    } catch (Exception e) {
                        logE(TAGG+"Exception caught: "+e.getMessage());
                    }
                }

                doCounterIncrement();

                // this is the end of the loop-iteration, so check whether we will stop or continue
                if (doCheckWhetherNeedToStop()) {
                    isThreadRunning = false;
                    break;
                }
            }//end while
        }//end run()

        private void doSleepPaused() {
            final String TAGG = this.TAGG+"doSleepPaused: ";

            try {
                Thread.sleep(pausedProcessingSleepDuration);
            } catch (InterruptedException e) {
                logW(TAGG + "Exception caught trying to sleep during pause: " + e.getMessage());
            }
        }

        private void doSleepActive() {
            final String TAGG = this.TAGG+"doSleepActive: ";

            try {
                Thread.sleep(activeProcessingSleepDuration);
            } catch (InterruptedException e) {
                logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
            }
        }

        private void doCounterIncrement() {
            final String TAGG = this.TAGG+"doCounterIncrement: ";

            try {
                loopIterationCounter++;
            } catch (Exception e) {
                logW(TAGG+"Exception caught incrementing loop counter. Resetting to 1: "+e.getMessage());
                loopIterationCounter = 1;
            }
        }

        private boolean doCheckWhetherNeedToStop() {
            final String TAGG = this.TAGG+"doCheckWhetherNeedToStop: ";
            boolean ret = false;

            try {
                if (Thread.currentThread().isInterrupted()) {
                    logI(TAGG + "Thread will now stop.");
                    isThreadRunning = false;
                }
                if (isStopRequested) {
                    logI(TAGG + "Thread has been requested to stop and will now do so.");
                    isThreadRunning = false;
                    ret = true;
                }
            } catch (Exception e) {
                logE(TAGG+"Exception caught: "+e.getMessage());
            }

            return ret;
        }

        /** Call this to terminate the loop and release resources. */
        public void cleanup() {
            final String TAGG = "cleanup: ";

            try {
                this.isStopRequested = true;

                // Note: At this point, the thread-loop should break on its own
            } catch (Exception e) {
                logE(TAGG+"Exception caught calling stopListening(): "+e.getMessage());
            }
        }
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
