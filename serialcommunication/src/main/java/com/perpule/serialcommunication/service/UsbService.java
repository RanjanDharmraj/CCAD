package com.perpule.serialcommunication.service;

import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import com.perpule.serialcommunication.usbserial.CDCSerialDevice;
import com.perpule.serialcommunication.usbserial.UsbSerialDevice;
import com.perpule.serialcommunication.usbserial.UsbSerialInterface;

import java.util.HashMap;
import java.util.Map;

public class UsbService extends Service {

    public static final String ACTION_USB_READY = "com.perpule.vpos.connectivityservices.USB_READY";
    public static final String ACTION_USB_ATTACHED = "android.hardware.usb.action.USB_DEVICE_ATTACHED";
    public static final String ACTION_USB_DETACHED = "android.hardware.usb.action.USB_DEVICE_DETACHED";
    public static final String ACTION_USB_NOT_SUPPORTED = "com.perpule.vpos.usbservice.USB_NOT_SUPPORTED";
    public static final String ACTION_NO_USB = "com.perpule.vposr.usbservice.NO_USB";
    public static final String ACTION_USB_PERMISSION_GRANTED = "com.perpule.vpos.usbservice.USB_PERMISSION_GRANTED";
    public static final String ACTION_USB_PERMISSION_NOT_GRANTED = "com.perpule.vpos.usbservice.USB_PERMISSION_NOT_GRANTED";
    public static final String ACTION_USB_DISCONNECTED = "com.perpule.vpos.usbservice.USB_DISCONNECTED";
    public static final String ACTION_CDC_DRIVER_NOT_WORKING = "com.perpule.vpos.connectivityservices.ACTION_CDC_DRIVER_NOT_WORKING";
    public static final String ACTION_USB_DEVICE_NOT_WORKING = "com.perpule.vpos.connectivityservices.ACTION_USB_DEVICE_NOT_WORKING";
    public static final int MESSAGE_FROM_SERIAL_PORT = 0;
    public static final int CTS_CHANGE = 1;
    public static final int DSR_CHANGE = 2;
    private static final String ACTION_USB_PERMISSION = "com.perpule.vpos.USB_PERMISSION";
    private static final int BAUD_RATE = 9600; // BaudRate. Change this value if you need
    public static boolean SERVICE_CONNECTED = false;

    private IBinder binder = new UsbBinder();

    private Context context;
    private Handler mHandler;
    private UsbManager usbManager;
    private UsbDevice device;
    private UsbDeviceConnection connection;
    private UsbSerialDevice serialPort;
    private UsbServiceReadCallBack usbServiceReadCallBack;
    private int parity;

    private boolean serialPortConnected;

    public void setParity(int parity) {
        this.parity = parity;
    }

    public void startThread() {
        new ConnectionThread().start();
    }

    /*
     *  Data received from serial port will be received here. Just populate onReceivedData with your code
     *  In this particular example. byte stream is converted to String and send to UI thread to
     *  be treated there.
     */

    public interface UsbServiceReadCallBack {
        void onReceivedData(String data);
    }

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {
        @Override
        public void onReceivedData(byte[] arg0) {
            try {
                String data = bytesToHex(arg0);
                if(data.equals("80")) {
                    write(new byte[] { 0x2 });
                } else if (data.equals("5E")) {
                    write(new byte[] { 0x3E });
                } else if(data.equals("41")){
                    write(new byte[] { 0x2 });
                } else if(data.equals("42")) {
                    write(new byte[] { 0x2 });
                } else if(data.equals("62")) {

                } else if( data.equals("65")) {

                } else if( data.equals("72")) {

                } else if( data.equals("73")) {

                } else if( data.equals("74")) {

                } else if( data.equals("77")) {

                } else if( data.equals("5")) {

                } else if( data.equals("45")) {

                } else if( data.equals("10")) {

                } else if( data.equals("29")) {

                } else if( data.equals("2F")) {

                }
                Log.d("Recieved Data", data);
            } catch (Exception ignored) {
            }
        }
    };

    private static String bytesToHex(byte[] hashInBytes) {

        StringBuilder sb = new StringBuilder();
        for (byte b : hashInBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();

    }

    /*
     * State changes in the CTS line will be received here
     */
    private UsbSerialInterface.UsbCTSCallback ctsCallback = new UsbSerialInterface.UsbCTSCallback() {
        @Override
        public void onCTSChanged(boolean state) {
            if (mHandler != null)
                mHandler.obtainMessage(CTS_CHANGE).sendToTarget();
        }
    };

    /*
     * State changes in the DSR line will be received here
     */
    private UsbSerialInterface.UsbDSRCallback dsrCallback = new UsbSerialInterface.UsbDSRCallback() {
        @Override
        public void onDSRChanged(boolean state) {
            if (mHandler != null)
                mHandler.obtainMessage(DSR_CHANGE).sendToTarget();
        }
    };
    /*
     * Different notifications from OS will be received here (USB attached, detached, permission responses...)
     * About BroadcastReceiver: http://developer.android.com/reference/android/content/BroadcastReceiver.html
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intentReceive) {
            if (intentReceive.getAction() != null) {
                switch (intentReceive.getAction()) {
                    case ACTION_USB_PERMISSION:
                        boolean granted = intentReceive.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                        if (granted) // User accepted our USB connection. Try to open the device as a serial port
                        {
                            Intent intent = new Intent(ACTION_USB_PERMISSION_GRANTED);
                            context.sendBroadcast(intent);
                            connection = usbManager.openDevice(device);
                        } else // User not accepted our USB connection. Send an Intent to the Main Activity
                        {
                            Intent intent = new Intent(ACTION_USB_PERMISSION_NOT_GRANTED);
                            context.sendBroadcast(intent);
                        }
                        break;
                    case ACTION_USB_ATTACHED:
                        if (!serialPortConnected)
                            findSerialPortDevice(); // A USB device has been attached. Try to open it as a Serial port
                        break;
                    case ACTION_USB_DETACHED:
                        // Usb device was disconnected. send an intent to the Main Activity
                        Intent intent = new Intent(ACTION_USB_DISCONNECTED);
                        context.sendBroadcast(intent);
                        if (serialPortConnected) {
                            serialPort.close();
                        }
                        serialPortConnected = false;
                        break;
                }
            }
        }
    };

    /*
     * onCreate will be executed when service is started. It configures an IntentFilter to listen for
     * incoming Intents (USB ATTACHED, USB DETACHED...) and it tries to open a serial port.
     */
    @Override
    public void onCreate() {
        this.context = this;
        serialPortConnected = false;
        UsbService.SERVICE_CONNECTED = true;
        setFilter();
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        findSerialPortDevice();
    }

    /* MUST READ about services
     * http://developer.android.com/guide/components/services.html
     * http://developer.android.com/guide/components/bound-services.html
     */
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        UsbService.SERVICE_CONNECTED = false;
    }

    /*
     * This function will be called from MainActivity to write data through Serial Port
     */
    public void write(byte[] data) {
        Log.i("Serial Port", String.valueOf(serialPort));
        if (serialPort != null)
            serialPort.write(data);
    }

    public void setHandler(Handler mHandler) {
        this.mHandler = mHandler;
    }


    public void setUsbServiceReadCallBack(UsbServiceReadCallBack usbServiceReadCallBack) {
        this.usbServiceReadCallBack = usbServiceReadCallBack;
    }

    private void findSerialPortDevice() {
        // This snippet will try to open the first encountered usb device connected, excluding usb root hubs
        HashMap<String, UsbDevice> usbDevices = usbManager.getDeviceList();
        if (!usbDevices.isEmpty()) {
            boolean keep = true;
            for (Map.Entry<String, UsbDevice> entry : usbDevices.entrySet()) {
                device = entry.getValue();
                int deviceVID = device.getVendorId();
                int devicePID = device.getProductId();

                if (deviceVID != 0x1d6b && (devicePID != 0x0001 && devicePID != 0x0002 && devicePID != 0x0003) && deviceVID != 0x5c6 && devicePID
                        != 0x904c) {

                    // There is a device connected to our Android device. Try to open it as a Serial Port.
                    requestUserPermission();
                    keep = false;
                } else {
                    connection = null;
                    device = null;
                }

                if (!keep)
                    break;
            }
            if (!keep) {
                // There is no USB devices connected (but usb host were listed). Send an intent to MainActivity.
                Intent intent = new Intent(ACTION_NO_USB);
                sendBroadcast(intent);
            }
        } else {
            // There is no USB devices connected. Send an intent to MainActivity
            Intent intent = new Intent(ACTION_NO_USB);
            sendBroadcast(intent);
        }
    }

    private void setFilter() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(ACTION_USB_DETACHED);
        filter.addAction(ACTION_USB_ATTACHED);
        registerReceiver(usbReceiver, filter);
    }

    /*
     * Request user permission. The response will be received in the BroadcastReceiver
     */
    private void requestUserPermission() {
        PendingIntent mPendingIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        usbManager.requestPermission(device, mPendingIntent);
    }

    public class UsbBinder extends Binder {
        public UsbService getService() {
            return UsbService.this;
        }
    }

    /*
     * A simple thread to open a serial port.
     * Although it should be a fast operation. moving usb operations away from UI thread is a good thing.
     */
    private class ConnectionThread extends Thread {
        @Override
        public void run() {
            serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
            if (serialPort != null) {
                if (serialPort.open()) {
                    serialPortConnected = true;
                    serialPort.setBaudRate(BAUD_RATE);
                    serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                    serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                    serialPort.setParity(parity);
                    serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                    serialPort.read(mCallback);
                    serialPort.getCTS(ctsCallback);
                    serialPort.getDSR(dsrCallback);

                    //
                    // Some Arduinos would need some sleep because firmware wait some time to know whether a new sketch is going
                    // to be uploaded or not
                    //Thread.sleep(2000); // sleep some. YMMV with different chips.

                    // Everything went as expected. Send an intent to MainActivity
                    Intent intent = new Intent(ACTION_USB_READY);
                    context.sendBroadcast(intent);
                } else {
                    // Serial port could not be opened, maybe an I/O error or if CDC driver was chosen, it does not really fit
                    // Send an Intent to Main Activity
                    if (serialPort instanceof CDCSerialDevice) {
                        Intent intent = new Intent(ACTION_CDC_DRIVER_NOT_WORKING);
                        context.sendBroadcast(intent);
                    } else {
                        Intent intent = new Intent(ACTION_USB_DEVICE_NOT_WORKING);
                        context.sendBroadcast(intent);
                    }
                }
            } else {
                // No driver for given device, even generic CDC driver could not be loaded
                Intent intent = new Intent(ACTION_USB_NOT_SUPPORTED);
                context.sendBroadcast(intent);
            }
        }
    }
}
