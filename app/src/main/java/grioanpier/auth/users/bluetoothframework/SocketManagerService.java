package grioanpier.auth.users.bluetoothframework;
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
import android.app.Service;
import android.bluetooth.*;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.TreeMap;

import grioanpier.auth.users.bluetoothframework.loaders.ConnectedThread;

public class SocketManagerService extends Service {

    private final IBinder mBinder = new SocketManagerServiceBinder();
    private BluetoothSocket hostSocket = null;
    private final TreeMap<String, BluetoothSocket> playerSockets = new TreeMap<>();
    private final TreeMap<String, ConnectedThread> connectedThreads = new TreeMap<>();
    private final SocketManagerServiceHandler socketManagerHandler = new SocketManagerServiceHandler(this);

    private final HashMap<String, String> connectedDevicesNames = new HashMap<>();


    /**
     * Synchronization lock to be used by the write methods in order to avoid possible messages that are sent the same time
     * and things mess up.
     */
    private final static Object Write_Lock = new Object();

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


    public void addPlayerSocket(BluetoothSocket btSocket) {
        ConnectedThread thread = new ConnectedThread(btSocket, socketManagerHandler);
        thread.start();
        connectedThreads.put(thread.ID, thread);
        playerSockets.put(thread.ID, btSocket);
        connectedDevicesNames.put(btSocket.getRemoteDevice().getName(), thread.ID);

    }

    private boolean removePlayerSocket(String key) {
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

    void setHostSocket(BluetoothSocket btSocket) {
        hostSocket = btSocket;
        ConnectedThread thread = new ConnectedThread(btSocket, socketManagerHandler);
        thread.start();
        connectedThreads.put(thread.ID, thread);
        connectedDevicesNames.put(btSocket.getRemoteDevice().getName(), thread.ID);
    }

    private void removeHostSocket() {
        if (hostSocket != null) {
            try {
                hostSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            hostSocket = null;
        }
    }

    public String getHostAddress() {
        if (hostSocket == null)
            return null;
        else
            return hostSocket.getRemoteDevice().getAddress();
    }

    public String getHostName() {
        if (hostSocket == null)
            return null;
        else
            return hostSocket.getRemoteDevice().getName();
    }

    private void removeThread(String key) {
        connectedThreads.remove(key);
    }

    //TODO The MAC is unavailable as of Android 6.

    /**
     * Retrieves the MAC of the devices, if the two devices are conencted.
     * @param deviceName The device whose MAC we are looking for
     * @return the MAC address of the device
     */
    public String getMAC(String deviceName) {
        return connectedDevicesNames.get(deviceName);
    }

    /**
     * Sends the content to every connected device (including to yours).
     *
     * @param message the content to send.
     * @param appCode the appCode of the content. It states what part of the app the content comes from. For example {BLUETOOTH_CHAT}.
     */
    public void sendGlobalMessage(String message, int appCode) {
        sendMessage(message, "null", appCode, true);
    }

    /**
     * Sends the content to a specific device that is connected to the host (including the host). Only the host knows the MAC addresses
     * of all the connected devices. The rest devices can just use the target's name and the host will handle the lookup.
     * TODO: what happens if there are 2 devices with the same name?
     * @param message the content to send
     * @param target the target device's MAC address or public name.
     * @param appCode the appCode of the content. It states what part of the app the content comes from. For example {BLUETOOTH_CHAT}.
     */
    public void sendPrivateMessage(String message, String target, int appCode) {
        sendMessage(message, target, appCode, false);
    }

    private synchronized void sendMessage(String message, String target, int appCode, boolean global) {
        //Format the content.
        StringBuilder builder = new StringBuilder();

        //Pack everything in a BluetoothMessage
        BluetoothMessage btMsg = new BluetoothMessage();
        btMsg.isGlobal = global;
        btMsg.targetMAC = target;
        btMsg.sourceMAC = BluetoothManager.getMACAddress();
        btMsg.content = message;
        btMsg.appCode = appCode;
        builder.append(btMsg.getMessage());

        synchronized (Write_Lock) {
            if (global) {
                //Send the message. If the device isn't the host, then the content is sent to the host who relays it appropriately.
                writeToAll(builder.toString());

                if (BluetoothManager.isHost()) {
                    //If the device is the host, also consume it
                    byte[] buffer = builder.toString().getBytes();
                    socketManagerHandler.obtainMessage(ConnectedThread.THREAD_READ, buffer.length, -1, buffer).sendToTarget();
                }
            } else {
                if (BluetoothManager.isHost()) {
                    writeTo(builder.toString(), target);
                } else {
                    //The message will be sent to the host who will forward it.
                    writeToAll(builder.toString());
                }

            }

        }
    }


    /**
     * Formats the content in the form of [{@param content.length}][{@param content}]. The length of the content should be less than 4 decimals (0-999)
     * For example, "Hello World!" will be formatted to "012Hello World!", where 12 is the length of "Hello World!".
     *
     * @param message The content to format
     * @return The formatted content
     */
    public static String format(String message) {
        return BluetoothMessage.format(message);
    }

    /**
     * De-formats a content that's in the form of [content.length][content][rest]. The length of the content can be any String, as long as its size is less than 4 decimals (0-999)
     * and in the form of [001, 002, ..., 010, 011, ..., 999]. The [rest] can by anything, it isn't taken into account.
     * Use the returned integer to calculate the start and end indexes of the code, which will be at index_start=3 and index_end=3+length.
     * <p/>
     * For example,
     * String content = "012Hello World![rest]";
     * int length = deformat(content);
     * String content = content.substring(3,length+3); // content == "Hello World!"
     * String rest = content.substring(length + 3, content.length()); // rest == "BlahBlahBlah"
     *
     * @param message The content to be de-formatted.
     * @return The length of the actual content.
     */
    public static int deformat(String message) {
        return BluetoothMessage.deformat(message);
    }

    private void writeToAll(String message) {
        byte[] buffer = message.getBytes();
        introduceDelay(250);
        for (ConnectedThread thread : connectedThreads.values())
            thread.write(buffer);
    }

    private void writeTo(String message, String key) {
        introduceDelay(250);
        ConnectedThread thread = connectedThreads.get(key);
        if (thread != null) {
            thread.write(message.getBytes());
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

    private void clearConnectedThreads() {
        for (ConnectedThread thread : connectedThreads.values())
            thread.cancel();
        connectedThreads.clear();
        connectedDevicesNames.clear();
    }

    public void clear() {
        removeHostSocket();
        closePlayerSockets();
        clearConnectedThreads();
    }

    public class SocketManagerServiceBinder extends Binder {
        public SocketManagerService getService() {
            return SocketManagerService.this;
        }
    }

    public <T extends Handler> void addHandler(T handler) {
        socketManagerHandler.addHandler(handler);
    }

    public <T extends Handler> void removeHandler(T handler) {
        socketManagerHandler.removeHandler(handler);
    }

    public static class SocketManagerServiceHandler extends Handler {
        private final WeakReference<SocketManagerService> socketManagerService2WeakReference;
        private final TreeMap<Integer, Handler> mHandlers;

        private <T extends Handler> void addHandler(T handler) {
            mHandlers.put(handler.hashCode(), handler);
        }

        private <T extends Handler> void removeHandler(T handler) {
            mHandlers.remove(handler.hashCode());
        }

        SocketManagerServiceHandler(SocketManagerService socketManagerService) {
            mHandlers = new TreeMap<>();
            socketManagerService2WeakReference = new WeakReference<>(socketManagerService);
        }

        @Override
        public synchronized void handleMessage(Message msg) {
            switch (msg.what) {
                case ConnectedThread.THREAD_READ:
                    int numOfBytes = msg.arg1;
                    String message = new String((byte[]) msg.obj, 0, numOfBytes);
                    //message=[length][isGlobal][length][target MAC][length][source MAC][length][appCode][message content]
                    BluetoothMessage btMsg = new BluetoothMessage(message);

                    //Consuming the message
                    if (!BluetoothManager.isHost() || (btMsg.isGlobal || btMsg.targetMAC.equals(BluetoothManager.getMACAddress()))) {
                        //If the device isn't the host, then consume the message, global or private
                        //Otherwise, the device is the host, so consume the message only if it's global or private but the target was the host.
                        for (Handler handler : mHandlers.values())
                            handler.obtainMessage(btMsg.appCode, btMsg.content).sendToTarget();
                    }


                    //Relaying the message. The message is relayed only from the host.
                    if (BluetoothManager.isHost()) {
                        if (btMsg.isGlobal && !btMsg.sourceMAC.equals(BluetoothManager.getMACAddress())) {
                            //Relay the message to everyone if the message is global and wasn't sent from the same device.
                            //The host sends the message to everyone before consuming it, so at this point it has already been relayed.
                            socketManagerService2WeakReference.get().writeToAll(message);
                        } else if (!btMsg.isGlobal && !btMsg.targetMAC.equals(BluetoothManager.getMACAddress())) {
                            //If the device is the host and the message is private, forward appropriately. (unless the target was the host)
                            //The source device doesn't get a copy of the message.

                            //The device sends the name of the target instead of his MAC. The host retrieves it from the list of connected devices.
                            String targetMac = socketManagerService2WeakReference.get().getMAC(btMsg.targetMAC);
                            if (targetMac!=null){
                                socketManagerService2WeakReference.get().writeTo(message, targetMac);
                            }

                        }
                    }


                    break;

                case THREAD_CONNECTED:

                    break;

                case ConnectedThread.THREAD_DISCONNECTED:
                    //TODO the msg.obj should be forwarded to all the handlers instead.
                    //ConnectedThread calls ConnectedThread.cancel() internally which closes the streams and the socket.
                    //Remove it from the list as well. The thread returns its ID and the name of the remote device in a String[] array.
                    String ID = ((String[]) msg.obj)[0];
                    String remoteDeviceName = ((String[]) msg.obj)[1];
                    socketManagerService2WeakReference.get().removeThread(ID);
                    socketManagerService2WeakReference.get().connectedDevicesNames.remove(remoteDeviceName);
                    //Also remove the socket from our list.
                    String who;
                    if (socketManagerService2WeakReference.get().removePlayerSocket(ID)) {
                        //The user who left was a player.
                        who = remoteDeviceName;
                    } else {
                        socketManagerService2WeakReference.get().removeHostSocket();
                        who = "The host";
                    }

                    for (Handler handler : mHandlers.values())
                        handler.obtainMessage(ConnectedThread.THREAD_DISCONNECTED, who).sendToTarget();

                    break;

            }
        }
    }


    //These are provided as int in the msg.what
    public static final int THREAD_READ = ConnectedThread.THREAD_READ;//0
    public static final int THREAD_DISCONNECTED = ConnectedThread.THREAD_DISCONNECTED;//1
    public static final int THREAD_STREAM_ERROR = ConnectedThread.THREAD_STREAM_ERROR;//2
    public static final int THREAD_CONNECTED = 3;


}
