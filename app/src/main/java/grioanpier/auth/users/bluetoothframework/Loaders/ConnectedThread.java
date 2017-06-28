package grioanpier.auth.users.bluetoothframework.loaders;
/*
Copyright {2016} {Ioannis Pierros (ioanpier@gmail.com)}

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
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

    private final BluetoothSocket mSocket;
    private final InputStream mInStream;
    private final OutputStream mOutStream;
    private final Handler mHandler;
    public final String ID;

    private static final int MESSAGE_SIZE = 1024;
    public static final int THREAD_READ = 0;
    public static final int THREAD_DISCONNECTED = 1;
    public static final int THREAD_STREAM_ERROR = 2;

    private boolean isActive;

    public <T extends Handler> ConnectedThread(BluetoothSocket socket, T handler) {
        mSocket = socket;
        InputStream tempIn = null;
        OutputStream tempOut = null;
        mHandler = handler;
        ID = socket.getRemoteDevice().getAddress();
        isActive = true;

        try {
            tempIn = socket.getInputStream();
            tempOut = socket.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
            mHandler.obtainMessage(THREAD_STREAM_ERROR).sendToTarget();
        }

        mInStream = tempIn;
        mOutStream = tempOut;
    }

    public void run() {
        byte[] buffer = new byte[MESSAGE_SIZE];
        int numOfBytes;

        // Keep listening to the InputStream while connected
        while (isActive) {
            try {
                numOfBytes = mInStream.read(buffer);
                if (numOfBytes == -1) {
                    continue;
                }
                mHandler.obtainMessage(THREAD_READ, numOfBytes, -1, buffer).sendToTarget();
            } catch (IOException e) {
                //TODO the message should only contain the Thread.ID
                String[] info = {ID, mSocket.getRemoteDevice().getName()};
                mHandler.obtainMessage(THREAD_DISCONNECTED, info).sendToTarget();
                cancel();
            } catch (Exception e){
                e.printStackTrace();
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public synchronized void cancel() {
        isActive = false;

        try {
            if (mInStream != null)
                mInStream.close();
        } catch (IOException e) {
        }

        try {
            if (mOutStream != null)
                mOutStream.close();
        } catch (IOException e) {
        }

        try {
            mSocket.close();
        } catch (IOException e) {
        }

        interrupt();
    }


}

