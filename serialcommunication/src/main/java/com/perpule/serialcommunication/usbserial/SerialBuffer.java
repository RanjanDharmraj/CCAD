package com.perpule.serialcommunication.usbserial;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class SerialBuffer {
    static final int DEFAULT_READ_BUFFER_SIZE = 4 * 1024;
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 16 * 1024;
    private ByteBuffer readBuffer;
    private SynchronizedBuffer writeBuffer;
    private byte[] readBuffer_compatible; // Read buffer for android < 4.2

    SerialBuffer(boolean version) {
        writeBuffer = new SynchronizedBuffer();
        if (version) {
            readBuffer = ByteBuffer.allocate(DEFAULT_READ_BUFFER_SIZE);

        } else {
            readBuffer_compatible = new byte[DEFAULT_READ_BUFFER_SIZE];
        }
    }

    /*
     * Print debug messages
     */
    public void debug(boolean value) {
    }

    public void putReadBuffer(ByteBuffer data) {
        synchronized (this) {
            try {
                readBuffer.put(data);
            } catch (BufferOverflowException e) {
                // TO-DO
            }
        }
    }

    ByteBuffer getReadBuffer() {
        synchronized (this) {
            return readBuffer;
        }
    }

    public byte[] getDataReceived() {
        synchronized (this) {
            byte[] dst = new byte[readBuffer.position()];
            readBuffer.position(0);
            readBuffer.get(dst, 0, dst.length);
            return dst;
        }
    }

    void clearReadBuffer() {
        synchronized (this) {
            readBuffer.clear();
        }
    }

    byte[] getWriteBuffer() {
        return writeBuffer.get();
    }

    void putWriteBuffer(byte[] data) {
        writeBuffer.put(data);
    }

    void resetWriteBuffer() {
        writeBuffer.reset();
    }

    byte[] getBufferCompatible() {
        return readBuffer_compatible;
    }

    byte[] getDataReceivedCompatible(int numberBytes) {
        return Arrays.copyOfRange(readBuffer_compatible, 0, numberBytes);
    }

    private class SynchronizedBuffer {
        private byte[] buffer;
        private int position;

        SynchronizedBuffer() {
            this.buffer = new byte[DEFAULT_WRITE_BUFFER_SIZE];
            position = -1;
        }

        public synchronized void put(byte[] src) {
            if (src == null || src.length == 0) return;
            if (position == -1)
                position = 0;
            if (position + src.length > DEFAULT_WRITE_BUFFER_SIZE - 1) //Checking bounds. Source data does not fit in buffer
            {
                if (position < DEFAULT_WRITE_BUFFER_SIZE)
                    System.arraycopy(src, 0, buffer, position, DEFAULT_WRITE_BUFFER_SIZE - position);
                position = DEFAULT_WRITE_BUFFER_SIZE;
                notify();
            } else // Source data fits in buffer
            {
                System.arraycopy(src, 0, buffer, position, src.length);
                position += src.length;
                notify();
            }
        }

        public synchronized byte[] get() {
            if (position == -1) {
                try {
                    wait();
                } catch (InterruptedException ignored) {
                }
            }
            if (position <= -1) return new byte[0];
            byte[] dst = Arrays.copyOfRange(buffer, 0, position);
            position = -1;
            return dst;
        }

        public synchronized void reset() {
            position = -1;
        }
    }

}
