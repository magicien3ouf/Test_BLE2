package com.example.test_ble;

import androidx.appcompat.app.AppCompatActivity;

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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;

import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final static String TAG = MainActivity.class.getSimpleName();

    public static final String EXTRA_MESSAGE = "com.example.test_ble.MESSAGE";    //text displayed when the button is clicked
    public static final String EXTRA_COUNT = "com.example.test_ble.COUNT";

    private final static int REQUEST_ENABLE_BT = 1;


    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private String mBluetoothDeviceAddress;
    private BluetoothGatt mBluetoothGatt = null;
    private BluetoothDevice mDevice = null;
    private BluetoothLeScanner mBLEScanner = null;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

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

    public final static UUID UUID_BLE_MIDI_SERVICE = UUID.fromString("03B80E5A-EDE8-4B33-A751-6CE34EC4C700");
    public final static UUID UUID_BLE_MIDI_CHARAC = UUID.fromString("7772E5DB-3868-4112-A1A9-F2669D106BF3");


    public int count = 0;

    private Handler handler;

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            /* do what you need to do */
            count += 1;
            writeCharacteristic();
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

                Log.i(TAG, "Attempting to start service discovery:" +
                        mBluetoothGatt.getServices());

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                Log.i(TAG, "Disconnected from GATT server.");
                // TODO
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // TODO
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // TODO
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            // TODO
        }
    };


    @Override                                               //Useful to optimise bytecode (i.e. faster build)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        mBLEScanner = bluetoothAdapter.getBluetoothLeScanner();

        mBLEScanner.startScan(scanCallback);

/*
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        BluetoothDevice mDevice = null;
        if (pairedDevices.size() > 0) {
            // There are paired devices. Get the name and address of each paired device.
            for (BluetoothDevice device : pairedDevices) {
                Log.w(TAG, "Found device: " + device.getName());
                if (device.getAddress().equals("00:A0:50:B7:8A:A5")) {
                    mDevice = device;
                    break;
                }
            }
        }*/
        handler = new Handler();
        runnable.run();
    }

    /** Called when the user taps the Send button */
    public void sendMessage(View view) {
        // Do something in response to button
        Intent intent = new Intent(this, DisplayMessageActivity.class);
        EditText editText = (EditText) findViewById(R.id.editText);
        String message = editText.getText().toString();
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_COUNT, count);
        startActivity(intent);
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
            if (device.getName() == "psocmidi") {
                mBLEScanner.stopScan(scanCallback);
                connectDevice(device);
            }
        }
    };

    public void connectDevice(BluetoothDevice device) {
        if (device != null) {
            mDevice = device;
        }
        if (mDevice == null) {
            return;
        }
        // connect
        mBluetoothGatt = mDevice.connectGatt(this, false, mGattCallback);
        String deviceName = mDevice.getName();
        String deviceHardwareAddress = mDevice.getAddress(); // MAC address
        final TextView helloTextView = (TextView) findViewById(R.id.text_view_id);     //link to the eponymous TextView defined in activity_main.xml
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

        byte[] value = new byte[1];
        value[0] = (byte) (21 & 0xFF);      //byte we want to send according to MIDI protocol (just to test, to change afterwards)
        charac.setValue(value);
        boolean status = mBluetoothGatt.writeCharacteristic(charac);
        return status;
    }
}




