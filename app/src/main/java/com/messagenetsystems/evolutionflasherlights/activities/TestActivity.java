package com.messagenetsystems.evolutionflasherlights.activities;

/* TestActivity
 * This activity is for testing and debugging the light connection, modes, etc.
 *
 * Revisions:
 *  2020.07.18      Chris Rider     Created. Originally in an effort to try and test the revised Chinese instructions for maintaining connection to the lights.
 */

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import com.bosphere.filelogger.FL;
import com.messagenetsystems.evolutionflasherlights.Constants;
import com.messagenetsystems.evolutionflasherlights.MainApplication;
import com.messagenetsystems.evolutionflasherlights.R;
import com.messagenetsystems.evolutionflasherlights.devices.BluetoothLightController_HY254117V9;
import com.messagenetsystems.evolutionflasherlights.utilities.ConversionUtils;
import com.messagenetsystems.evolutionflasherlights.utilities.SettingsUtils;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.BOND_BONDED;
import static android.bluetooth.BluetoothDevice.BOND_BONDING;
import static android.bluetooth.BluetoothDevice.BOND_NONE;
import static android.bluetooth.BluetoothGatt.GATT_SUCCESS;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;


public class TestActivity extends Activity {
    final String TAG = TestActivity.class.getSimpleName();

    private Context appContext;
    private MainApplication mainApplication;

    private TextView tvStatus;
    private EditText etLightMac;
    private Button btnConnect, btnDisconnect;
    private CheckBox chkBoxFlashing;
    private Button btnRed, btnOrange, btnYellow, btnGreen, btnBlue, btnPurple, btnStandby, btnWhite, btnOff;

    private String lightMacAddress;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDevice bluetoothDevice;
    private BluetoothGatt bluetoothGatt;
    private GattCallback gattCallback;

    // Logging stuff...
    private final int LOG_SEVERITY_V = 1;
    private final int LOG_SEVERITY_D = 2;
    private final int LOG_SEVERITY_I = 3;
    private final int LOG_SEVERITY_W = 4;
    private final int LOG_SEVERITY_E = 5;
    private int logMethod = Constants.LOG_METHOD_LOGCAT;


    /*============================================================================================*/
    /* Class Methods */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        final String TAGG = "onCreate: ";
        logV(TAGG + "Invoked.");

        appContext = getApplicationContext();

        try {
            // Initialize reference to Application instance
            mainApplication = ((MainApplication) appContext);

            // Initialize bluetooth adapter
            final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();

            // Initialize bluetooth light module device
            lightMacAddress = SettingsUtils.getProvFileFlasherLightControllerMacAddress();
            bluetoothDevice = bluetoothAdapter.getRemoteDevice(lightMacAddress.toUpperCase());

            // Initialize GATT callback
            gattCallback = new GattCallback(this);

            // Initialize activity elements
            tvStatus = (TextView) findViewById(R.id.test_tv_status);
            etLightMac = (EditText) findViewById(R.id.test_et_MAC);
            btnConnect = (Button) findViewById(R.id.test_btn_connect);
            btnDisconnect = (Button) findViewById(R.id.test_btn_disconnect);
            chkBoxFlashing = (CheckBox) findViewById(R.id.test_chk_flashing);
            btnRed = (Button) findViewById(R.id.test_btn_red);
            btnOrange = (Button) findViewById(R.id.test_btn_orange);
            btnYellow = (Button) findViewById(R.id.test_btn_yellow);
            btnGreen = (Button) findViewById(R.id.test_btn_green);
            btnBlue = (Button) findViewById(R.id.test_btn_blue);
            btnPurple = (Button) findViewById(R.id.test_btn_purple);
            btnStandby = (Button) findViewById(R.id.test_btn_standby);
            btnWhite = (Button) findViewById(R.id.test_btn_white);
            btnOff = (Button) findViewById(R.id.test_btn_off);

            // Initialize activity element actions
            btnConnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_gattConnecting(null);
                    bluetoothGatt = bluetoothDevice.connectGatt(appContext, false, gattCallback);
                    //TODO: Let callback handle setting UI, depending on how it goes
                }
            });
            btnDisconnect.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_gattDisconnecting(null);
                    bluetoothGatt.disconnect();
                    //TODO: Let callback handle setting UI, depending on how it goes
                }
            });
            btnRed.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_redBright);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });
            btnOrange.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_orangeBright);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });
            btnYellow.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_yellowBright);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });
            btnGreen.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_greenBright);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });
            btnBlue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_blueBright);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });
            btnPurple.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_purpleBright);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });
            btnStandby.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_standby);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });
            btnWhite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_whiteBright);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });
            btnOff.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setUI_commanding(null);
                    BluetoothGattCharacteristic characteristic = bluetoothGatt.getService(gattCallback.uuid_service).getCharacteristic(gattCallback.uuid_char1001);
                    characteristic.setValue(gattCallback.charValue_off);
                    bluetoothGatt.writeCharacteristic(characteristic);
                }
            });

        } catch (Exception e) {
            logE(TAGG+"Exception caught instantiating "+TAG+": "+e.getMessage());
        }
    }

    @Override
    public void onResume() {
        // This will also fire if singleInstance flag is set and you start the activity
        super.onResume();
        final String TAGG = "onResume: ";
        logV(TAGG+"Running onResume (activity should now be visible and in-front)");

        etLightMac.setText(lightMacAddress);

        if (bluetoothGatt == null || bluetoothGatt.getConnectionState(bluetoothDevice) == STATE_DISCONNECTED) {
            setUI_gattDisconnected(null);
        } else if (bluetoothGatt.getConnectionState(bluetoothDevice) == STATE_CONNECTED) {
            setUI_gattConnectedAndReady(null);
        } else {
            setUI_gattDisconnected("Unhandled connection state!");
        }

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, 123);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        final String TAGG = "onPause: ";
        logV(TAGG+"Running onPause (activity is no longer visible or in-front)");

        if (bluetoothGatt != null && bluetoothGatt.getConnectionState(bluetoothDevice) == BluetoothGatt.STATE_CONNECTED) {
            logV(TAGG+"Disconnecting and closing GATT client...");
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
        }
    }

    @Override
    public void onDestroy() {
        final String TAGG = "onDestroy: ";
        logV(TAGG+"Running onDestroy.");

        if (bluetoothDevice != null) {
            bluetoothDevice = null;
        }

        if (bluetoothAdapter != null) {
            bluetoothAdapter = null;
        }

        super.onDestroy();
    }


    /*============================================================================================*/
    /* Utility methods */

    private void setUI_gattConnecting(TestActivity ta, @Nullable String statusText) {
        if (statusText == null)
            statusText = "Connecting...";

        ta.tvStatus.setText(statusText);
        ta.btnConnect.setEnabled(false);
        ta.btnDisconnect.setEnabled(false);
        ta.chkBoxFlashing.setEnabled(false);
        ta.btnRed.setEnabled(false);
        ta.btnOrange.setEnabled(false);
        ta.btnYellow.setEnabled(false);
        ta.btnGreen.setEnabled(false);
        ta.btnBlue.setEnabled(false);
        ta.btnPurple.setEnabled(false);
        ta.btnStandby.setEnabled(false);
        ta.btnWhite.setEnabled(false);
        ta.btnOff.setEnabled(false);
    }
    private void setUI_gattConnecting(@Nullable String statusText) {
        setUI_gattConnecting(this, statusText);
    }

    private void setUI_gattConnectedAndReady(TestActivity ta, @Nullable String statusText) {
        if (statusText == null)
            statusText = "Connected and ready for commands.";

        ta.tvStatus.setText(statusText);
        ta.btnConnect.setEnabled(false);
        ta.btnDisconnect.setEnabled(true);
        ta.chkBoxFlashing.setEnabled(true);
        ta.btnRed.setEnabled(true);
        ta.btnOrange.setEnabled(true);
        ta.btnYellow.setEnabled(true);
        ta.btnGreen.setEnabled(true);
        ta.btnBlue.setEnabled(true);
        ta.btnPurple.setEnabled(true);
        ta.btnStandby.setEnabled(true);
        ta.btnWhite.setEnabled(true);
        ta.btnOff.setEnabled(true);
    }
    private void setUI_gattConnectedAndReady(@Nullable String statusText) {
        setUI_gattConnectedAndReady(this, statusText);
    }

    private void setUI_gattDisconnecting(TestActivity ta, @Nullable String statusText) {
        if (statusText == null)
            statusText = "Disconnecting...";

        ta.tvStatus.setText(statusText);
        ta.btnConnect.setEnabled(false);
        ta.btnDisconnect.setEnabled(false);
        ta.chkBoxFlashing.setEnabled(false);
        ta.btnRed.setEnabled(false);
        ta.btnOrange.setEnabled(false);
        ta.btnYellow.setEnabled(false);
        ta.btnGreen.setEnabled(false);
        ta.btnBlue.setEnabled(false);
        ta.btnPurple.setEnabled(false);
        ta.btnStandby.setEnabled(false);
        ta.btnWhite.setEnabled(false);
        ta.btnOff.setEnabled(false);
    }
    private void setUI_gattDisconnecting(@Nullable String statusText) {
        setUI_gattDisconnecting(this, statusText);
    }

    private void setUI_gattDisconnected(TestActivity ta, @Nullable String statusText) {
        if (statusText == null)
            statusText = "Disconnected.";

        ta.tvStatus.setText(statusText);
        ta.btnConnect.setEnabled(true);
        ta.btnDisconnect.setEnabled(false);
        ta.chkBoxFlashing.setEnabled(false);
        ta.btnRed.setEnabled(false);
        ta.btnOrange.setEnabled(false);
        ta.btnYellow.setEnabled(false);
        ta.btnGreen.setEnabled(false);
        ta.btnBlue.setEnabled(false);
        ta.btnPurple.setEnabled(false);
        ta.btnStandby.setEnabled(false);
        ta.btnWhite.setEnabled(false);
        ta.btnOff.setEnabled(false);
    }
    private void setUI_gattDisconnected(@Nullable String statusText) {
        setUI_gattDisconnected(this, statusText);
    }

    private void setUI_commanding(TestActivity ta, @Nullable String statusText) {
        if (statusText == null)
            statusText = "Sending command.";

        ta.tvStatus.setText(statusText);
        ta.btnConnect.setEnabled(false);
        ta.btnDisconnect.setEnabled(false);
        ta.chkBoxFlashing.setEnabled(false);
        ta.btnRed.setEnabled(false);
        ta.btnOrange.setEnabled(false);
        ta.btnYellow.setEnabled(false);
        ta.btnGreen.setEnabled(false);
        ta.btnBlue.setEnabled(false);
        ta.btnPurple.setEnabled(false);
        ta.btnStandby.setEnabled(false);
        ta.btnWhite.setEnabled(false);
        ta.btnOff.setEnabled(false);
    }
    private void setUI_commanding(@Nullable String statusText) {
        setUI_commanding(this, statusText);
    }


    /*============================================================================================*/
    /* Subclasses */

    /** Define what happens when doing GATT stuff (connecting, writing, etc.).
     * This is setup to be instantiated whenever you have a discrete command to send, and to live only during that command. */
    private class GattCallback extends BluetoothGattCallback {
        final String TAGG = GattCallback.class.getSimpleName() + ": ";

        UUID uuid_service = UUID.fromString("00001000-0000-1000-8000-00805f9b34fb");
        UUID uuid_char1001 = UUID.fromString("00001001-0000-1000-8000-00805f9b34fb");
        UUID uuid_char1002 = UUID.fromString("00001002-0000-1000-8000-00805f9b34fb");
        UUID uuid_char1003 = UUID.fromString("00001003-0000-1000-8000-00805f9b34fb");
        byte[] charValue_handshake = new byte[]{(byte)0xb8,(byte)0x04,(byte)0x04,(byte)0xe3,(byte)0x24,(byte)0xa8,(byte)0x69};
        byte[] charValue_password = new byte[]{(byte)0xb8,(byte)0x03,(byte)0x05,(byte)0x04,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x00};   //B8 03 05 04 00 00 00 00

        BluetoothLightController_HY254117V9 lightController_hy254117V9 = new BluetoothLightController_HY254117V9(logMethod);
        byte[] charValue_redBright = lightController_hy254117V9.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController_hy254117V9.constructDataBytes_color_red());
        byte[] charValue_orangeBright = lightController_hy254117V9.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController_hy254117V9.constructDataBytes_color_orange());
        byte[] charValue_yellowBright = lightController_hy254117V9.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController_hy254117V9.constructDataBytes_color_yellow());
        byte[] charValue_greenBright = lightController_hy254117V9.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController_hy254117V9.constructDataBytes_color_green());
        byte[] charValue_blueBright = lightController_hy254117V9.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController_hy254117V9.constructDataBytes_color_blue());
        byte[] charValue_purpleBright = lightController_hy254117V9.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController_hy254117V9.constructDataBytes_color_purple());
        byte[] charValue_standby = lightController_hy254117V9.constructLightCommandByteSequence_colorMaxBrightnessSteady(lightController_hy254117V9.constructLightCommandByteSequence_whiteRgbMinBrightness());
        byte[] charValue_whiteBright = lightController_hy254117V9.constructLightCommandByteSequence_whiteMaxBrightnessSteady();
        byte[] charValue_off = lightController_hy254117V9.constructLightCommandByteSequence_turnOff();
        byte[] charValue_flashing = lightController_hy254117V9.constructLightCommandByteSequence_flashingOn();

        TestActivity testActivity;
        List<BluetoothGattService> services = null;

        GattCallback(TestActivity testActivity) {
            this.testActivity = testActivity;
        }

        @Override
        public void onConnectionStateChange(final BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            final String TAGG = this.TAGG+"onConnectionStateChange: ";
            logV(TAGG+TAGG + "Invoked (status=" + status + ", newState=" + newState + ").");

            if (status == GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    // We're not really bonding in our scheme here, but it's "proper" to handle it in case
                    // Take action depending on the bond state (which, if in play, would dictate how/when we continue)
                    int bondState = bluetoothDevice.getBondState();
                    if (bondState == BOND_NONE || bondState == BOND_BONDED) {
                        // Connected to device, now proceed to discover its services but delay a bit if needed
                        // With Android versions prior to 8 (7 and lower), if the device has the "Service Changed Characteristic",
                        // the Android stack will still be busy handling it and calling discoverServices() w/out a delay would make
                        // it fail, so you have to add a 1000-1500ms delay. The exact time needed depends on the number of
                        // characteristics of your device. Since at this point, you don't know yet if the device has this characteristic,
                        // it is best to simply always do the delay.
                        int delayWhenBonded = 50;
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.N) {
                            delayWhenBonded = 1000;
                        }
                        final int delay = bondState == BOND_BONDED ? delayWhenBonded : 50;
                        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                logV(TAGG+"Discovering services with "+delay+"ms delay.");
                                testActivity.runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        setUI_gattConnecting(testActivity, "Discovering services...");
                                    }
                                });
                                if (!gatt.discoverServices()) {
                                    logE(TAGG+"discoverServices failed to start.");
                                }
                            }
                        }, delay);
                    }  else if (bondState == BOND_BONDING) {
                        // Bonding process in progress, let it complete
                        // Stack would be busy in this case and service discovery unavailable
                        logW(TAGG+"Waiting for bonding to complete.");
                    } else {
                        // Unhandled case
                        logE(TAGG+"Unhandled bond state.");
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    // We successfully disconnected on our own request
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattDisconnected(testActivity, null);
                        }
                    });
                    gatt.close();
                } else {
                    // We're CONNECTING or DISCONNECTING, ignore for now as it isn't really implemented anyway
                }
            } else {
                // An error happened... figure it out

                if (status == 19) {
                    // GATT_CONN_TERMINATE_PEER_USER
                    // The device disconnected itself on purpose.
                    // For example, all data has been transferred and there is nothing else to do.
                    logW(TAGG+"Device has disconnected itself on purpose. Closing.");
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattDisconnected(testActivity, "Peripheral has disconnected.");
                        }
                    });
                    gatt.close();
                } else if (status == 8) {
                    // GATT_CONN_TIMEOUT
                    // The connection timed out and device disconnected itself.
                    logW(TAGG+"Connection timed-out and device disconnected itself. Closing.");
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattDisconnected(testActivity, "Connection timed-out. Peripheral has disconnected.");
                        }
                    });
                    gatt.close();
                } else if (status == 133) {
                    // GATT_ERROR (this really means nothing, thanks to Android's poor implementation)
                    // There was a low-level error in the communication which led to loss of connection.
                    logE(TAGG+"Status 133 (low-level error / loss of connection / failure to connect). ");
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattDisconnected(testActivity, "Bluetooth stack error. Failed to connect.");
                        }
                    });
                    gatt.close();
                } else {
                    logE(TAGG + "An error occurred.");
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattDisconnected(testActivity, "Unknown error. Failed to connect.");
                        }
                    });
                    gatt.close();
                }
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            final String TAGG = this.TAGG+"onServicesDiscovered: ";
            logV(TAGG + "Invoked (status="+status+")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                services = gatt.getServices();
                if (services == null) {
                    logE(TAGG+"No services available, disconnecting and aborting.");
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattDisconnecting(testActivity, "No services found! Disconnecting...");
                        }
                    });
                    gatt.disconnect();
                    return;
                }

                logD(TAGG+"Success. "+services.size()+" services available.");

                // Set high priority connection
                gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);

                // Get our service
                BluetoothGattService service = gatt.getService(uuid_service);
                if (service == null) {
                    logE(TAGG+"Service not found!");
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattDisconnecting(testActivity, "Service unavailable. Disconnecting...");
                        }
                    });
                    gatt.disconnect();
                    return;
                }

                // Enable notify
                BluetoothGattCharacteristic char1002 = service.getCharacteristic(uuid_char1002);
                gatt.setCharacteristicNotification(char1002, true);
                // Now that notify is enabled, it will have descriptor with handle 0x2902, so we need
                // to write BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE to it. First, convert 0x2902
                // to 128 bit UUID (00002902 + BASE-96 BLE UUID).
                UUID notifyDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
                BluetoothGattDescriptor descriptor = char1002.getDescriptor(notifyDescriptorUUID);
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);

                // Send handshake
                /*
                BluetoothGattCharacteristic characteristic = service.getCharacteristic(uuid_char1003);
                if (characteristic == null) {
                    logE(TAGG+"Characteristic (for writing handshake data) not found!");
                } else {
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattConnecting(testActivity, "Writing handshake data...");
                        }
                    });
                    characteristic.setValue(charValue_handshake);
                    gatt.writeCharacteristic(characteristic);
                }
                */
            } else {
                //logW(TAGG+TAGG+"Unhandled status.");
                logD(TAGG+"Unhandled status, disconnecting...");
                testActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setUI_gattDisconnecting(testActivity, "Problem in service discovery! Disconnecting...");
                    }
                });
                gatt.disconnect();
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            final String TAGG = this.TAGG+"onCharacteristicChanged: ";
            logV(TAGG + "Invoked ("+characteristic.getValue().toString()+").");
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            final String TAGG = this.TAGG+"onCharacteristicRead: ";
            logV(TAGG + "Invoked (status="+status+")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                logD(TAGG+"Success.");

                logV(TAGG + "Characteristic value = \"" + ConversionUtils.byteArrayToHexString(characteristic.getValue()) + "\".");
            } else {
                logW(TAGG+"Unhandled status.");
            }
        }

        @Override
        // Result of a characteristic write operation
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            final String TAGG = this.TAGG+"onCharacteristicWrite: ";
            logV(TAGG+TAGG + "Invoked (status="+status+")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                logD(TAGG+"Success (bytes that were written: \"" + ConversionUtils.byteArrayToHexString(characteristic.getValue()) + "\").");

                if (Arrays.equals(characteristic.getValue(), charValue_handshake)) {
                    logV(TAGG + "Handshake written.");
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattConnecting(testActivity, "Handshake completed. Sending password...");
                        }
                    });
                    characteristic.setValue(charValue_password);
                    gatt.writeCharacteristic(characteristic);
                } else if (Arrays.equals(characteristic.getValue(), charValue_password)) {
                    logV(TAGG + "Password written.");
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattConnectedAndReady(testActivity, null);
                        }
                    });
                } else if (Arrays.equals(characteristic.getValue(), charValue_redBright)
                        || Arrays.equals(characteristic.getValue(), charValue_orangeBright)
                        || Arrays.equals(characteristic.getValue(), charValue_yellowBright)
                        || Arrays.equals(characteristic.getValue(), charValue_greenBright)
                        || Arrays.equals(characteristic.getValue(), charValue_blueBright)
                        || Arrays.equals(characteristic.getValue(), charValue_purpleBright)
                        || Arrays.equals(characteristic.getValue(), charValue_standby)
                        || Arrays.equals(characteristic.getValue(), charValue_whiteBright)
                        || Arrays.equals(characteristic.getValue(), charValue_off)
                        ) {
                    logV(TAGG+"Light mode written: "+ConversionUtils.byteArrayToHexString(characteristic.getValue()));
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattConnectedAndReady(testActivity, "Command sent. Ready for another action.");
                        }
                    });
                } else {
                    logV(TAGG + "Characteristic value = \"" + ConversionUtils.byteArrayToHexString(characteristic.getValue()) + "\".");
                }

            } else {
                logW(TAGG+"Unhandled status.");
                //lastWrittenLightCommand_asDecimal = SIGNALLIGHT_CMD_NONE;


            }

            //logD(TAGG+TAGG+"Disconnecting.");
            //gatt.disconnect();
        }

        @Override
        // Result of a characteristic write operation
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            final String TAGG = this.TAGG+"onDescriptorWrite: ";
            logV(TAGG+TAGG + "Invoked (status="+status+")");

            if (status == BluetoothGatt.GATT_SUCCESS) {
                logD(TAGG+"Success (bytes that were written: \"" + ConversionUtils.byteArrayToHexString(descriptor.getValue()) + "\").");

                // Write handshake char value
                BluetoothGattCharacteristic characteristic = gatt.getService(uuid_service).getCharacteristic(uuid_char1003);
                if (characteristic == null) {
                    logE(TAGG+"Characteristic (for writing handshake data) not found!");
                } else {
                    testActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            setUI_gattConnecting(testActivity, "Writing handshake data...");
                        }
                    });
                    characteristic.setValue(charValue_handshake);
                    gatt.writeCharacteristic(characteristic);
                }

            } else {
                logW(TAGG+"Unhandled status.");
                //lastWrittenLightCommand_asDecimal = SIGNALLIGHT_CMD_NONE;


            }

            //logD(TAGG+TAGG+"Disconnecting.");
            //gatt.disconnect();
        }
    }

    /** Method to setup the characteristic for notify.
     * Note: EXPERIMENTAL and unused! See GattCallback subclass below. */
    /*
    public boolean setCharacteristicNotification(BluetoothGatt bluetoothGatt, BluetoothGattCharacteristic characteristic, boolean enable) {
        final String TAGG = "setCharacteristicNotification: ";

        try {
            // Enable notify
            bluetoothGatt.setCharacteristicNotification(characteristic, enable);

            // Now that notify is enabled, it will have descriptor with handle 0x2902, so we need
            // to write BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE to it. First, convert 0x2902
            // to 128 bit UUID (00002902 + BASE-96 BLE UUID).
            UUID notifyDescriptorUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
            BluetoothGattDescriptor descriptor = characteristic.getDescriptor(notifyDescriptorUUID);
            descriptor.setValue(enable ? BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE : new byte[]{0x01,0x00});

            return bluetoothGatt.writeDescriptor(descriptor);
        } catch (Exception e) {
            logE(TAGG+"Exception caught: "+e.getMessage());
            return false;
        }
    }
    */


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
