package com.messagenetsystems.evolutionflasherlights.receivers;

/* CommandReceiver
 * Broadcast receiver for light commands.
 * This is a receiver for any/all apps to use on a device.
 *
 * Example how to broadcast to this receiver to make the lights turn red:
    public static void broadcastLightCommand(Context context, int lightCommand) {
        Intent myIntent = new Intent(FlasherLights.Intents.Filters.LIGHTCMD);
        myIntent.setAction(FlasherLights.Intents.Actions.DO_LIGHT_COMMAND);
        myIntent.putExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD_PLATFORM, FlasherLights.Intents.Extras.Values.PLATFORM_MNS);
        myIntent.putExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD, lightCommand);
        context.sendBroadcast(myIntent);
    }
 *
 * Revisions:
 *  2020.06.01      Chris Rider     Created.
 *  2020.06.16      Chris Rider     Added support for message UUID.
 *  2020.06.25      Chris Rider     Refactored whole app to shift work from BluetoothService to MainApplication.
 *  2020.07.02      Chris Rider     Tweaked logging levels.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.Constants;
import com.messagenetsystems.evolutionflasherlights.MainApplication;
import com.messagenetsystems.evolutionflasherlights.models.FlasherLights;
import com.messagenetsystems.evolutionflasherlights.services.MainService;

import java.lang.ref.WeakReference;
import java.util.UUID;


public class CommandReceiver extends BroadcastReceiver {
    private final static String TAG = CommandReceiver.class.getSimpleName();

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
    public CommandReceiver(Context context, int logMethod) {
        logV("Instantiating...");

        this.logMethod = logMethod;
        this.appContextRef = new WeakReference<Context>(context);

        try {
            this.mainApplication = ((MainApplication) context.getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "Exception caught instantiating "+TAG+": "+e.getMessage());
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
            if (intent.getAction().equals(FlasherLights.Intents.Actions.DO_LIGHT_COMMAND)) {
                Log.d(TAG, TAGG + "Received light request.");

                if (intent.getExtras() == null) {
                    logW(TAGG + "Intent contains no extras. Can't know which light command to handle. Aborting!");
                    return;
                }

                // Invoke execution of the received command
                executeLightCommand(
                        intent.getByteExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD, FlasherLights.CMD_UNKNOWN),
                        intent.getLongExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD_DURATION_S, Long.MAX_VALUE),
                        intent.getStringExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD_MESSAGE_UUID_STR)
                );
            }
            else if (intent.getAction().equals(FlasherLights.Intents.Actions.DO_LIGHT_COMMAND_LEGACY)) {
                Log.d(TAG, TAGG + "Received legacy light request (DEPRECATED).");

                if (intent.getExtras() == null) {
                    logW(TAGG + "Intent contains no extras. Can't know which light command to handle. Aborting!");
                    return;
                }

                // Invoke execution of the received command
                executeLightCommand(
                        //intent.getStringExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD),
                        intent.getByteExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD, FlasherLights.CMD_UNKNOWN),
                        intent.getLongExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD_DURATION_S, Long.MAX_VALUE),
                        intent.getStringExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD_MESSAGE_UUID_STR)
                );
            }
            else if (intent.getAction().equals(FlasherLights.Intents.Actions.POPULATE_LIGHT_CONTROLLER_ASSOCIATION_FILE)) {
                Log.d(TAG, TAGG + "Received request to populate light controller association file.");
                //TODO...

                //get the light controller mac address to associate (intent extras?)

                //populate the file with it

            }else {
                Log.w(TAG, TAGG+"Intent action did not match any handled conditions.");
            }
        } catch (Exception e) {
            logE(TAGG + "Exception caught: " + e.getMessage());
        }
    }


    /*============================================================================================*/
    /* Utility Methods */

    /** Execute the specified light command.
     * @param command Light-command byte to execute
     */
    private void executeLightCommand(byte command, long durationS, String messageUuidStr) {
        final String TAGG = "executeLightCommand: ";
        logD(TAGG+"Invoked for command: "+Byte.toString(command)+" ("+mainApplication.flasherLightOmniCommandCodes.codeToEnglish(command)+")");

        // Normalize duration - should only be positive values
        if (durationS < 0) durationS = 0;

        // Normalize UUID
        UUID uuid;
        if (messageUuidStr == null || messageUuidStr.isEmpty()) {
            uuid = null;
        } else {
            try {
                uuid = UUID.fromString(messageUuidStr);
            } catch (Exception e) {
                logE(TAGG+"Exception caught parsing UUID string to UUID object: "+e.getMessage());
                uuid = null;
            }
        }

        this.mainApplication.executeLightCommand(command, durationS, uuid, false);
    }

    /** Execute the specified MessageNet legacy light command.
     * ** DEPRECATED **
     * @param legacyCommand MessageNet raw BannerMessage dbb_light_signal value to execute
     */
    private void executeLightCommand(String legacyCommand) {
        final String TAGG = "executeLightCommand(legacy): ";
        logD(TAGG+"Invoked for command: \""+legacyCommand+"\"");

        MainService.doLightCommand_legacy(appContextRef.get().getApplicationContext(), legacyCommand, 0);
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
