package com.messagenetsystems.evolutionflasherlights.receivers;

/** BootReceiver
 *
 *  Handles receiving boot-up notification from the system, and any resultant actions.
 *
 *  2019.01.14  Chris Rider     Creation (copied from main app).
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.messagenetsystems.evolutionflasherlights.activities.StartupActivity;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
    /** Specify what happens when we receive the boot-up notification from the OS **/

        Intent intentToStart;

        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            // Define our app's start-up...
            intentToStart = new Intent(context, StartupActivity.class);

            // Start it...
            context.startActivity(intentToStart);

        }

    }

}
