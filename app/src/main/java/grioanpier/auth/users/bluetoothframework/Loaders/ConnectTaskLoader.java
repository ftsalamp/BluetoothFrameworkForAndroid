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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.AsyncTaskLoader;
import android.content.Context;

import java.io.IOException;
import java.util.UUID;

/**
 * Listens for incoming connections on the provided UUIDs.
 * It listens for 1 UUID at every time and as soon as a connection is initialized, it moves to the next.
 */
public class ConnectTaskLoader extends AsyncTaskLoader<BluetoothSocket> {

    private final BluetoothDevice mBtDevice;
    private final UUID[] mUUIDs;
    private BluetoothSocket mBtSocket;

    public ConnectTaskLoader(Context context, BluetoothDevice bluetoothDevice, UUID... uuids) {
        super(context);
        mUUIDs=uuids;
        mBtDevice=bluetoothDevice;
    }

    @Override
    public void deliverResult(BluetoothSocket socket) {
        mBtSocket = socket;
        if (isStarted())
            super.deliverResult(socket);
    }

    @Override
    protected void onStartLoading() {
        super.onStartLoading();
        if (mBtSocket != null)
            deliverResult(mBtSocket);
        if (takeContentChanged() || (mBtSocket == null)) {
            // If the data has changed since the last time it was loaded
            // or is not currently available, start a load.
            forceLoad();
        }
    }


    @Override
    public BluetoothSocket loadInBackground() {
        BluetoothSocket btSocket;
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

        //Cycles through the available UUIDs and tries to connect to the specified device
        int index=0;
        do{
            try {
                Thread.sleep(100);
                btSocket = mBtDevice.createRfcommSocketToServiceRecord(mUUIDs[index]);
                btSocket.connect();
            } catch (IOException e) {
                btSocket=null;
                index++;
            } catch (InterruptedException e) {
                btSocket=null;
                e.printStackTrace();
            }

        }while ((btSocket == null) && (index < mUUIDs.length));

        return btSocket;
    }
}



