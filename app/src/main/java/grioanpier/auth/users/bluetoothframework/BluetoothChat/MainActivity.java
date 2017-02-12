package grioanpier.auth.users.bluetoothframework.bluetoothChat;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import grioanpier.auth.users.bluetoothframework.*;
import grioanpier.auth.users.bluetoothframework.BluetoothManager;
import grioanpier.auth.users.bluetoothframework.BluetoothManager.BluetoothGetAvailableDevicesListener;
import grioanpier.auth.users.bluetoothframework.BluetoothManager.BluetoothRequestEnableListener;
import grioanpier.auth.users.bluetoothframework.BluetoothManager.ConnectListener;
import grioanpier.auth.users.bluetoothframework.R.id;
import grioanpier.auth.users.bluetoothframework.R.layout;
import grioanpier.auth.users.bluetoothframework.R.menu;
import grioanpier.auth.users.bluetoothframework.SocketManagerService.SocketManagerServiceBinder;

public class MainActivity extends Activity {

    private static final String LOG_TAG = MainActivity.class.getSimpleName();
    private boolean restored;

    private static final String sBluetoothManagerFragmentTag = "bluetoothmanager";
    private BluetoothManager btManager;

    private static final String sPlaceholderFragmentTag = "localplaceholder";
    private PlaceholderFragment frag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_main_activity);

        //Finish the activity if there is no Bluetooth on the device.
        if (!BluetoothManager.isBluetoothAvailable()) {
            //We are killing the activity in onCreate so the View hasn't been inflated yet.
            Toast.makeText(getApplicationContext(), "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }

        if (savedInstanceState == null) {
            restored = false;
            btManager = new BluetoothManager();
            frag = new PlaceholderFragment();
            getFragmentManager().beginTransaction()
                    .add(id.container, frag, sPlaceholderFragmentTag)
                    .add(btManager, sBluetoothManagerFragmentTag)
                    .commit();
        } else {
            restored = true;
            btManager = (BluetoothManager) getFragmentManager().findFragmentByTag(sBluetoothManagerFragmentTag);
            frag = (PlaceholderFragment) getFragmentManager().findFragmentByTag(sPlaceholderFragmentTag);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.ensure_discoverable) {
            btManager.ensureDiscoverable();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onStart() {
        super.onStart();
        //Start the SocketManagerService that holds the various bluetooth threads.
        startService(new Intent(this, SocketManagerService.class));

        if (BluetoothManager.isBluetoothEnabled()) {
            if (!restored)
                //If this is the first time running, set the Paired devices.
                frag.setDevicesList(btManager.getPairedDevices());
        } else {
            //The Bluetooth isn't enabled. Set a listener to listen for the result and prompts the user to enable it.
            btManager.setBluetoothRequestEnableListener(new BluetoothRequestEnableListener() {
                @Override
                public void onResult(boolean enabled) {
                    if (!enabled) {
                        Toast.makeText(getApplicationContext(), "Bluetooth is needed for Local Game", Toast.LENGTH_LONG).show();
                        btManager.getActivity().finish();
                    }
                }

                @Override
                public void onEnabled() {
                    frag.setDevicesList(btManager.getPairedDevices());
                }
            });
            btManager.ensureEnabled();
        }

        //Set a listener for {onDeviceFound} Events. Send the found device to the {@link BluetoothChatFragment}
        btManager.setBluetoothGetAvailableDevicesListener(new BluetoothGetAvailableDevicesListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device) {
                frag.addDeviceInList(device);
            }
        });

        //The {@link BluetoothManager} starts the discovery only if it isn't already discovering.
        btManager.getAvailableDevices();
    }

    @Override
    public void onStop() {
        super.onStop();
        //Make sure to cancel the bluetooth discovery.
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
    }

    /**
     * A placeholder fragment containing this screen's view. A list of BluetoothDevices
     * and buttons to join, spectate, host
     */
    public static class PlaceholderFragment extends Fragment {

        private static final String LOG_TAG = MainActivity.class.getSimpleName() + PlaceholderFragment.class.getSimpleName();
        private static final String bundleDeviceList = "devicesListForSaveInstance";
        private static final String bundleListViewPosition = "listViewPositionForSaveInstance";

        SocketManagerService mService;
        boolean mBound = false;

        private ListView listView;
        private int mListViewPosition = ListView.INVALID_POSITION;
        private Button join_button;
        private Button host_button;
        private Button refresh_button;

        private View selectedView;
        private String selectedMAC;

        private ArrayAdapter<String> devicesAdapter = null;
        private ArrayList<String> devicesList = new ArrayList<>();
        // HashSet to back up devicesList to prevent duplicates.
        private HashSet<String> devicesSet = new HashSet<>();

        public PlaceholderFragment() {
        }

        @Override
        public void onSaveInstanceState(Bundle bundle) {
            super.onSaveInstanceState(bundle);

            if (devicesList != null)
                bundle.putStringArrayList(bundleDeviceList, devicesList);

            if (mListViewPosition != ListView.INVALID_POSITION)
                bundle.putInt(bundleListViewPosition, mListViewPosition);
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setHasOptionsMenu(true);

            if (savedInstanceState != null) {
                devicesList = savedInstanceState.getStringArrayList(bundleDeviceList);
                if (devicesList != null)
                    devicesSet = new HashSet<>(devicesList);
                else
                    devicesSet = new HashSet<>();
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(layout.fragment_main_activity, container, false);
            //call findViewById for the join and spectate buttons and assign them their respective onClickListeners
            listView = (ListView) rootView.findViewById(id.pairedDevicesList);

            devicesAdapter = new ArrayAdapter<>(getActivity(),
                    layout.bt_devices_array_adapter,
                    devicesList);

            listView.setAdapter(devicesAdapter);

            //Sets the current selectedView to transparent color when scrolling,
            listView.setOnScrollListener(new OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView view, int scrollState) {
                }

                @Override
                public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                    if ((mListViewPosition < firstVisibleItem) || (mListViewPosition >= (firstVisibleItem + visibleItemCount))) {
                        mListViewPosition = ListView.INVALID_POSITION;
                    }
                }
            });

            listView.setOnItemClickListener(new OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                    //Set the current selectedView to the selected color
                    selectedView = view;
                    selectedView.setSelected(true);

                    //Extract MAC address
                    selectedMAC = devicesAdapter.getItem(position);
                    selectedMAC = selectedMAC.substring(selectedMAC.indexOf("\n") + 1, selectedMAC.length());

                    mListViewPosition = position;
                }
            });

            if ((savedInstanceState != null) && savedInstanceState.containsKey(bundleDeviceList)) {
                mListViewPosition = savedInstanceState.getInt(bundleListViewPosition);
            }

            join_button = (Button) rootView.findViewById(id.join_button);
            host_button = (Button) rootView.findViewById(id.hostgame_button);
            refresh_button = (Button) rootView.findViewById(id.refresh_button);


            join_button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    ((MainActivity)getActivity()).btManager.connect(selectedMAC);
                }
            });
            host_button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    host();
                }
            });
            refresh_button.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    refresh();
                }
            });

            ((MainActivity)getActivity()).btManager.setConnectListener(new ConnectListener() {
                @Override
                public void onConnected(boolean connected, String deviceName) {
                    if(connected){
                        Toast.makeText(getActivity(), "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
                        join();
                    }else{
                        Log.e(LOG_TAG, "Failed to connect to device");
                    }

                }
            });

            return rootView;
        }

        @Override
        public void onStart() {
            super.onStart();
            // Bind to SocketManagerService
            Intent intent = new Intent(getActivity(), SocketManagerService.class);
            getActivity().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        }

        @Override
        public void onResume() {
            super.onResume();
            if (mListViewPosition != ListView.INVALID_POSITION) {
                listView.smoothScrollToPosition(mListViewPosition);
                listView.setSelection(mListViewPosition);
            }
        }

        @Override
        public void onStop(){
            super.onStop();
            getActivity().unbindService(mConnection);
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

        public void refresh() {
            if (mBound) {
                mService.clear();
            }
            grioanpier.auth.users.bluetoothframework.BluetoothManager.refreshUUIDs();
        }

        private void join() {
            Intent intent = new Intent(getActivity(), ChatRoom.class);
            grioanpier.auth.users.bluetoothframework.BluetoothManager.DEVICE_TYPE = grioanpier.auth.users.bluetoothframework.BluetoothManager.PLAYER;
            startActivity(intent);
        }

        private void host() {
            if (mBound) {
                mService.clear();
            } else {
                Log.e(LOG_TAG, "SocketManagerService wasn't bound!");
            }

            grioanpier.auth.users.bluetoothframework.BluetoothManager.refreshUUIDs();
            cancelDiscovery();
            //Since the user wants to become a host, clear the host socket, if there is one.

            Intent intent = new Intent(getActivity(), ChatRoom.class);
            grioanpier.auth.users.bluetoothframework.BluetoothManager.DEVICE_TYPE = grioanpier.auth.users.bluetoothframework.BluetoothManager.HOST;
            startActivity(intent);
        }

        private void cancelDiscovery() {
            BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
        }

        public void setDevicesList(Set<BluetoothDevice> list) {
            String string;

            for (BluetoothDevice device : list) {
                string = device.getName() + "\n" + device.getAddress();
                if (devicesSet.add(string)) {
                    devicesAdapter.add(string);
                }
            }
        }

        public void addDeviceInList(BluetoothDevice device) {
            String string = device.getName() + "\n" + device.getAddress();

            if (devicesSet.add(string)) {
                devicesAdapter.add(string);
            }
        }

    }
}
