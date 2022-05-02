package com.messagenetsystems.evolutionflasherlights.utilities;

/* ConversionUtils
 *
 * Revisions:
 *  2020.06.12      Chris Rider     Updated byteArrayToHexString method to support delineation for easier reading of the result.
 *  2020.06.23      Chris Rider     Migrated in convertCommandCodeToBleCharacteristicValueList from BluetoothService.
 */

import android.support.annotation.Nullable;
import android.util.Log;

import com.messagenetsystems.evolutionflasherlights.MainApplication;

import java.util.ArrayList;
import java.util.List;


public class ConversionUtils {
    private static final String TAG = ConversionUtils.class.getSimpleName();

    // Constants...
    private final static char[] HEX_CHARS = "0123456789ABCDEF".toCharArray();

    public static String byteArrayToHexString(byte[] bytes, @Nullable String delineator) {
        final String TAGG = "byteArrayToHexString: ";
        String ret;

        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_CHARS[v >>> 4];
            hexChars[j * 2 + 1] = HEX_CHARS[v & 0x0F];
        }

        ret = new String(hexChars);

        if (delineator != null) {
            if (delineator.length() > 1) {
                Log.w(TAG, TAGG+"Only one-character delineators are supported. Omitting delineator altogether.");
            } else {
                Log.v(TAG, TAGG+"Hex Before: \""+ret+"\"");
                StringBuilder updatedRet = new StringBuilder();
                for (int i = 0; i < ret.length(); i++) {
                    if (i % 2 == 0) {
                        updatedRet.append(ret.charAt(i));
                        updatedRet.append(ret.charAt(i+1));
                    } else {
                        if (i < ret.length()-1) updatedRet.append(delineator);
                    }
                }

                // update what we will return
                ret = updatedRet.toString();
            }
        }

        Log.v(TAG, TAGG+"Returning:  \""+ret+"\"");
        return ret;
    }
    public static String byteArrayToHexString(byte[] bytes) {
        return byteArrayToHexString(bytes, null);
    }

    /** Convert the Omni flasher light command code byte to the device's appropriate BLE characteristic value.
     * The returned value is a List of values, in case we need a multipart characteristic write (e.g. to make flash or something).
     * We made it public, just in case the logic is desired elsewhere without needing to duplicate it, for code-maintainability. */
    public static List<byte[]> convertCommandCodeToBleCharacteristicValueList(byte flasherLightCommandCode) {
        final String TAGG = "convertCommandCodeToBleCharacteristicValueList("+String.valueOf(flasherLightCommandCode)+"): ";

        List<byte[]> lightCommand = new ArrayList<>();

        byte[] lightCommandBase;
        byte[] lightCommandAdditional = null;

        try {
            // Translate the signal-light command from message into a light controller command
            if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_OFF) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_turnOff();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_RED_BRI) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_red(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_RED_MED) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMedBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_red());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_RED_DIM) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMinBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_red());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_GREEN_BRI) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_green(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_GREEN_MED) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMedBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_green());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_GREEN_DIM) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMinBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_green());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_BLUE_BRI) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_blue(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_BLUE_MED) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMedBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_blue());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_BLUE_DIM) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMinBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_blue());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_ORANGE_BRI) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_orange(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_ORANGE_MED) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMedBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_orange());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_ORANGE_DIM) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMinBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_orange());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PINK_BRI) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_pink(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PINK_MED) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMedBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_pink());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PINK_DIM) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMinBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_pink());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PURPLE_BRI) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_purple(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PURPLE_MED) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMedBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_purple());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_PURPLE_DIM) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMinBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_purple());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_YELLOW_BRI) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_yellow(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_YELLOW_MED) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMedBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_yellow());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_YELLOW_DIM) {
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMinBrightness(MainApplication.lightControllerDeviceModel.constructDataBytes_color_yellow());
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITECOOL_BRI
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEPURE_BRI
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEWARM_BRI) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_whiteMaxBrightnessSteady();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITECOOL_MED
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEPURE_MED
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEWARM_MED) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_whiteMedBrightness();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITECOOL_DIM
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEPURE_DIM
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_WHITEWARM_DIM) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_whiteMinBrightness();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_RED
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_RED) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_red(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
                lightCommandAdditional = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_flashingOn();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_GREEN
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_GREEN) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_green(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
                lightCommandAdditional = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_flashingOn();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_BLUE
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_BLUE) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_blue(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
                lightCommandAdditional = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_flashingOn();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_ORANGE
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_ORANGE) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_orange(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
                lightCommandAdditional = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_flashingOn();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_PINK
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_PINK) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_pink(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
                lightCommandAdditional = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_flashingOn();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_PURPLE
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_PURPLE) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_purple(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
                lightCommandAdditional = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_flashingOn();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_YELLOW
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_YELLOW) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_colorMaxBrightnessSteady(MainApplication.lightControllerDeviceModel.constructDataBytes_color_yellow(MainApplication.lightControllerDeviceModel.COLOR_BRIGHTNESS_MAX));
                lightCommandAdditional = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_flashingOn();
            }
            else if (flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITECOOL
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITECOOL
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITEPURE
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITEPURE
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FLASHING_WHITEWARM
                    || flasherLightCommandCode == MainApplication.flasherLightOmniCommandCodes.CMD_LIGHT_FADING_WHITEWARM) {   //TODO: differentiate
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_whiteMaxBrightnessSteady();
                lightCommandAdditional = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_flashingOn();
            }
            else {
                //default
                lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_whiteRgbMinBrightness();
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            lightCommandBase = MainApplication.lightControllerDeviceModel.constructLightCommandByteSequence_whiteRgbMinBrightness();
        }

        // Assemble the list of byte arrays
        lightCommand.add(lightCommandBase);
        if (lightCommandAdditional != null) {
            lightCommand.add(lightCommandAdditional);
        }

        Log.v(TAG, TAGG+"Returning: "+String.valueOf(lightCommand));
        return lightCommand;
    }

}
