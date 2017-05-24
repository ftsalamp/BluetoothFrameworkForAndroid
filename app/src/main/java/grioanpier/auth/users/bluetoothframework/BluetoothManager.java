package grioanpier.auth.users.bluetoothframework;

import android.app.Activity;
import android.app.Fragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.Loader;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

import grioanpier.auth.users.bluetoothframework.SocketManagerService.SocketManagerServiceBinder;
import grioanpier.auth.users.bluetoothframework.loaders.AcceptTaskLoader;
import grioanpier.auth.users.bluetoothframework.loaders.ConnectTaskLoader;

/**
 * A {@link Fragment} that contains various useful methods regarding the Bluetooth.
 */
public class BluetoothManager extends Fragment {

    private static final int ACCEPT_LOADER = 0;
    private static final int CONNECT_LOADER = 1;

    //UUID was acquired from UUID.randomUUID() once and is now hardcoded
    //bluetooth client and server must use the same UUID
    private static final UUID[] sUUIDs = {
            UUID.fromString("728b4e0c-20bf-47cd-843e-016ab7075f1a"),
            UUID.fromString("85f8593d-4780-49d6-a174-df5ee4960b4a"),
            UUID.fromString("f113f31b-d6bc-4bb7-b5da-53f23c155c45"),
            UUID.fromString("93a5d2e8-4fd2-4415-a245-81a41a4adab7"),
            UUID.fromString("e3159cf2-b4ae-451d-b30d-ff4c61e86a53"),
            UUID.fromString("a62e7a9d-fd82-4e10-85ea-2acb45dadc98"),
            UUID.fromString("d187a344-23c5-4cc2-bc3b-f70eef93b3fc"),
            UUID.fromString("da5dd52e-1cdf-474d-8eeb-3c31287ab7e2"),
            UUID.fromString("6b98a8ea-9641-49e8-959c-9a9f767a6809"),
            UUID.fromString("037c8466-b294-489c-b410-00f5a8c123c9")
    };

    //The device that we want to connect to. Shouldn't be used anywhere else except for the ConnectLoader
    private BluetoothDevice connectedDevice = null;


    private static final ArrayList<UUID> sAvailableUUIDs = new ArrayList<>(Arrays.asList(sUUIDs));
    private static final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    private SocketManagerService mService;
    private boolean mBound = false;

    private static final int UNDEFINED = -1;
    public static final int PLAYER = 0;
    public static final int HOST = 1;
    public static int DEVICE_TYPE = UNDEFINED;

    //Possible values for resultCode that is passed on the onActivityResult
    private final static int RESULT_CANCELED = Activity.RESULT_CANCELED;
    private final static int RESULT_OK = Activity.RESULT_OK;

    private final static String ACTION_FOUND = BluetoothDevice.ACTION_FOUND;
    private final static String ACTION_DISCOVERY_STARTED = BluetoothAdapter.ACTION_DISCOVERY_STARTED;
    private final static String ACTION_DISCOVERY_FINISHED = BluetoothAdapter.ACTION_DISCOVERY_FINISHED;

    private final static String ACTION_STATE_CHANGED = BluetoothAdapter.ACTION_STATE_CHANGED;
    private final static String EXTRA_STATE = BluetoothAdapter.EXTRA_STATE;
    private final static int STATE_OFF = BluetoothAdapter.STATE_OFF;
    private final static int STATE_TURNING_ON = BluetoothAdapter.STATE_TURNING_ON;
    private final static int STATE_ON = BluetoothAdapter.STATE_ON;
    private final static int STATE_TURNING_OFF = BluetoothAdapter.STATE_TURNING_OFF;

    //Locally defined ints that the system passes back to me in the onActivityResult() implementation as the requestCode parameter.
    private final static int REQUEST_ENABLE_BLUETOOTH = 1;
    private final static int REQUEST_MAKE_DISCOVERABLE = 2;

    // Create a BroadcastReceiver for ACTION_FOUND
    private BroadcastReceiver mDiscoveryReceiver = null;
    private BroadcastReceiver mBluetoothStateReceiver = null;
    private final static int isDiscoverable = BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE;

    public static boolean isBluetoothAvailable() {
        return (BluetoothAdapter.getDefaultAdapter() != null);
    }

    public static boolean isBluetoothEnabled() {
        return BluetoothAdapter.getDefaultAdapter().isEnabled();
    }

    public static String getName() {
        if (mBluetoothAdapter != null)
            return mBluetoothAdapter.getName();
        else
            return null;
    }

    /**
     * Returns the MAC address of the device.
     *
     * @return the MAC address of the device
     */
    //TODO in android 6 this has been deprecated and is unusable.
    public static String getMACAddress() {
        if (mBluetoothAdapter != null)
            return mBluetoothAdapter.getAddress();
        else
            return null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_ENABLE_BLUETOOTH: {
                //Requested to enable the bluetooth
                if (bluetoothRequestEnableListener != null) {
                    switch (resultCode) {
                        case RESULT_OK:
                            bluetoothRequestEnableListener.onResult(true);
                            break;
                        case RESULT_CANCELED:
                            bluetoothRequestEnableListener.onResult(false);
                            break;
                    }
                }
                break;
            }
            case REQUEST_MAKE_DISCOVERABLE: {
                if (bluetoothRequestDiscoverableListener != null)
                    bluetoothRequestDiscoverableListener.onResult(resultCode != RESULT_CANCELED);
            }
        }
    }

    //Request that Bluetooth is enabled
    public void enableBluetooth() {
        if (mBluetoothAdapter.isEnabled()) {
            if (bluetoothRequestEnableListener != null)
                bluetoothRequestEnableListener.onEnabled();
            return;
        }

        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STATE_CHANGED);
        mBluetoothStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int extra = intent.getIntExtra(EXTRA_STATE, 42);
                switch (extra) {
                    case STATE_ON:
                        if (bluetoothRequestEnableListener != null)
                            bluetoothRequestEnableListener.onEnabled();
                        break;
                    case STATE_OFF:
                        break;
                    case STATE_TURNING_OFF:
                        break;
                    case STATE_TURNING_ON:
                        break;
                }
            }
        };
        getActivity().registerReceiver(mBluetoothStateReceiver, filter);
        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BLUETOOTH);
    }

    public void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() != isDiscoverable) {
            Intent makeDiscoverable = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            startActivityForResult(makeDiscoverable, REQUEST_MAKE_DISCOVERABLE);
        }
    }

    public Set<BluetoothDevice> getPairedDevices() {
        return mBluetoothAdapter.getBondedDevices();
    }

    /**
     * Begins searching for nearby bluetooth devices.
     */
    public void discoverDevices() {
        if (mBluetoothAdapter.isDiscovering())
            return;

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_FOUND);
        filter.addAction(ACTION_DISCOVERY_STARTED);
        filter.addAction(ACTION_DISCOVERY_FINISHED);

        mDiscoveryReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                discoveryBroadcast(intent);
            }
        };
        getActivity().registerReceiver(mDiscoveryReceiver, filter);
        mBluetoothAdapter.startDiscovery();
    }

    private void discoveryBroadcast(Intent intent) {
        String action = intent.getAction();
        switch (action) {
            //When the discovery starts
            case ACTION_DISCOVERY_STARTED:
                break;
            // When discovery finds a device
            case ACTION_DISCOVERY_FINISHED:
                if (mDiscoveryReceiver != null) {
                    getActivity().unregisterReceiver(mDiscoveryReceiver);
                    mDiscoveryReceiver = null;
                }
                break;
            case ACTION_FOUND:
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if ((device != null) && (bluetoothGetAvailableDevicesListener != null)) {
                    bluetoothGetAvailableDevicesListener.onDeviceFound(device);
                }
                break;
        }
    }

    private boolean serverListenForConnectionsConstant = false;

    public void serverListenForConnections(boolean constant) {
        serverListenForConnectionsConstant = constant;
        UUID uuid = getNextUUID();
        if (uuid != null) {
            serverListenForConnections(uuid);
        } else {
            if (serverListenForConnectionsListener != null) {
                serverListenForConnectionsListener.onConnectionEstablished(false, NO_AVAILABLE_UUID);
            }
        }

    }

    private void onConnectionEstablished(boolean established) {
        if (established) {
            removeNextUUID();
            if (serverListenForConnectionsConstant) {
                UUID uuid = getNextUUID();
                if (uuid != null) {
                    prepareServerListenForConnections();
                    serverListenForConnections(uuid);
                }
            }
        }
    }

    /**
     * Creates an {@link AcceptTaskLoader} that listens for incoming connections for the provided {@link UUID}.
     * The results are stored in the {@link SocketManagerService}.
     *
     * @param uuid The {@link UUID} that will be used to listen for incoming connections.
     */
    private void serverListenForConnections(final UUID uuid) {
        DEVICE_TYPE = HOST;

        LoaderManager loaderManager = getLoaderManager();
        loaderManager.initLoader(ACCEPT_LOADER, null, new LoaderCallbacks<BluetoothSocket>() {
            @Override
            public Loader<BluetoothSocket> onCreateLoader(int id, Bundle args) {
                return new AcceptTaskLoader(getActivity(), uuid);
            }

            @Override
            public void onLoadFinished(Loader<BluetoothSocket> loader, BluetoothSocket bluetoothSocket) {
                if (serverListenForConnectionsListener != null) {
                    if (bluetoothSocket != null) {
                        serverListenForConnectionsListener.onConnectionEstablished(true, bluetoothSocket.getRemoteDevice().getName());
                        onConnectionEstablished(true);
                        if (mBound) {
                            mService.addPlayerSocket(bluetoothSocket);

                        }
                    } else {
                        serverListenForConnectionsListener.onConnectionEstablished(false, null);
                        onConnectionEstablished(false);
                    }
                }
            }

            @Override
            public void onLoaderReset(Loader<BluetoothSocket> loader) {
            }
        });

    }

    /**
     * Creates a {@link ConnectTaskLoader} to try and connect to the specified device.
     * If a connection is established, it calls the respective method for the supplied {source}
     *
     * @param MAC_address the MAC Address of the target device.
     */
    public void connect(String MAC_address) {
        if (MAC_address == null)
            return;

        String hostAddress = null;
        if (mBound) {
            hostAddress = mService.getHostAddress();
        }

        if ((hostAddress != null) && MAC_address.equals(hostAddress)) {
            Toast.makeText(getActivity(), "Already connected", Toast.LENGTH_SHORT).show();
            if (connectListener != null) {
                String hostName = mBound ? mService.getHostName() : null;
                connectListener.onConnected(true, hostName);
            }
            return;
        }

        if (mBound) {
            mService.clear();
        }

        refreshUUIDs();

        //If we were already trying to connect to a device, destroy the loader and start again.
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(MAC_address);
        if (    connectedDevice != null) {
            connectedDevice = device;
            getLoaderManager().restartLoader(CONNECT_LOADER, null, connectLoader);
        } else {
            connectedDevice = device;
            getLoaderManager().initLoader(CONNECT_LOADER, null, connectLoader);
        }

    }

    private final LoaderCallbacks<BluetoothSocket> connectLoader = new LoaderCallbacks<BluetoothSocket>() {
        @Override
        public Loader<BluetoothSocket> onCreateLoader(int id, Bundle args) {
            return new ConnectTaskLoader(getActivity(), connectedDevice, sUUIDs);
        }

        @Override
        //Attempts to connect to the device.
        public void onLoadFinished(Loader<BluetoothSocket> loader, BluetoothSocket btSocket) {
            DEVICE_TYPE = PLAYER;
            if (btSocket != null) {
                String name = btSocket.getRemoteDevice().getName();
                if (connectListener != null) {
                    connectListener.onConnected(true, name);
                }
                if (mBound) {
                    mService.setHostSocket(btSocket);
                }
            } else {
                if (connectListener != null) {
                    connectListener.onConnected(false, null);
                }
            }
        }
        @Override
        public void onLoaderReset(Loader<BluetoothSocket> loader) {
        }
    };

    private UUID getNextUUID() {
        if (sAvailableUUIDs.isEmpty())
            return null;
        else
            return sAvailableUUIDs.get(0);
    }

    /**
     * @return true if there was something to remove, false otherwise.
     */
    private void removeNextUUID() {
        if (!sAvailableUUIDs.isEmpty()) {
            sAvailableUUIDs.remove(0);
        }
    }

    public static void refreshUUIDs() {
        sAvailableUUIDs.clear();
        sAvailableUUIDs.ensureCapacity(10);
        sAvailableUUIDs.addAll(Arrays.asList(sUUIDs));
    }

    public static boolean isHost() {
        return DEVICE_TYPE == HOST;
    }

    /**
     * Prepares the server to listen for incoming connections.
     */
    private void prepareServerListenForConnections() {
        getLoaderManager().destroyLoader(ACCEPT_LOADER);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to SocketManagerService
        Intent intent = new Intent(getActivity(), SocketManagerService.class);
        getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    /**
     * Defines callbacks for service binding, passed to bindService()
     */
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            SocketManagerServiceBinder binder = (SocketManagerServiceBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public void onStop() {
        if (mBound) {
            getActivity().unbindService(mConnection);
            mBound = false;
        }
        super.onStop();
    }

    @Override
    public void onDestroy() {
        if (mDiscoveryReceiver != null) {
            getActivity().unregisterReceiver(mDiscoveryReceiver);
            mDiscoveryReceiver = null;
        }
        if (mBluetoothStateReceiver != null) {
            getActivity().unregisterReceiver(mBluetoothStateReceiver);
            mBluetoothStateReceiver = null;
        }
        mBluetoothAdapter.cancelDiscovery();
        super.onDestroy();
    }


    public interface BluetoothRequestEnableListener {
        /**
         * Invoked when the user decides whether to enable the Bluetooth or not.
         *
         * @param enabled true if the user activated the bluetooth, false otherwise. Note: true doesn't mean the Bluetooth is already active.
         */
        void onResult(boolean enabled);

        /**
         * Invoked when the Bluetooth is fully active.
         */
        void onEnabled();
    }

    public interface BluetoothRequestDiscoverableListener {
        void onResult(boolean enabled);
    }

    public interface BluetoothGetAvailableDevicesListener {
        /**
         * @param device the (@link BluetoothDevice) that was found.
         */
        void onDeviceFound(BluetoothDevice device);
    }

    public interface ServerListenForConnectionsListener {
        //Invoked when a connection was established. The result is saved in SocketManagerService.hostSockets
        void onConnectionEstablished(boolean established, String name);
    }

    public interface ConnectListener {
        void onConnected(boolean connected, String deviceName);
    }

    private BluetoothRequestEnableListener bluetoothRequestEnableListener;

    public void setBluetoothRequestEnableListener(BluetoothRequestEnableListener listener) {
        bluetoothRequestEnableListener = listener;
    }


    private BluetoothRequestDiscoverableListener bluetoothRequestDiscoverableListener;

    public void setBluetoothRequestDiscoverableListener(BluetoothRequestDiscoverableListener listener) {
        bluetoothRequestDiscoverableListener = listener;
    }


    private BluetoothGetAvailableDevicesListener bluetoothGetAvailableDevicesListener;

    public void setBluetoothGetAvailableDevicesListener(BluetoothGetAvailableDevicesListener listener) {
        bluetoothGetAvailableDevicesListener = listener;
    }


    private ServerListenForConnectionsListener serverListenForConnectionsListener;

    public void setServerListenForConnectionsListener(ServerListenForConnectionsListener listener) {
        serverListenForConnectionsListener = listener;
    }

    private ConnectListener connectListener;

    public void setConnectListener(ConnectListener listener) {
        connectListener = listener;
    }

    private final static String NO_AVAILABLE_UUID = "There are no more available UUIDs";

}

