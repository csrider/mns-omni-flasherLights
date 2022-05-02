package com.messagenetsystems.evolutionflasherlights.utilities;

/**
 * SettingsUtils Class
 *
 * Handles and organizes all settings related tasks.
 * This may include device, hardware, software, and shared-preferences settings.
 *
 * 2019.01.14   Chris Rider     Creation (copied from main app).
 * 2020.06.30   Chris Rider     Added method to write MAC address to provisioning file.
 * 2020.07.01   Chris Rider     Fixed bug in new method from yesterday.
 */

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class SettingsUtils {

    private static String TAG = "SettingsUtils";

    /***********************************************************************************************
     * BLUETOOTH SETTINGS...
     */

    /** Returns whether BLE is supported on this device. */
    public static boolean isBleSupported(Context context) {
        final String TAGG = "isBleSupported: ";

        boolean ret = false;

        try {
            if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
                ret = true;
            }
        } catch (Exception e) {
            Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
        }

        if (ret == false) {
            BluetoothManager mBluetoothManager;
            BluetoothAdapter mBluetoothAdapter;

            //let's try one more test, in case the above didn't work
            try {

                Log.d(TAG, TAGG+"Initializing mBluetoothManager and getting mBluetoothAdapter, so we can test capability.");
                mBluetoothManager = (BluetoothManager) context.getApplicationContext().getSystemService(Context.BLUETOOTH_SERVICE);
                mBluetoothAdapter = mBluetoothManager.getAdapter();

                // Ensure Bluetooth is available on the device and it is enabled
                if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                    Log.w(TAG, TAGG + "Bluetooth adapter is either unavailable or not enabled. Attempting to enable...");
                        enableBluetoothAdapter();
                        try {
                            Log.d(TAG, TAGG+"Waiting for bluetooth to come online.");
                            Thread.sleep(5*1000);
                        } catch (InterruptedException e) {
                            Log.w(TAG, TAGG+"Exception caught trying to wait for Bluetooth to enable.");    //just let it keep trying
                        }
                        if (SettingsUtils.isBluetoothEnabled()) {
                            Log.d(TAG, TAGG+"Bluetooth is now enabled, restarting initialization routine.");
                            //mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
                            //mBluetoothAdapter = mBluetoothManager.getAdapter();
                        }
                } else {
                    Log.d(TAG, TAGG+"Bluetooth initialized.");
                }

                // Make sure we have access coarse location enabled
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, TAGG + "Coarse location permission is not granted.");
                /*
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access");
                builder.setMessage("Please grant location access so this app can detect peripherals.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
                */
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, TAGG + "Exception caught: " + e.getMessage());
            }

            mBluetoothAdapter = null;
            mBluetoothManager = null;
        }

        return ret;
    }

    /** Return whether bluetooth adapter is enabled */
    public static boolean isBluetoothEnabled() {
        final String TAGG = "isBluetoothEnabled: ";
        boolean ret;

        //BluetoothManager mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        //BluetoothAdapter mBluetoothAdapter = mBluetoothManager.getAdapter();
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Log.d(TAG, TAGG+"Adapter is either unavailable or not enabled.");
            ret = false;
        } else {
            ret = true;
        }

        mBluetoothAdapter = null;

        Log.v(TAG, TAGG+"Returning "+ String.valueOf(ret)+".");
        return ret;
    }

    /** Turn on the bluetooth adapter */
    public static void enableBluetoothAdapter() {
        final String TAGG = "enableBluetoothAdapter";

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (isBluetoothEnabled()) {
            Log.d(TAG, TAGG+"Adapter is enabled, nothing to do.");
        } else {
            if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON) {
                Log.d(TAG, TAGG+"Adapter is already currently turning on.");
            } else {
                Log.d(TAG, TAGG + "Adapter is not enabled, enabling now...");
                try {
                    mBluetoothAdapter.enable();
                } catch (Exception e) {
                    Log.e(TAG, TAGG + "Exception caught trying to get and enable BluetoothAdapter: " + e.getMessage());
                }
            }
        }

        mBluetoothAdapter = null;
    }

    public static List<String> getBluetoothDevices_List(Context context) {
        try {
            BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

            Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();

            List<String> s = new ArrayList<String>();
            for(BluetoothDevice bt : pairedDevices) {
                s.add(bt.getName());
            }

            return s;
        } catch (Exception e) {
            Log.e(TAG, "getBluetoothDevices_List: Exception caught trying to get List of bonded devices: "+ e.getMessage());
            return null;
        }
    }

    /** Look for the name of the default connected Bluetooth device
     * @return String Bluetooth device name
     */
    public static String getBluetoothDeviceName(Context context) {
        String name = null;

        // Get the device's default bluetooth adapter
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        try {
            // Get the name of the default bluetooth device
            name = mBluetoothAdapter.getName();
            return name;
        } catch (Exception e) {
            Log.e(TAG, "getBluetoothDeviceName: Exception caught trying to read value for '"+ name +"': "+ e.getMessage() +".");
            return null;
        }
    }


    /***********************************************************************************************
     * SYSTEM SETTINGS...
     */

    /** Validate MAC address */
    public static boolean isThisMacAddressValid(String macAddress) {
        final String TAGG = "isThisMacAddressValid: ";

        boolean ret = false;
        String regex = "([\\da-fA-F]{2}(?:\\:|$)){6}";

        try {
            ret = macAddress.matches(regex);
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+ e.getMessage() +".");
        }

        Log.v(TAG, TAGG+"Returning: \""+ String.valueOf(ret)+"\"");
        return ret;
    }

    public static boolean saveLightControllerMacAddressToProvFile(String macAddress) {
        final String TAGG = "saveLightControllerMacAddressToProvFile(\""+String.valueOf(macAddress)+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = true;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        final String file = "/sdcard/evoProvisionData.xml";

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Populating provisioning data file's MAC address...");
            final String cmd = "/system/bin/sed -i -e 's|<lightControllerMacAddress>.\\{1,\\}</lightControllerMacAddress>|<lightControllerMacAddress>"+macAddress+"</lightControllerMacAddress>|' "+file+"\n";
            Log.v(TAG, TAGG+"cmd = \""+String.valueOf(cmd)+"\"");
            stdin.write(cmd.getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command (nothing if sed is successful)...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                //NOTE: we're looking for something like:
                // <string name="flasherMacAddress">44:A6:E5:1D:FA:82</string>
                if (line.contains("No such file or directory")) {
                    Log.w(TAG, TAGG+"No such file or directory.");
                    ret = false;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
                ret = false;
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }

    public static boolean setSharedPrefsFlasherLightControllerMacAddress(String macAddress) {
        final String TAGG = "setSharedPrefsFlasherLightControllerMacAddress(\""+String.valueOf(macAddress)+"\"): ";
        Log.v(TAG, TAGG+"Invoked.");

        boolean ret = false;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        final String sharedPrefsFile = "/data/user/0/com.messagenetsystems.evolution/shared_prefs/com.messagenetsystems.evolution_preferences.xml";
        //final String sharedPrefsFile = "/sdcard/test.xml";

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Populating main app's shared prefs XML file MAC address...");
            final String cmd = "/system/bin/sed -i -e 's|<string name=\"flasherMacAddress\">.\\{1,\\}</string>|<string name=\"flasherMacAddress\">"+macAddress+"</string>|' "+sharedPrefsFile+"\n";
            Log.v(TAG, TAGG+"cmd = \""+String.valueOf(cmd)+"\"");
            stdin.write(cmd.getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command (nothing if sed is successful)...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                //NOTE: we're looking for something like:
                // <string name="flasherMacAddress">44:A6:E5:1D:FA:82</string>
                if (line.contains("No such file or directory")) {
                    Log.w(TAG, TAGG+"No such file or directory.");
                    ret = false;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
                ret = false;
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
            ret = false;
        }

        final String readSharedPrefsFileMAC = getSharePrefsFlasherLightControllerMacAddress();
        Log.v(TAG, TAGG+"readSharedPrefsFileMAC = \""+String.valueOf(readSharedPrefsFileMAC)+"\"");
        if (readSharedPrefsFileMAC != null
                && readSharedPrefsFileMAC.equalsIgnoreCase(macAddress)) {
            ret = true;
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }
    public static String getSharePrefsFlasherLightControllerMacAddress() {
        final String TAGG = "getSharePrefsFlasherLightControllerMacAddress: ";

        String ret = null;

        /* Original code from main app
        /*
        String keyName = "(unspecified)";
        SharedPreferences prefs;

        try {
            keyName = String.valueOf(context.getResources().getString(R.string.spKeyName_flasherMacAddress));
            prefs = PreferenceManager.getDefaultSharedPreferences(context);                         //get shared prefs
            ret = prefs.getString(keyName, null);                                                   //get the value from shared prefs
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught trying to read value for '"+ keyName +"': "+ e.getMessage() +".");
            ret = null;
        }

        prefs = null;
        */

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        final String sharedPrefsFile = "/data/user/0/com.messagenetsystems.evolution/shared_prefs/com.messagenetsystems.evolution_preferences.xml";
        //final String sharedPrefsFile = "/sdcard/test.xml";

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Looking in main app's shared prefs XML file for MAC address...");
            stdin.write(("/system/bin/grep flasherMacAddress "+sharedPrefsFile+"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                //NOTE: we're looking for something like:
                // <string name="flasherMacAddress">44:A6:E5:1D:FA:82</string>
                if (line.contains("flasherMacAddress")) {
                    String str = line;
                    str = str.split(">")[1];    //str = Element #1 of array ["<string name="flasherMacAddress", "44:A6:E5:1D:FA:82</string"]
                    str = str.split("<")[0];    //str = Element #0 of array ["44:A6:E5:1D:FA:82", "/string"]
                    ret = str;
                } else if (line.contains("No such file or directory")) {
                    Log.w(TAG, TAGG+"No such file or directory.");
                } else {
                    Log.w(TAG, TAGG+"Unhandled error.");
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+String.valueOf(ret));
        return ret;
    }

    public static String getProvFileFlasherLightControllerMacAddress() {
        final String TAGG = "getProvFileFlasherLightControllerMacAddress: ";

        String ret = null;

        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        final String file = "/sdcard/evoProvisionData.xml";

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Looking in provisioning XML file for MAC address...");
            stdin.write(("/system/bin/grep lightControllerMacAddress "+file+"\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                //NOTE: we're looking for something like:
                // <lightControllerMacAddress>44:A6:E5:1A:21:7D</lightControllerMacAddress>
                if (line.contains("lightControllerMacAddress")) {
                    String str = line;          //str = <lightControllerMacAddress>44:A6:E5:1A:21:7D</lightControllerMacAddress>
                    str = str.split(">")[1];    //str.split(">") = Element #1 of array ["<lightControllerMacAddress", "44:A6:E5:1A:21:7D</lightControllerMacAddress"]
                    str = str.split("<")[0];    //str.split("<") = Element #0 of array ["44:A6:E5:1A:21:7D", "/lightControllerMacAddress"]
                    ret = str;
                } else if (line.contains("No such file or directory")) {
                    Log.w(TAG, TAGG+"No such file or directory.");
                } else {
                    Log.w(TAG, TAGG+"Unhandled error.");
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning: "+String.valueOf(ret));
        return ret;
    }

    /** Check if main app is running...
     * Always assume it isn't so we set lights to non-obnoxious state. */
    public static boolean isMainAppRunningOK() {
        final String TAGG = "isMainAppRunningOK: ";

        boolean ret = false;
        Process process = null;
        OutputStream stdin;     //used to write commands to shell... using OutputStream type, we can execute commands like writing commands in terminal
        InputStream stdout;     //used to read output of a command we executed... using InputStream type, we can input command's output to our routine here
        InputStream stderr;     //used to read errors of a command we executed... using InputStream type, we can input command's errors to our routine here
        BufferedReader br;
        String line;

        try {
            // Start a super-user process under which to execute our commands as root
            Log.d(TAG, TAGG + "Starting super-user shell session...");
            process = Runtime.getRuntime().exec("su");

            // Get process streams
            stdin = process.getOutputStream();
            stdout = process.getInputStream();
            stderr = process.getErrorStream();

            // Construct and execute command (new-line is like hitting enter)
            Log.d(TAG, TAGG + "Using ps command to determine if app is running...");
            stdin.write(("/system/bin/ps | /system/bin/grep -w com.messagenetsystems.evolution\n").getBytes());

            // Exit the shell
            stdin.write(("exit\n").getBytes());

            // Flush and close the stdin stream
            stdin.flush();
            stdin.close();

            // Read output of the executed command
            Log.v(TAG, TAGG+"Reading output of executed command...");
            br = new BufferedReader(new InputStreamReader(stdout));
            while ((line = br.readLine()) != null) {
                Log.v(TAG, TAGG+"stdout line: "+line);
                if (line.contains("SyS_epoll_")) {
                    ret = true;
                }
            }
            br.close();

            // Read error stream of the executed command
            Log.v(TAG, TAGG+"Reading errors of executed command...");
            br = new BufferedReader(new InputStreamReader(stderr));
            while ((line = br.readLine()) != null) {
                Log.w(TAG, TAGG+"stderr line: "+line);
                ret = false;
            }
            br.close();

            // Wait for process to finish
            process.waitFor();
            process.destroy();
        } catch (Exception e) {
            Log.e(TAG, TAGG+"Exception caught: "+e.getMessage());
        }

        Log.v(TAG, TAGG+"Returning "+String.valueOf(ret));
        return ret;
    }


    /***********************************************************************************************
     * RESOURCES SETTINGS...
     */

    public static String getResourceString(Context context, int resourceID) {
        String resourceName = "(unknown)";
        String resourceValue;
        try {
            //resourceName = context.getResources().getText(resourceID).toString();
            resourceName = context.getResources().getResourceName(resourceID);
            resourceValue = String.valueOf(context.getResources().getText(resourceID));
            Log.v(TAG, "getResourceString: Returning value for \""+resourceName+"\": \""+resourceValue+"\".");
            return resourceValue;
        } catch (Exception e) {
            Log.e(TAG, "getResourceString: Exception caught trying to read value for \""+resourceName+"\": "+e.getMessage()+".");
            return "";
        }
    }

    public static float getResourceStringAsFloat(Context context, int resourceID) {
        String resourceName = "(unknown)";
        float resourceValue = 0;
        try {
            resourceName = context.getResources().getResourceName(resourceID);
            resourceValue = Float.parseFloat(String.valueOf(context.getResources().getText(resourceID)));
            Log.v(TAG, "getResourceStringAsFloat: Returning value for \""+resourceName+"\": "+resourceValue+".");
        } catch (Exception e) {
            Log.e(TAG, "getResourceStringAsFloat: Exception caught trying to read value for \""+resourceName+"\": "+e.getMessage()+".");
        }
        return resourceValue;
    }


    /***********************************************************************************************
     * SHARED-PREFERENCES SETTINGS...
     */


}
