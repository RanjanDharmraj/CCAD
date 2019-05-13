package com.perpule.ccad;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.perpule.serialcommunication.service.UsbService;
import com.perpule.serialcommunication.usbserial.UsbSerialInterface;


public class MainActivity extends AppCompatActivity implements UsbService.UsbServiceReadCallBack {

    public UsbService usbService;
    public Button btnCashAcceptorConnect, btnCashAcceptorDisconnect,
            btnDespenceNoteConnect, btnDespenceNoteDisconnect,
            btnDespenceCoinConnect, btnDespenceCoinDisconnect;
    private BroadcastReceiver mUsbReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mUsbReceiver = new UsbBroadcastReceiver();
        initView();
    }

    private void initView() {
        btnCashAcceptorConnect = findViewById(R.id.button1);
        btnCashAcceptorDisconnect = findViewById(R.id.button2);
        btnDespenceNoteConnect = findViewById(R.id.button3);
        btnDespenceNoteDisconnect = findViewById(R.id.button4);
        btnDespenceCoinConnect = findViewById(R.id.button5);
        btnDespenceCoinDisconnect = findViewById(R.id.button6);

        btnCashAcceptorConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usbService.setParity(UsbSerialInterface.PARITY_EVEN);
                usbService.startThread();
                usbService.write(new byte[]{0x30});
            }
        });

        btnCashAcceptorDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usbService.write(new byte[]{0x5E});
            }
        });

        btnDespenceNoteConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usbService.setParity(UsbSerialInterface.PARITY_NONE);
                usbService.startThread();
                int checksum = 4^80^2^69^48^49^3;
                usbService.write(new byte[]{4,80,2,69,48,49,3, (byte) checksum});

            }
        });

        btnDespenceNoteDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });

        btnDespenceCoinConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                usbService.setParity(UsbSerialInterface.PARITY_NONE);
                usbService.startThread();
                byte checkSum = calcChecksum(new byte[]{0x05,0x10,0x00,0x11,0x00});
                usbService.write(new byte[]{0x05,0x10,0x00,0x11,0x00,checkSum});

                byte checkSum1 = calcChecksum(new byte[]{0x05,0x10,0x00,0x14,0x0A});
                usbService.write(new byte[]{0x05,0x10,0x00,0x14,0x0A,checkSum1});
            }
        });

        btnDespenceCoinDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

            }
        });
    }

    private byte calcChecksum(byte[] data) {
        byte sum = 0;
        for (byte b : data) {
            sum += b;
        }
        return sum;
    }

    @Override
    protected void onStart() {
        super.onStart();
        setFilters();  // Start listening notifications from UsbService
        startService(UsbService.class, usbConnection); // Start UsbService(if it was not started before) and Bind it
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mUsbReceiver);
    }

    private class UsbBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getAction()) {
                    case UsbService.ACTION_USB_PERMISSION_GRANTED: // USB PERMISSION GRANTED
                        Toast.makeText(context, "USB Ready", Toast.LENGTH_SHORT).show();
                        break;
                    case UsbService.ACTION_USB_PERMISSION_NOT_GRANTED: // USB PERMISSION NOT GRANTED
                        Toast.makeText(context, "USB Permission not granted", Toast.LENGTH_SHORT).show();
                        break;
                    case UsbService.ACTION_NO_USB: // NO USB CONNECTED
                        Toast.makeText(context, "No USB connected", Toast.LENGTH_SHORT).show();
                        break;
                    case UsbService.ACTION_USB_DISCONNECTED: // USB DISCONNECTED
                        Toast.makeText(context, "USB disconnected", Toast.LENGTH_SHORT).show();
                        break;
                    case UsbService.ACTION_USB_NOT_SUPPORTED: // USB NOT SUPPORTED
                        Toast.makeText(context, "USB device not supported", Toast.LENGTH_SHORT).show();
                        break;
                }
            }
        }
    }


    private final ServiceConnection usbConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName arg0, IBinder arg1) {
            usbService = ((UsbService.UsbBinder) arg1).getService();
            usbService.setUsbServiceReadCallBack(MainActivity.this);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            usbService.setUsbServiceReadCallBack(null);
            usbService = null;
        }
    };

    private void setFilters() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbService.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbService.ACTION_NO_USB);
        filter.addAction(UsbService.ACTION_USB_DISCONNECTED);
        filter.addAction(UsbService.ACTION_USB_NOT_SUPPORTED);
        filter.addAction(UsbService.ACTION_USB_PERMISSION_NOT_GRANTED);
        registerReceiver(mUsbReceiver, filter);
    }

    private void startService(Class<?> service, ServiceConnection serviceConnection) {
        if (!UsbService.SERVICE_CONNECTED) {
            Intent startService = new Intent(this, service);
            startService(startService);
        }
        Intent bindingIntent = new Intent(this, service);
        bindService(bindingIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onReceivedData(String data) {

    }
}
