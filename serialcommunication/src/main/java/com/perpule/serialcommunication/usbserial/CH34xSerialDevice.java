/*
 * Based in the CH340x driver made by Andreas Butti (https://github
 * .com/mik3y/usb-serial-for-android/blob/master/usbSerialForAndroid/src/main/java/com/hoho/android/usbserial/driver/Ch34xSerialDriver.java)
 * Thanks to Paul Alcock for provide me with one of those Arduino nano clones!!!
 * Also thanks to Lex Wernars for send me a CH340 that didnt work with the former version of this code!!
 * */

package com.perpule.serialcommunication.usbserial;

import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbRequest;

import java.util.concurrent.atomic.AtomicBoolean;

public class CH34xSerialDevice extends UsbSerialDevice {

    private static final int REQTYPE_HOST_FROM_DEVICE = UsbConstants.USB_TYPE_VENDOR | UsbConstants.USB_DIR_IN;
    private static final int REQTYPE_HOST_TO_DEVICE = 0x40;

    private static final int CH341_REQ_WRITE_REG = 0x9A;
    private static final int CH341_REQ_READ_REG = 0x95;

    // Parity values
    private static final int CH34X_PARITY_NONE = 0xc3;
    private static final int CH34X_PARITY_ODD = 0xcb;
    private static final int CH34X_PARITY_EVEN = 0xdb;
    private static final int CH34X_PARITY_MARK = 0xeb;
    private static final int CH34X_PARITY_SPACE = 0xfb;

    //Flow control values
    private static final int CH34X_FLOW_CONTROL_NONE = 0x0000;
    private static final int CH34X_FLOW_CONTROL_RTS_CTS = 0x0101;
    private static final int CH34X_FLOW_CONTROL_DSR_DTR = 0x0202;
    // XON/XOFF doesnt appear to be supported directly from hardware

    private UsbInterface mInterface;
    private UsbEndpoint inEndpoint;
    private UsbEndpoint outEndpoint;
    private UsbRequest requestIN;

    private FlowControlThread flowControlThread;
    private UsbCTSCallback ctsCallback;
    private UsbDSRCallback dsrCallback;
    private boolean rtsCtsEnabled;
    private boolean dtrDsrEnabled;
    private boolean dtr = false;
    private boolean rts = false;
    private boolean ctsState = false;
    private boolean dsrState = false;

    public CH34xSerialDevice(UsbDevice device, UsbDeviceConnection connection) {
        super(device, connection);
    }

    CH34xSerialDevice(UsbDevice device, UsbDeviceConnection connection, int iface) {
        super(device, connection);
        rtsCtsEnabled = false;
        dtrDsrEnabled = false;
        mInterface = device.getInterface(iface >= 0 ? iface : 0);
    }

    @Override
    public boolean open() {
        boolean ret = openCH34X();
        if (ret) {
            // Initialize UsbRequest
            requestIN = new UsbRequest();
            requestIN.initialize(connection, inEndpoint);

            // Restart the working thread if it has been killed before and  get and claim interface
            restartWorkingThread();
            restartWriteThread();

            // Create Flow control thread but it will only be started if necessary
            createFlowControlThread();

            // Pass references to the threads
            setThreadsParams(requestIN, outEndpoint);

            asyncMode = true;

            return true;
        } else {
            return false;
        }
    }

    @Override
    public void close() {
        killWorkingThread();
        killWriteThread();
        stopFlowControlThread();
        connection.releaseInterface(mInterface);
    }

    @Override
    public boolean syncOpen() {
        boolean ret = openCH34X();
        if (ret) {
            // Create Flow control thread but it will only be started if necessary
            createFlowControlThread();
            setSyncParams(inEndpoint, outEndpoint);
            asyncMode = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void syncClose() {
        stopFlowControlThread();
        connection.releaseInterface(mInterface);
    }

    @Override
    public void setBaudRate(int baudRate) {
    }

    @Override
    public void setDataBits(int dataBits) {
    }

    @Override
    public void setStopBits(int stopBits) {
    }

    @Override
    public void setParity(int parity) {
        switch (parity) {
            case UsbSerialInterface.PARITY_NONE:
                setCh340xParity(CH34X_PARITY_NONE);
                break;
            case UsbSerialInterface.PARITY_ODD:
                setCh340xParity(CH34X_PARITY_ODD);
                break;
            case UsbSerialInterface.PARITY_EVEN:
                setCh340xParity(CH34X_PARITY_EVEN);
                break;
            case UsbSerialInterface.PARITY_MARK:
                setCh340xParity(CH34X_PARITY_MARK);
                break;
            case UsbSerialInterface.PARITY_SPACE:
                setCh340xParity(CH34X_PARITY_SPACE);
                break;
            default:
                break;
        }
    }

    @Override
    public void setFlowControl(int flowControl) {
        switch (flowControl) {
            case UsbSerialInterface.FLOW_CONTROL_OFF:
                rtsCtsEnabled = false;
                dtrDsrEnabled = false;
                setCh340xFlow(CH34X_FLOW_CONTROL_NONE);
                break;
            case UsbSerialInterface.FLOW_CONTROL_RTS_CTS:
                rtsCtsEnabled = true;
                dtrDsrEnabled = false;
                setCh340xFlow(CH34X_FLOW_CONTROL_RTS_CTS);
                ctsState = checkCTS();
                startFlowControlThread();
                break;
            case UsbSerialInterface.FLOW_CONTROL_DSR_DTR:
                rtsCtsEnabled = false;
                dtrDsrEnabled = true;
                setCh340xFlow(CH34X_FLOW_CONTROL_DSR_DTR);
                dsrState = checkDSR();
                startFlowControlThread();
                break;
            default:
                break;
        }
    }

    @Override
    public void setRTS(boolean state) {
        rts = state;
        writeHandshakeByte();
    }

    @Override
    public void setDTR(boolean state) {
        dtr = state;
        writeHandshakeByte();
    }

    @Override
    public void getCTS(UsbCTSCallback ctsCallback) {
        this.ctsCallback = ctsCallback;
    }

    @Override
    public void getDSR(UsbDSRCallback dsrCallback) {
        this.dsrCallback = dsrCallback;
    }

    @Override
    public void getBreak(UsbBreakCallback breakCallback) {
        //TODO
    }

    @Override
    public void getFrame(UsbFrameCallback frameCallback) {
        //TODO
    }

    @Override
    public void getOverrun(UsbOverrunCallback overrunCallback) {
        //TODO
    }

    @Override
    public void getParity(UsbParityCallback parityCallback) {
        //TODO
    }

    private boolean openCH34X() {
        if (!connection.claimInterface(mInterface, true)) {
            return false;
        }

        // Assign endpoints
        int numberEndpoints = mInterface.getEndpointCount();
        for (int i = 0; i <= numberEndpoints - 1; i++) {
            UsbEndpoint endpoint = mInterface.getEndpoint(i);
            if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_IN) {
                inEndpoint = endpoint;
            } else if (endpoint.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK
                    && endpoint.getDirection() == UsbConstants.USB_DIR_OUT) {
                outEndpoint = endpoint;
            }
        }

        return init() == 0;
    }

    private int init() {
        /*
            Init the device at 9600 bauds
         */

        if (setControlCommandOut(0xa1, 0xc29c, 0xb2b9, null) < 0) {
            return -1;
        }

        if (setControlCommandOut(0xa4, 0xdf, 0, null) < 0) {
            return -1;
        }

        if (setControlCommandOut(0xa4, 0x9f, 0, null) < 0) {
            return -1;
        }

        if (checkState("init #4", 0x95, 0x0706, new int[]{0x9f, 0xee}) == -1)
            return -1;

        if (setControlCommandOut(0x9a, 0x2727, 0x0000, null) < 0) {
            return -1;
        }

        if (setControlCommandOut(0x9a, 0x1312, 0xb282, null) < 0) {
            return -1;
        }

        if (setControlCommandOut(0x9a, 0x0f2c, 0x0008, null) < 0) {
            return -1;
        }

        if (setControlCommandOut(0x9a, 0x2518, 0x00c3, null) < 0) {
            return -1;
        }

        if (checkState("init #9", 0x95, 0x0706, new int[]{0x9f, 0xee}) == -1)
            return -1;

        if (setControlCommandOut(0x9a, 0x2727, 0x0000, null) < 0) {
            return -1;
        }

        return 0;
    }

    private int setCh340xParity(int indexParity) {
        if (setControlCommandOut(CH341_REQ_WRITE_REG, 0x2518, indexParity, null) < 0)
            return -1;
        if (checkState("set_parity", 0x95, 0x0706, new int[]{0x9f, 0xee}) == -1)
            return -1;
        if (setControlCommandOut(CH341_REQ_WRITE_REG, 0x2727, 0, null) < 0)
            return -1;
        return 0;
    }

    private int setCh340xFlow(int flowControl) {
        if (checkState("set_flow_control", 0x95, 0x0706, new int[]{0x9f, 0xee}) == -1)
            return -1;
        if (setControlCommandOut(CH341_REQ_WRITE_REG, 0x2727, flowControl, null) == -1)
            return -1;
        return 0;
    }

    private int checkState(String msg, int request, int value, int[] expected) {
        byte[] buffer = new byte[expected.length];
        int ret = setControlCommandIn(request, value, 0, buffer);

        if (ret != expected.length) {
            return -1;
        } else {
            return 0;
        }
    }

    private boolean checkCTS() {
        byte[] buffer = new byte[2];
        int ret = setControlCommandIn(CH341_REQ_READ_REG, 0x0706, 0, buffer);

        if (ret != 2) {
            return false;
        }

        return (buffer[0] & 0x01) == 0x00;
    }

    private boolean checkDSR() {
        byte[] buffer = new byte[2];
        int ret = setControlCommandIn(CH341_REQ_READ_REG, 0x0706, 0, buffer);

        if (ret != 2) {
            return false;
        }

        return (buffer[0] & 0x02) == 0x00;
    }

    private int writeHandshakeByte() {
        if (setControlCommandOut(0xa4, ~((dtr ? 1 << 5 : 0) | (rts ? 1 << 6 : 0)), 0, null) < 0) {
            return -1;
        }
        return 0;
    }

    private int setControlCommandOut(int request, int value, int index, byte[] data) {
        int dataLength = 0;
        if (data != null) {
            dataLength = data.length;
        }
        return connection.controlTransfer(REQTYPE_HOST_TO_DEVICE, request, value, index, data, dataLength, USB_TIMEOUT);
    }

    private int setControlCommandIn(int request, int value, int index, byte[] data) {
        int dataLength = 0;
        if (data != null) {
            dataLength = data.length;
        }
        return connection.controlTransfer(REQTYPE_HOST_FROM_DEVICE, request, value, index, data, dataLength, USB_TIMEOUT);
    }

    private void createFlowControlThread() {
        flowControlThread = new FlowControlThread();
    }

    private void startFlowControlThread() {
        if (!flowControlThread.isAlive())
            flowControlThread.start();
    }

    private void stopFlowControlThread() {
        if (flowControlThread != null) {
            flowControlThread.stopThread();
            flowControlThread = null;
        }
    }

    private class FlowControlThread extends Thread {
        private long time = 100; // 100ms

        private boolean firstTime;

        private AtomicBoolean keep;

        FlowControlThread() {
            keep = new AtomicBoolean(true);
            firstTime = true;
        }

        @Override
        public void run() {
            while (keep.get()) {
                if (!firstTime) {
                    // Check CTS status
                    if (rtsCtsEnabled) {
                        boolean cts = pollForCTS();
                        if (ctsState != cts) {
                            ctsState = !ctsState;
                            if (ctsCallback != null)
                                ctsCallback.onCTSChanged(ctsState);
                        }
                    }

                    // Check DSR status
                    if (dtrDsrEnabled) {
                        boolean dsr = pollForDSR();
                        if (dsrState != dsr) {
                            dsrState = !dsrState;
                            if (dsrCallback != null)
                                dsrCallback.onDSRChanged(dsrState);
                        }
                    }
                } else {
                    if (rtsCtsEnabled && ctsCallback != null)
                        ctsCallback.onCTSChanged(ctsState);

                    if (dtrDsrEnabled && dsrCallback != null)
                        dsrCallback.onDSRChanged(dsrState);

                    firstTime = false;
                }
            }
        }

        void stopThread() {
            keep.set(false);
        }

        boolean pollForCTS() {
            synchronized (this) {
                try {
                    wait(time);
                } catch (InterruptedException ignored) {
                }
            }

            return checkCTS();
        }

        boolean pollForDSR() {
            synchronized (this) {
                try {
                    wait(time);
                } catch (InterruptedException ignored) {
                }
            }

            return checkDSR();
        }
    }
}
