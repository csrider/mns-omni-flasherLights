package com.messagenetsystems.evolutionflasherlights.receivers;

/** RequestReceiver
 * Handles receiving request to do something with the lights.
 *
 * Revisions:
 *  2019.01.14  Chris Rider     Created.
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import com.messagenetsystems.evolutionflasherlights.R;
import com.messagenetsystems.evolutionflasherlights.services.BluetoothFlasherLightsService;

public class RequestReceiver extends BroadcastReceiver {
    private static final String TAG = RequestReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        String TAGG = "onReceive ("+String.valueOf(intent.getAction())+"): ";
        Log.v(TAG, TAGG+"Invoked.");

        final String intentAction_handleLightRequest = context.getResources().getString(R.string.intentAction_handleLightRequest);
        final String intentAction_populateLightControllerAssociationFile = context.getResources().getString(R.string.intentAction_populateLightControllerAssociationFile);

        if (intent.getAction().equals(intentAction_handleLightRequest)) {
            Log.d(TAG, TAGG + "Received light request.");

            //parse out the type of request

            //if request is to send a command to the lights, then do that
            //doLightCommand(context, );

        } else if (intent.getAction().equals(intentAction_populateLightControllerAssociationFile)) {
            Log.d(TAG, TAGG + "Received request to populate light controller association file.");

            //get the light controller mac address to associate (intent extras?)

            //populate the file with it

        } else {
            Log.w(TAG, TAGG+"Intent action did not match any handled conditions.");
        }
    }

    void doLightCommand(final Context context, final String lightCmd, final int numToRepeatCmd, final String postCmdStatusToShow) {
        final String TAGG = "doLightCommand(\""+lightCmd+"\","+String.valueOf(numToRepeatCmd)+",\""+String.valueOf(postCmdStatusToShow)+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        Log.d(TAG, TAGG+"Requesting light action.");
        BluetoothFlasherLightsService.requestLightAction(context,
                lightCmd,
                BluetoothFlasherLightsService.LightCommandBroadcastReceiver.CMD_LIGHTS_FORCE_SEND_YES,
                BluetoothFlasherLightsService.LightCommandBroadcastReceiver.CMD_LIGHTS_DO_PREVENT_FURTHER_COMMANDS_NO);

        if (numToRepeatCmd > 0) {
            Log.v(TAG, TAGG+"Repeating command (numToRepeatCmd="+numToRepeatCmd+")");

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    doLightCommand(context, lightCmd, numToRepeatCmd-1, postCmdStatusToShow);
                }
            }, 5*1000);
        }
    }
}
