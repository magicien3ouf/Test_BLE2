package com.example.test_ble;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Base64;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    public static final String EXTRA_COUNT = "com.example.test_ble.COUNT";

    private final static int REQUEST_ENABLE_BT = 1;

    // Bluetooth objects that we need to interact with

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothDevice mDevice = null;
    private BluetoothLeScanner mBLEScanner = null;

    // Bluetooth characteristics that we need to read/write

    private static BluetoothGattCharacteristic mCurrentCharacteristic;

    // UUIDs for the service and characteristics that the custom CapSenseLED service uses

    public final static UUID UUID_BLE_MIDI_SERVICE = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700");
    public final static UUID UUID_BLE_MIDI_CHARAC = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3");

    public final static UUID UUID_BLE_INFO_SERVICE = UUID.fromString("beabbac4-c45c-4795-b4d7-f929e6f2c16e");
    public final static UUID currentCharacteristicUUID = UUID.fromString("1028fd4e-e8de-11e9-81b4-2a2ae2dbcce4");
    public final static UUID tensionCharacteristicUUID = UUID.fromString("1028ff9c-e8de-11e9-81b4-2a2ae2dbcce4");
    public final static UUID tempCharacteristicUUID = UUID.fromString("102900f0-e8de-11e9-81b4-2a2ae2dbcce4");
    public final static UUID powerCharacteristicUUID = UUID.fromString("10290230-e8de-11e9-81b4-2a2ae2dbcce4");
    public final static UUID freqCharacteristicUUID = UUID.fromString("1029037a-e8de-11e9-81b4-2a2ae2dbcce4");

    // Actions used during broadcasts to the main activity

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    //This is required for Android 6.0 (Marshmallow)
    private static final int PERMISSION_REQUEST_COARSE_LOCATION = 1;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.bluetooth.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "com.example.bluetooth.le.ACTION_DATA_AVAILABLE";
    public final static String EXTRA_DATA =
            "com.example.bluetooth.le.EXTRA_DATA";
    public final static String ACTION_DATA_RECEIVED =
            "com.example.bluetooth.le.ACTION_DATA_RECEIVED";

    public int count = 0;
    public boolean isConnected = false;

    private Handler handler;

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            /* do what you need to do */
            count += 1;
            writeCharacteristic();

            TextView textCount = findViewById(R.id.textCount);
            textCount.setText(getString(R.string.textCount, count));
            readCharacteristic(currentCharacteristicUUID);
            readCharacteristic(tensionCharacteristicUUID);
            readCharacteristic(tempCharacteristicUUID);
            readCharacteristic(powerCharacteristicUUID);
            readCharacteristic(freqCharacteristicUUID);
            /* and here comes the "trick" */
            //Log.i(TAG, "count : " + count);
            handler.postDelayed(this, 100);
        }
    };

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                intentAction = ACTION_GATT_CONNECTED;
                // TODO
                Log.i(TAG, "Connected to GATT server.");
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Test service discovery:" +
                        mBluetoothGatt.discoverServices());                 //renvoie true si les services sont bien découverts

                BluetoothGattService mService = gatt.getService(UUID_BLE_INFO_SERVICE);

                Log.i(TAG, "Attempting to start service discovery:" +
                       mService);

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                // TODO
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                BluetoothGattService mService = gatt.getService(UUID_BLE_INFO_SERVICE);
                /* Get characteristics from our desired service */
                mCurrentCharacteristic  = mService.getCharacteristic(currentCharacteristicUUID);

                // Broadcast that service/characteristic/descriptor discovery is done
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);   // maybe to delete
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            Log.w(TAG, "onCharacteristicRead launched");
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "onServicesDiscovered launched with Gatt = " + gatt + " | char = " + characteristic.getUuid().toString() + " | status = " +status);
                String uuid = characteristic.getUuid().toString();

                if (uuid.equalsIgnoreCase(currentCharacteristicUUID.toString())) {
                    final int data = characteristic.getValue()[0]&0xFF;

                    TextView textCurrent_mA = findViewById(R.id.textCurrent_mA);
                    Log.w(TAG, "value read : " + data);
                    textCurrent_mA.setText( getString(R.string.textCurrent_mA, data));
                }
                else if (uuid.equalsIgnoreCase(tensionCharacteristicUUID.toString())) {
                    final int data = characteristic.getValue()[0]&0xFF;

                    TextView textTension_V = findViewById(R.id.textTension_V);
                    Log.w(TAG, "value read : " + data);
                    textTension_V.setText( getString(R.string.textTension_V, data));
                }
                else if (uuid.equalsIgnoreCase(tempCharacteristicUUID.toString())) {
                    final int data = characteristic.getValue()[0]&0xFF;

                    TextView textTemp_C = findViewById(R.id.textTemp_C);
                    Log.w(TAG, "value read : " + data);
                    textTemp_C.setText( getString(R.string.textTemp_C, data));
                }
                else if (uuid.equalsIgnoreCase(powerCharacteristicUUID.toString())) {
                    final int data = characteristic.getValue()[0]&0xFF;

                    TextView textPower_dBm = findViewById(R.id.textPower_dBm);
                    Log.w(TAG, "value read : " + data);
                    textPower_dBm.setText( getString(R.string.textTemp_C, data));
                }
                else if (uuid.equalsIgnoreCase(freqCharacteristicUUID.toString())) {
                    final int data = characteristic.getValue()[0]&0xFF;

                    TextView textFreq_Hz = findViewById(R.id.textFreq_Hz);
                    Log.w(TAG, "value read : " + data);
                    textFreq_Hz.setText( getString(R.string.textFreq_Hz, data));
                }
                // Notify the main activity that new data is available
                broadcastUpdate(ACTION_DATA_RECEIVED);
            }
            else{
                Log.i(TAG, "onCharacteristicRead received: " + status);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            // TODO
        }
        /**
         * Sends a broadcast to the listener in the main activity.
         *
         * @param action The type of action that occurred.
         */
        private void broadcastUpdate(final String action) {
            final Intent intent = new Intent(action);
            sendBroadcast(intent);
        }
    };


    @Override                                               //Useful to optimise bytecode (i.e. faster build)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //This section required for Android 6.0 (Marshmallow)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android M Permission check 
            if (this.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("This app needs location access ");
                builder.setMessage("Please grant location access so this app can detect devices.");
                builder.setPositiveButton(android.R.string.ok, null);
                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    public void onDismiss(DialogInterface dialog) {
                        requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST_COARSE_LOCATION);
                    }
                });
                builder.show();
            }
        } //End of section for Android 6.0 (Marshmallow)

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported",
                    Toast.LENGTH_SHORT).show();
            finish();
        }

        Intent myIntent = new Intent( Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        startActivity(myIntent);

        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Log.w(TAG, "Adapter created : " + mBluetoothAdapter);

        if (mBluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Log.w(TAG, "Device doesn't support Bluetooth");
        }

        if (!mBluetoothAdapter.isEnabled()) {
            Log.i(TAG, "Ask to enable bluetooth");
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mBLEScanner = mBluetoothAdapter.getBluetoothLeScanner();

        mBLEScanner.startScan(scanCallback);
        Log.i(TAG, "Scan started");
        handler = new Handler();
        runnable.run();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(runnable);
    }

    private ScanCallback scanCallback = new ScanCallback() { //TODO add log infos
        @Override
        public void onScanResult(int callbackType, ScanResult scanResult) {
            BluetoothDevice device = scanResult.getDevice();
            Log.e(TAG, "Scanned device " + device.getName() + "!");
            if(device.getName()==null){
                return;
            }
            else if ( device.getName().compareTo("psocmidi_alex") == 0 ) {
                Log.w(TAG, "entering inside");
                mBLEScanner.stopScan(scanCallback);
                connectDevice(device);
                Log.w(TAG, "we are trying to connect");
            }
        }
        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Scan error " + errorCode + "!");
        }
    };

    public void connectDevice(BluetoothDevice device) {
        if (device != null) {
            Log.i(TAG, "Device = " + device);
            mDevice = device;
            isConnected = true;
            Log.w(TAG, "we are connected !!!!!!!!!!");
        }
        if (mDevice == null) {
            return;
        }
        // connect
        mBluetoothGatt = mDevice.connectGatt(this, false, mGattCallback);
        String deviceName = mDevice.getName();
        String deviceHardwareAddress = mDevice.getAddress(); // MAC address
        final TextView helloTextView = (TextView) findViewById(R.id.Connexion_infos);     //link to the eponymous TextView defined in activity_main.xml
        helloTextView.setText("Name of the connected device : " + deviceName +         //name and address of the last paired device very useful I know
                "\nMAC address of the connected device : \n"
                + deviceHardwareAddress);
    }

    public boolean writeCharacteristic(){
        //check mBluetoothGatt is available
        if (mBluetoothGatt == null) {
            //Log.e(TAG, "lost connection");
            return false;
        }
        Log.i(TAG, "Write Char????");
        BluetoothGattService Service = mBluetoothGatt.getService(UUID_BLE_MIDI_SERVICE);
        if (Service == null) {
            Log.e(TAG, "service not found!");
            return false;
        }
        BluetoothGattCharacteristic charac = Service
                .getCharacteristic(UUID_BLE_MIDI_CHARAC);
        if (charac == null) {
            Log.e(TAG, "char not found!");
            return false;
        }

        byte[] value = new byte[4];
        int valueHex = 0xFFFFFFFF;      //byte we want to send according to MIDI protocol (just to test, to change afterwards)
        value[0] = (byte) (valueHex & 0xFF);
        value[1] = (byte) ((valueHex >> 8) & 0xFF);
        value[2] = (byte) ((valueHex >> 16) & 0xFF);
        value[3] = (byte) ((valueHex >> 24) & 0xFF);
//        value[4] = (byte) ((valueHex >> 32) & 0xFF);
//        value[5] = (byte) ((valueHex >> 40) & 0xFF);

        charac.setValue(value);
        boolean status = mBluetoothGatt.writeCharacteristic(charac);
        return status;
    }

    /**
     * This method is used to read the state of the current from the device
     */
    public boolean readCharacteristic(UUID CharacUUID) {
        //check mBluetoothGatt is available
        if (mBluetoothGatt == null) {
            //Log.e(TAG, "lost connection");
            return false;
        }

        BluetoothGattService mService = mBluetoothGatt.getService(UUID_BLE_INFO_SERVICE);
        if (mService == null) {
            Log.e(TAG, "service not found!");
            return false;
        }

        BluetoothGattCharacteristic charac = mService.getCharacteristic(CharacUUID);
        if (charac == null) {
            Log.e(TAG, "char not found!");
            return false;
        }

        boolean status = mBluetoothGatt.readCharacteristic(charac);
        return status;
    }


//    public void updateTextView(textView, String toThis) {
//        TextView textView = (TextView) findViewById(R.id.textView);
//        textView.setText(toThis);
//    }
}