package com.messagenetsystems.evolutionflasherlights.receivers;

/* NotificationActionReceiver
 * Broadcast receiver for notification bar item's actions.
 *
 * Revisions:
 *  2020.07.01      Chris Rider     Created.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.MainApplication;
import com.messagenetsystems.evolutionflasherlights.models.FlasherLights;

import java.util.UUID;

public class NotificationActionReceiver extends BroadcastReceiver {
    private final String TAG = NotificationActionReceiver.class.getSimpleName()+": ";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String TAGG = "onReceive: ";

        if (intent.getAction() == null) {
            FL.e(TAG+TAGG+"Intent's getAction returned null, aborting!");
            return;
        }

        MainApplication mainApplication = ((MainApplication) context.getApplicationContext());

        try {
            // Figure out why we got this broadcast, by checking the intent action that we got...
            if (intent.getAction().equals(FlasherLights.Intents.Actions.DO_LIGHT_COMMAND)) {
                FL.d(TAG+TAGG + "Received light request.");

                if (intent.getExtras() == null) {
                    FL.w(TAG+TAGG + "Intent contains no extras. Can't know which light command to handle. Aborting!");
                    return;
                }

                // Invoke execution of the received command
                executeLightCommand(
                        mainApplication,
                        intent.getByteExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD, FlasherLights.CMD_UNKNOWN),
                        intent.getLongExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD_DURATION_S, Long.MAX_VALUE),
                        intent.getStringExtra(FlasherLights.Intents.Extras.Keys.LIGHT_CMD_MESSAGE_UUID_STR)
                );
            }
            else {
                FL.w(TAG+TAGG+"Intent action did not match any handled conditions.");
            }
        } catch (Exception e) {
            FL.e(TAG+TAGG + "Exception caught: " + e.getMessage());
        }

    }

    /** Execute the specified light command.
     * @param command Light-command byte to execute
     */
    private void executeLightCommand(MainApplication mainApplication, byte command, long durationS, String messageUuidStr) {
        final String TAGG = "executeLightCommand: ";
        FL.v(TAG+TAGG+"Invoked for command: "+Byte.toString(command)+" ("+mainApplication.flasherLightOmniCommandCodes.codeToEnglish(command)+")");

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
                FL.e(TAG+TAGG+"Exception caught parsing UUID string to UUID object: "+e.getMessage());
                uuid = null;
            }
        }

        mainApplication.executeLightCommand(command, durationS, uuid, false);
    }
}
