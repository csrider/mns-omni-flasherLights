package com.messagenetsystems.evolutionflasherlights;

/* Constants class
 * Use this to conveniently and reliably reuse and maintain code.
 * This should focus on stuff that needs to be used across multiple classes.
 * Just use its members statically. No instantiation necessary or really desired. Keep it simple.
 *
 * Revisions:
 *  2020.05.28      Chris Rider     Created (migrated from main app).
 *  2020.06.01      Chris Rider     Moved light commands and intent strings to FlasherLights, an easily portable class file that can go to other apps to ensure consistency and reduce bugs.
 *  2020.06.18      Chris Rider     Added constant for found-controller notification text.
 *  2020.06.28      Chris Rider     Refactored intent-related constants names and values to make code maintainability easier.
 *  2020.07.02      Chris Rider     Decreased light command timeout from 2500ms to 2000ms.
 */


public class Constants {

    public static final int LOG_METHOD_LOGCAT = 1;
    public static final int LOG_METHOD_FILELOGGER = 2;

    public static final String NOTIF_TEXT_SCAN_START = "Scanning for light controller...";
    public static final String NOTIF_TEXT_FOUND_CONTROLLER = "Associated light controller";

    public static final boolean GATT_AUTOCONNECT = false;

    // Number of milliseconds to give a light command to respond, before we brute-force it to cancel
    public static final int LIGHT_COMMAND_TIMEOUT_MS = 2000;

    // WARNING: Make sure any changes to this block coincide with other apps' Constants class files!
    public static final String NAMESPACE_MESSAGENET = "com.messagenetsystems";
    public static final String PACKAGE_NAME_MAIN_APP = NAMESPACE_MESSAGENET+".evolution2";
    public static final String PACKAGE_NAME_FLASHERS = NAMESPACE_MESSAGENET+".evolutionflasherlights";

    // WARNING: Make sure any changes to this block coincide with other apps' Constants class files!
    public static class Intents {
        public static class Filters {
            public static String MAIN_APP_HEARTBEAT = PACKAGE_NAME_MAIN_APP + ".intent.filter.heartbeat";                                   //main delivery app's heartbeat intent filter string

            public static String MAIN_APP_DELIVERY_STATUS = PACKAGE_NAME_MAIN_APP + ".intent.filter.deliveryStatus";                        //main delivery app's delivery status intent filter string

            //public static String BT_SERVICE_COMMAND = PREFIX + ".bluetoothServiceCommand";
        }

        public static class Actions {
            public static String REGISTER_MAIN_APP_HEARTBEAT = PACKAGE_NAME_MAIN_APP + ".intent.action.registerHeartbeat";                  //main delivery app's heartbeat register request action string

            public static String UPDATE_NUMBER_DELIVERING_MSGS = PACKAGE_NAME_MAIN_APP + ".intent.action.updateNumberDeliveringMsgs";       //main delivery app's update # delivering msgs request action string

            //public static String BT_DEVICE_REACQUIRE = PREFIX + ".bluetoothDeviceReacquire";
            //public static String BT_RESTART = PREFIX + ".bluetoothReset";

            public static String MSG_DATA_UPDATE = ".msgDataUpdate";    //TODO: do we really need this? was just an idea to update message data after delivery has already been sent to us
        }

        public static class ExtrasKeys {
            public static String NOW_DATE_MS = NAMESPACE_MESSAGENET + ".intent.extra.nowDateMilliseconds";                                  //generic extras-key for current Date.getTime() value
            public static String APP_STARTED_DATE_MS = NAMESPACE_MESSAGENET + ".intent.extra.appStartedDateMilliseconds";                   //generic extras-key string for when app started

            public static String MAIN_APP_NUMBER_DELIVERING_MSGS = PACKAGE_NAME_MAIN_APP + ".intent.extra.numOfDeliveringMsgs";             //main delivery app's extras-key string for number of delivering msgs

            // Experimental, never enacted...
            //public static String MSG_UUID_STRING = PACKAGE_NAME_MAIN_APP + ".intent.extra.msgUuidString";
            //public static String MSG_EXPECTED_DELIVERY_DURATION_MS = PACKAGE_NAME_MAIN_APP + ".intent.extra.msgExpectedDeliveryDurationMS";
        }
    }


    /** Configuration subclass */
    public static class Configuration {
        public class App {
            public static final boolean LOG_TO_FILE = true;
        }
    }

}
