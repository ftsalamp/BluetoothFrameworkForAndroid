package grioanpier.auth.users.bluetoothframework.Loaders;

import android.bluetooth.BluetoothSocket;
import android.os.Handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A {@link Thread} that keeps the bluetooth connection with another device.
 * It's also the point of communication.
 */
public class ConnectedThread extends Thread {
    private static final String LOG_TAG = ConnectedThread.class.getSimpleName();

    private final BluetoothSocket mSocket;
    private final InputStream mInStream;
    private final OutputStream mOutStream;
    private final Handler mHandler;
    public final int ID = hashCode();

    private static final int MESSAGE_SIZE = 1024;
    public static final int THREAD_READ = 0;
    public static final int THREAD_DISCONNECTED = 1;
    public static final int THREAD_STREAM_ERROR = 42;

    private boolean isActive;

    public<T extends Handler> ConnectedThread(BluetoothSocket socket, T handler) {
        mSocket = socket;
        InputStream tempIn = null;
        OutputStream tempOut = null;
        mHandler = handler;
        isActive=true;

        try {
            tempIn = socket.getInputStream();
            tempOut = socket.getOutputStream();
        } catch (IOException e) {
            mHandler.obtainMessage(THREAD_STREAM_ERROR).sendToTarget();
        }

        mInStream = tempIn;
        mOutStream = tempOut;
    }

    public void run(){
        byte[] buffer = new byte[MESSAGE_SIZE];
        int numOfBytes;

        // Keep listening to the InputStream while connected
        while (isActive) {
            try {
                numOfBytes = mInStream.read(buffer);
                if (numOfBytes==-1){
                    continue;
                }
                mHandler.obtainMessage(THREAD_READ, numOfBytes, -1, buffer).sendToTarget();

            } catch (IOException e) {
                mHandler.obtainMessage(THREAD_DISCONNECTED, ID, -1, mSocket.getRemoteDevice().getName()).sendToTarget();
                cancel();
                break;
            }
        }
    }

    /**
     * Write to the connected OutStream.
     *
     * @param buffer the buffer to write to the stream
     */
    public synchronized void write(byte[] buffer) {
        try {
            mOutStream.write(buffer);
        } catch (IOException e) {}
    }

    public synchronized void cancel() {
        isActive=false;

        try{
            if (mInStream!=null)
                mInStream.close();
        } catch (IOException e) {}

        try{
            if (mOutStream!=null)
                mOutStream.close();
        } catch (IOException e) {}

        try {
            mSocket.close();
        } catch (IOException e) {}

        this.interrupt();

    }


}

