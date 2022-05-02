package com.messagenetsystems.evolutionflasherlights.receivers;

/* MainAppDataReceiver
 * Broadcast receiver for main delivery-app's message data, delivery status updates, etc..
 * NOTE: Do not confuse this "MainApp" with the flasher light's MainApplication... this class receives stuff from the separate delivery app ("main" app).
 *
 * Revisions:
 *  2020.06.03      Chris Rider     Created.
 *  2020.06.20      Chris Rider     Renamed from DeliveryStatusReceiver, since we could make this more inclusive for such things.
 *  2020.06.23      Chris Rider     Brought in MainApplication instance, and migrated globals over to that.
 *  2020.06.28      Chris Rider     Refactored/renamed from MessageDataReceiver to MainAppDataReceiver, so we can include more things in this class' scope (initially for processing main delivery app's heartbeat).
 *                                  Also now updating last-communication-from-main-app global variable when we receive other broadcast from main app.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.Constants;
import com.messagenetsystems.evolutionflasherlights.MainApplication;

import java.lang.ref.WeakReference;
import java.util.Date;


public class MainAppDataReceiver extends BroadcastReceiver {
    private final static String TAG = MainAppDataReceiver.class.getSimpleName();

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


    /** Constructor */
    public MainAppDataReceiver(Context context, int logMethod) {
        logV("Instantiating...");

        this.logMethod = logMethod;
        this.appContextRef = new WeakReference<Context>(context);

        try {
            this.mainApplication = ((MainApplication) context.getApplicationContext());
        } catch (Exception e) {
            logE("Exception caught instantiating MainApplication object: "+e.getMessage());
            return;
        }
    }

    public void cleanup() {
        final String TAGG = "cleanup: ";

        if (this.appContextRef != null) {
            this.appContextRef.clear();
            this.appContextRef = null;
        }

        this.mainApplication = null;
    }

    /** Specify what happens when we receive the broadcasts. */
    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAGG = "onReceive: ";

        if (intent.getAction() == null) {
            logW(TAGG+"Intent's getAction returned null, aborting!");
            return;
        }

        try {
            // Figure out why we got this broadcast, by checking the intent action that we got...
            if (intent.getAction().equals(Constants.Intents.Actions.UPDATE_NUMBER_DELIVERING_MSGS)) {
                logD(TAGG + "Received request to update number of delivering messages.");

                if (intent.getExtras() == null) {
                    logW(TAGG + "Intent contains no extras. Can't know what to update count to. Aborting.");
                    return;
                }

                // Get number of delivering messages data from the intent...
                int numberOfDeliveringMsgs = intent.getIntExtra(Constants.Intents.ExtrasKeys.MAIN_APP_NUMBER_DELIVERING_MSGS, this.mainApplication.numOfDeliveringMsgsInMainApp);

                // Update data wherever it's needed...
                logV(TAGG + "Updating current number of delivering message to: " + Integer.toString(numberOfDeliveringMsgs));
                this.mainApplication.numOfDeliveringMsgsInMainApp = numberOfDeliveringMsgs;

                // Go ahead an update the last-communication timestamps...
                MainApplication.mainAppLastCommunicationTimestamp_previous = MainApplication.mainAppLastCommunicationTimestamp;
                MainApplication.mainAppLastCommunicationTimestamp = new Date();
            }
            else if (intent.getAction().equals(Constants.Intents.Actions.REGISTER_MAIN_APP_HEARTBEAT)) {
                logD(TAGG + "Received request to register a heartbeat from the main delivery app.");

                long mainAppStartedTimestamp;
                long heartbeatTimestamp;

                if (intent.getExtras() == null) {
                    logW(TAGG + "Intent contains no extras. Can't know what to update values to. Aborting.");
                    return;
                }

                mainAppStartedTimestamp = intent.getLongExtra(Constants.Intents.ExtrasKeys.APP_STARTED_DATE_MS, -1);
                heartbeatTimestamp = intent.getLongExtra(Constants.Intents.ExtrasKeys.NOW_DATE_MS, new Date().getTime());

                // Convert and save values
                MainApplication.mainAppHeartbeat_appStartedTimestamp = new Date(mainAppStartedTimestamp);
                MainApplication.mainAppLastCommunicationTimestamp_previous = MainApplication.mainAppLastCommunicationTimestamp;
                MainApplication.mainAppLastCommunicationTimestamp = new Date(heartbeatTimestamp);
                logV(TAGG + "Converted intent's primitives, and set MainApplication heartbeat global values for main delivery app (started "+MainApplication.mainAppHeartbeat_appStartedTimestamp.toString()+" / heartbeat sent "+MainApplication.mainAppLastCommunicationTimestamp.toString()+")");
            }
            /* Experimental, never enacted...
            else if (intent.getAction().equals(Constants.Intents.Actions.MSG_DATA_UPDATE)) {
                logD(TAGG + "Received request to update existing message data.");

                if (intent.getExtras() == null) {
                    logW(TAGG + "Intent contains no extras. Can't know what to update. Aborting.");
                    return;
                }

                // Get message UUID data from intent (or null if nothing there)...
                String msgUuidStr = intent.getStringExtra(Constants.Intents.ExtrasKeys.MSG_UUID_STRING);
                if (msgUuidStr == null) {
                    //no way to update what we don't know, abort
                    logW(TAGG + "Message UUID not provided, so can't update data. Aborting.");
                } else if (msgUuidStr.equals(String.valueOf(MainApplication.msgCurrentlyDelivering_UUID))) {
                    //only update data if message uuid matches

                    // Get message's expected delivery duration (or -1 if nothing there)...
                    long expectedDeliveryDuration = intent.getLongExtra(Constants.Intents.ExtrasKeys.MSG_EXPECTED_DELIVERY_DURATION_MS, -1);
                    logV(TAGG+"Updating message "+msgUuidStr+" expected delivery duration to "+expectedDeliveryDuration);
                    MainApplication.msgCurrentlyDelivering_exectedDeliveryDurationMS = expectedDeliveryDuration;
                } else {
                    logW(TAGG+"Unhandled case. Aborting.");
                }

            }
            */
            else {
                logW(TAGG+"Intent action did not match any handled conditions.");
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Utility Methods */




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
