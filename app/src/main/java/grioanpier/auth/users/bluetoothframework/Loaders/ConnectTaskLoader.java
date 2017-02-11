package grioanpier.auth.users.bluetoothframework.loaders;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.AsyncTaskLoader;
import android.content.Context;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

/**
 * Listens for incoming connections on the provided UUIDs.
 * It listens for 1 UUID at every time and as soon as a connection is initialized, it moves to the next.
 */
public class ConnectTaskLoader extends AsyncTaskLoader<BluetoothSocket> {
    //TODO this should be called via @link{BluetoothManager}
    private static final String LOG_TAG = ConnectTaskLoader.class.getSimpleName();
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
            Log.e(LOG_TAG, "Trying (" +index+ ")to connect to bluetooth socket");
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



