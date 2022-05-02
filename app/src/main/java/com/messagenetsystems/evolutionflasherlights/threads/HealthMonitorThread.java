package com.messagenetsystems.evolutionflasherlights.threads;

/* HealthMonitorThread
 * Subclass for thread to ensure that lights are behaving and healthy.
 *
 * Revisions:
 *  2020.07.04      Chris Rider     Created.
 */

import android.content.Context;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.Constants;
import com.messagenetsystems.evolutionflasherlights.MainApplication;
import com.messagenetsystems.evolutionflasherlights.services.MainService;
import com.messagenetsystems.evolutionflasherlights.utilities.DatetimeUtils;

import java.lang.ref.WeakReference;
import java.util.Date;


public class HealthMonitorThread extends Thread {
    private String TAG = HealthMonitorThread.class.getSimpleName();

    // Overhead...
    private WeakReference<Context> appContextRef;
    private MainApplication mainApplication;

    private volatile boolean isStopRequested;           //flag to set/check for the thread to interrupt itself
    private volatile boolean isThreadRunning;           //just a status flag
    private volatile boolean pauseProcessing;           //flag to set if you want to pause processing work (may double as a kind of "is paused" flag)

    private int activeProcessingSleepDuration;          //duration (in milliseconds) to sleep during active processing (to help ensure CPU cycles aren't eaten like crazy)
    private int pausedProcessingSleepDuration;          //duration (in milliseconds) to sleep during paused processing (to help ensure CPU cycles aren't eaten like crazy)

    private long loopIterationCounter;

    // Local stuff...
    private DatetimeUtils datetimeUtils;
    private Date nowDate;

    private long problemCount_status133_previous = 0;
    private long problemCount_serviceDiscovery_previous = 0;

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;


    /** Constructor */
    public HealthMonitorThread(Context appContext, int logMethod) {
        this.appContextRef = new WeakReference<Context>(appContext);
        this.logMethod = logMethod;
        try {
            this.mainApplication = ((MainApplication) appContext.getApplicationContext());
        } catch (Exception e) {
            logE("Exception caught instantiating MainApplication object: "+e.getMessage());
            return;
        }

        this.isStopRequested = false;
        this.isThreadRunning = false;
        this.pauseProcessing = false;
        this.activeProcessingSleepDuration = 1000;
        this.pausedProcessingSleepDuration = 10000;
        this.loopIterationCounter = 1;

        this.datetimeUtils = new DatetimeUtils(appContext, logMethod);

        this.nowDate = new Date();
    }

    /** Main runnable routine... executes once whenever the initialized thread is commanded to start running with .start() or .execute() method call.
     * Remember that .start() implicitly spawns a thread and calls .execute() to invoke this run() method.
     * If you directly call .run(), this run() method will invoke on the same thread you call it from. */
    @Override
    public void run() {
        final String TAGG = "run: ";
        logV(TAGG + "Invoked.");

        // Short pause at startup to make sure other resources initialize before we begin checks
        doSleepActive();

        // As long as our thread is supposed to be running...
        while (!Thread.currentThread().isInterrupted()) {

            // Our thread has started or is still running
            isThreadRunning = true;

            // Either do nothing (if paused) or allow work to happen (if not paused)...
            logV(TAGG + "-------- Iteration #" + loopIterationCounter + " ------------------------");
            if (pauseProcessing) {
                logD(TAGG + "Processing is paused. Thread continuing to run, but no work is occurring.");

                doSleepPaused();
            } else {

                try {
                    ////////////////////////////////////////////////////////////////////////////////
                    // DO THE BULK OF THE ACTUAL WORK HERE...

                    if (mainApplication.bluetoothProblemExists_needDeviceReset) {
                        //TODO: rescan bluetooth device

                    }

                    if (mainApplication.problemCount_status133 > problemCount_status133_previous) {
                        logW(TAGG+"Problem count for 133 status has increased from "+problemCount_status133_previous+" to "+mainApplication.problemCount_status133+".");
                        problemCount_status133_previous = mainApplication.problemCount_status133;
                    }

                    if (mainApplication.problemCount_serviceDiscovery > problemCount_serviceDiscovery_previous) {
                        logW(TAGG+"Problem count for service-discovery has increased from "+problemCount_serviceDiscovery_previous+" to "+mainApplication.problemCount_serviceDiscovery+".");
                        problemCount_serviceDiscovery_previous = mainApplication.problemCount_serviceDiscovery;
                    }

                    // END THE BULK OF THE ACTUAL WORK HERE...
                    ////////////////////////////////////////////////////////////////////////////////
                } catch (NullPointerException e) {
                    logW(TAGG+"Null pointer exception caught (this may be normal upon first startup): "+e.getMessage());
                } catch (Exception e) {
                    logE(TAGG+"Unexpected exception caught: "+e.getMessage());
                }

                // Do a short delay to help prevent the thread loop from eating cycles
                doSleepActive();
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
        final String TAGG = "doSleepPaused: ";

        try {
            Thread.sleep(pausedProcessingSleepDuration);
        } catch (InterruptedException e) {
            logW(TAGG + "Exception caught trying to sleep during pause: " + e.getMessage());
        }
    }

    private void doSleepActive() {
        final String TAGG = "doSleepActive: ";

        try {
            Thread.sleep(activeProcessingSleepDuration);
        } catch (InterruptedException e) {
            logW(TAGG + "Exception caught trying to sleep: " + e.getMessage());
        }
    }

    private void doCounterIncrement() {
        final String TAGG = "doCounterIncrement: ";

        try {
            loopIterationCounter++;
        } catch (Exception e) {
            logW(TAGG+"Exception caught incrementing loop counter. Resetting to 1: "+e.getMessage());
            loopIterationCounter = 1;
        }
    }

    private boolean doCheckWhetherNeedToStop() {
        final String TAGG = "doCheckWhetherNeedToStop: ";
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

        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }

        this.mainApplication = null;

        this.datetimeUtils = null;
    }


    /*============================================================================================*/
    /* Getter/Setter Methods */

    public boolean isThreadRunning() {
        return this.isThreadRunning;
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