package grioanpier.auth.users.bluetoothframework.bluetoothChat;

import android.app.Activity;
import android.bluetooth.*;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
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
import grioanpier.auth.users.bluetoothframework.SocketManagerService.SocketManagerServiceBinder;

import static grioanpier.auth.users.bluetoothframework.BluetoothManager.refreshUUIDs;

public class MainActivity extends Activity {

    private static final String BLUETOOTH_MANAGER_TAG = "bluetoothmanager";
    private BluetoothManager bluetoothManager;

    private static final String bundleDeviceList = "devicesListForSaveInstance";
    private static final String bundleListViewPosition = "listViewPositionForSaveInstance";

    SocketManagerService mService;
    boolean mBound = false;

    private ListView listView;
    //This is used in onResume, to scroll to the selected device in the listView.
    private int mListViewPosition = ListView.INVALID_POSITION;

    //The device that the user has selected from the devices list.
    private View selectedView;
    private String selectedMAC;

    private ArrayList<String> devicesList = new ArrayList<>();
    private ArrayAdapter<String> devicesAdapter = null;
    // HashSet to back up devicesList to prevent duplicates.
    private HashSet<String> devicesSet = new HashSet<>();

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_activity, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.ensure_discoverable) {
            bluetoothManager.ensureDiscoverable();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        //Restore the devices that were found
        if (savedInstanceState != null) {
            devicesList = savedInstanceState.getStringArrayList(bundleDeviceList);
            if (devicesList != null)
                devicesSet = new HashSet<>(devicesList);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //Start the SocketManagerService that holds the various bluetooth threads.
        //Note that this is different for binding to the service.
        startService(new Intent(this, SocketManagerService.class));

        //Finish the activity if there is no Bluetooth on the device.
        if (!BluetoothManager.isBluetoothAvailable()) {
            //We are killing the activity in onCreate so the View hasn't been inflated yet.
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
        }
        setContentView(layout.activity_main_activity);

        //Add the BluetoothManager fragment in the activity.
        if (savedInstanceState == null) {
            bluetoothManager = new BluetoothManager();
            getFragmentManager().beginTransaction()
                    .add(bluetoothManager, BLUETOOTH_MANAGER_TAG)
                    .commit();
        } else {
            bluetoothManager = (BluetoothManager) getFragmentManager().findFragmentByTag(BLUETOOTH_MANAGER_TAG);
        }

        //Add listeners to the listView and the buttons.
        listView = (ListView) findViewById(id.pairedDevicesList);

        devicesAdapter = new ArrayAdapter<>(this,
                layout.bt_devices_array_adapter,
                devicesList);

        listView.setAdapter(devicesAdapter);

        //Initialize the devices list with the paired devices.
        if (savedInstanceState == null) {
            String string;
            for (BluetoothDevice device : bluetoothManager.getPairedDevices()) {
                string = device.getName() + "\n" + device.getAddress();
                if (devicesSet.add(string)) {
                    devicesAdapter.add(string);
                }
            }
        }

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

        //Save the MAC of the device that the user selected.
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

        Button join_button = (Button) findViewById(id.join_button);
        Button host_button = (Button) findViewById(id.hostgame_button);
        Button refresh_button = (Button) findViewById(id.refresh_button);

        join_button.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                bluetoothManager.connect(selectedMAC);
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

        bluetoothManager.setConnectListener(new ConnectListener() {
            @Override
            public void onConnected(boolean connected, String deviceName) {
                if (connected) {
                    Toast.makeText(getApplicationContext(), "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
                    join();
                }
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to SocketManagerService
        Intent intent = new Intent(this, SocketManagerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);


            //Make sure the bluetooth is enabled. If not, shut down the app.
            bluetoothManager.setBluetoothRequestEnableListener(new BluetoothRequestEnableListener() {
                @Override
                public void onResult(boolean enabled) {
                    if (!enabled) {
                        Toast.makeText(getApplicationContext(), "Bluetooth is needed for Bluetooth Chat", Toast.LENGTH_LONG).show();
                        bluetoothManager.getActivity().finish();
                    }
                }

                @Override
                public void onEnabled() {

                }
            });
            bluetoothManager.enableBluetooth();


        //If a device is found, add it in the devices list.
        bluetoothManager.setBluetoothGetAvailableDevicesListener(new BluetoothGetAvailableDevicesListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device) {
                String string = device.getName() + "\n" + device.getAddress();
                if (devicesSet.add(string)) {
                    devicesAdapter.add(string);
                }
            }
        });

        bluetoothManager.discoverDevices();
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
    public void onStop() {
        super.onStop();
        unbindService(mConnection);
        //Cancel the bluetooth discovery. If a device is found while the application is stopped, it won't be added in the list!
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
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

    /**
     * Closes down all bluetooth connections.
     */
    public void refresh() {
        if (mBound) {
            mService.clear();
        }
        refreshUUIDs();
        bluetoothManager.discoverDevices();
    }

    private void join() {
        Intent intent = new Intent(this, ChatRoom.class);
        grioanpier.auth.users.bluetoothframework.BluetoothManager.DEVICE_TYPE = grioanpier.auth.users.bluetoothframework.BluetoothManager.PLAYER;
        startActivity(intent);
    }

    private void host() {
        refresh();

        BluetoothManager.DEVICE_TYPE = BluetoothManager.HOST;
        Intent intent = new Intent(this, ChatRoom.class);
        startActivity(intent);
    }

}
