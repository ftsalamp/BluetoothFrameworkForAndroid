package grioanpier.auth.users.bluetoothframework;

import android.app.Service;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;

import java.io.IOException;
import java.util.Collection;
import java.util.TreeMap;

import grioanpier.auth.users.bluetoothframework.Loaders.ConnectedThread;

public class SocketManagerService extends Service {

    IBinder mBinder = new MyBinder();
    private BluetoothSocket hostSocket = null;
    private TreeMap<Integer, BluetoothSocket> playerSockets = new TreeMap<>();
    private final TreeMap<Integer, ConnectedThread> connectedThreads = new TreeMap<>();
    private static final String LOG_TAG = SocketManagerService.class.getCanonicalName();

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    /**
     * Return the communication channel to the service.  May return null if
     * clients can not bind to the service.  The returned
     * {@link IBinder} is usually for a complex interface
     * that has been <a href="{@docRoot}guide/components/aidl.html">described using
     * aidl</a>.
     * <p/>
     * <p><em>Note that unlike other application components, calls on to the
     * IBinder interface returned here may not happen on the main thread
     * of the process</em>.  More information about the main thread can be found in
     * <a href="{@docRoot}guide/topics/fundamentals/processes-and-threads.html">Processes and
     * Threads</a>.</p>
     *
     * @param intent The Intent that was used to bind to this service,
     *               as given to {@link Context#bindService
     *               Context.bindService}.  Note that any extras that were included with
     *               the Intent at that point will <em>not</em> be seen here.
     * @return Return an IBinder through which clients can call on to the
     * service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * Called by the system to notify a Service that it is no longer used and is being removed.  The
     * service should clean up any resources it holds (threads, registered
     * receivers, etc) at this point.  Upon return, there will be no more calls
     * in to this Service object and it is effectively dead.  Do not call this method directly.
     */
    @Override
    public void onDestroy() {
        clear();
    }


    public <T extends Handler> void addPlayerSocket(BluetoothSocket btSocket, T handler) {
        Log.i(LOG_TAG, "A new player socket was added: " + btSocket.hashCode());
        ConnectedThread thread = new ConnectedThread(btSocket, handler);
        thread.start();
        connectedThreads.put(thread.ID, thread);
        playerSockets.put(thread.ID, btSocket);
    }

    public boolean removePlayerSocket(Integer key) {
        Log.i(LOG_TAG, "Removing player socket: " + key);
        if (playerSockets.containsKey(key)) {
            try {
                playerSockets.remove(key).close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return true;
        } else
            return false;
    }

    public Collection<BluetoothSocket> getPlayerSockets() {
        return playerSockets.values();
    }

    public <T extends Handler> void setHostSocket(BluetoothSocket btSocket, T handler) {
        hostSocket = btSocket;
        ConnectedThread thread = new ConnectedThread(btSocket, handler);
        thread.start();
        connectedThreads.put(thread.ID, thread);
    }

    public boolean removeHostSocket() {
        if (hostSocket == null)
            return false;
        else {
            try {
                hostSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            hostSocket = null;
            return true;
        }
    }

    public BluetoothSocket getHostSocket() {
        return hostSocket;
    }

    public String getHostAddress() {
        if (hostSocket == null)
            return null;
        else
            return hostSocket.getRemoteDevice().getAddress();
    }

    public void removeThread(Integer key) {
        connectedThreads.remove(key);
    }

    public int threadsSize() {
        return connectedThreads.size();
    }

    public void writeToAll(String message) {
        Log.i(LOG_TAG, "Relaying message: " + message);
        byte[] buffer = message.getBytes();
        introduceDelay(250);
        for (ConnectedThread thread : connectedThreads.values())
            thread.write(buffer);
    }

    public void writeTo(String message, int threadIndex) {
        introduceDelay(250);
        Collection<ConnectedThread> col = connectedThreads.values();
        if (!col.isEmpty()) {
            ConnectedThread thread = (ConnectedThread) col.toArray()[threadIndex];
            if (thread != null) {
                thread.write(message.getBytes());
            }
        }
    }

    //Don't want messages to be relayed too fast in succession because they get entangled.
    private void introduceDelay(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
        }
    }

    private void closePlayerSockets() {
        for (BluetoothSocket socket : playerSockets.values()) {
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        playerSockets.clear();

    }

    private void closeHostSocket() {
        try {
            if (hostSocket != null)
                hostSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            hostSocket = null;
        }
    }

    private void clearConnectedThreads() {
        for (ConnectedThread thread : connectedThreads.values())
            thread.cancel();
        connectedThreads.clear();
    }

    public void clear() {
        closeHostSocket();
        closePlayerSockets();
        clearConnectedThreads();
    }

    public class MyBinder extends Binder {

        SocketManagerService getService() {
            return SocketManagerService.this;
        }

    }
}
