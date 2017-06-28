package grioanpier.auth.users.bluetoothframework.bluetoothChat;
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
import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import grioanpier.auth.users.bluetoothframework.BluetoothManager;
import grioanpier.auth.users.bluetoothframework.BluetoothManager.BluetoothRequestDiscoverableListener;
import grioanpier.auth.users.bluetoothframework.BluetoothManager.BluetoothRequestEnableListener;
import grioanpier.auth.users.bluetoothframework.BluetoothManager.ServerListenForConnectionsListener;
import grioanpier.auth.users.bluetoothframework.R;
import grioanpier.auth.users.bluetoothframework.R.id;
import grioanpier.auth.users.bluetoothframework.R.layout;
import grioanpier.auth.users.bluetoothframework.R.string;
import grioanpier.auth.users.bluetoothframework.SocketManagerService;
import grioanpier.auth.users.bluetoothframework.SocketManagerService.SocketManagerServiceBinder;

public class ChatRoom extends Activity {

    private SocketManagerService mService;
    private boolean mBound = false;

    private boolean hasPromptedDiscoverable = false;
    private final String hasPromptedDiscoverableString = "DiscoverablePrompts";

    private PlaceholderFragment waitingScreenFragment;
    private BluetoothManager btManager;
    private static final String sBluetoothManagerFragmentTag = "bluetoothManager";

    private ActivityHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout.activity_chat_room);
        getWindow().setBackgroundDrawableResource(R.drawable.chat);
        //This fragment has been statically added inside the activity's xml!
        waitingScreenFragment = (PlaceholderFragment) getFragmentManager().findFragmentById(id.waiting_screen_fragment);
        waitingScreenFragment.setUserVisibleHint(false);
        mHandler = new ActivityHandler(this, waitingScreenFragment);

        if (savedInstanceState == null) {
            btManager = new BluetoothManager();
            getFragmentManager().beginTransaction()
                    .add(btManager, sBluetoothManagerFragmentTag)
                    .commit();

        } else {
            btManager = (BluetoothManager) getFragmentManager().findFragmentByTag(sBluetoothManagerFragmentTag);
            hasPromptedDiscoverable = savedInstanceState.getBoolean(hasPromptedDiscoverableString);
        }


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(string.ensure_discoverable);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        String title = item.getTitle().toString();

        if (title.equals(getString(string.ensure_discoverable))) {
            btManager.ensureDiscoverable();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putBoolean(hasPromptedDiscoverableString, hasPromptedDiscoverable);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Bind to SocketManagerService
        Intent intent = new Intent(getApplicationContext(), SocketManagerService.class);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        if (BluetoothManager.DEVICE_TYPE == BluetoothManager.HOST) {
                //Make sure the Bluetooth is enabled. When it is, start listening for incoming connections.
                btManager.setBluetoothRequestEnableListener(new BluetoothRequestEnableListener() {
                    @Override
                    public void onResult(boolean enabled) {
                        if (!enabled) {
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            startActivity(intent);
                            finish();
                        }
                    }

                    @Override
                    public void onEnabled() {
                        //Start listening for incoming connections as soon as the bluetooth is enabled if the device is the host
                        serverListenForConnections();

                    }
                });
                btManager.enableBluetooth();

                //Prompt the user to make the device discoverable
                btManager.setBluetoothRequestDiscoverableListener(new BluetoothRequestDiscoverableListener() {
                    @Override
                    public void onResult(boolean enabled) {
                        if (!enabled)
                            Toast.makeText(getApplicationContext(), "Non-paired devices won't be able to find you", Toast.LENGTH_SHORT).show();
                    }
                });

                if (!hasPromptedDiscoverable) {
                    btManager.ensureDiscoverable();
                    hasPromptedDiscoverable = true;
                }


        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mBound) {
            mBound = false;
            mService.removeHandler(mHandler);
            unbindService(mConnection);
        }
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
            mService.addHandler(mHandler);
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    private void serverListenForConnections() {
        btManager.setServerListenForConnectionsListener(new ServerListenForConnectionsListener() {
            @Override
            public void onConnectionEstablished(boolean established, String name) {
                if (established) {
                    Toast.makeText(getApplicationContext(), "Connected with " + name, Toast.LENGTH_SHORT).show();
                    waitingScreenFragment.playersJoinedIncrement();
                }
            }
        });
        btManager.serverListenForConnections(true);
    }

    public static class ActivityHandler extends Handler {
        private final WeakReference<ChatRoom> contextWeakReference;
        private final WeakReference<PlaceholderFragment> waitingScreenFragment;

        ActivityHandler(ChatRoom context, PlaceholderFragment waitingScreen) {
            contextWeakReference = new WeakReference<>(context);
            waitingScreenFragment = new WeakReference<>(waitingScreen);
        }

        @Override
        public synchronized void handleMessage(Message msg) {
            switch (msg.what) {
                case SocketManagerService.THREAD_DISCONNECTED:
                    Toast.makeText(contextWeakReference.get(), msg.obj + " disconnected", Toast.LENGTH_SHORT).show();
                    waitingScreenFragment.get().playersJoinedDecrement();
                    break;
                default:
                    break;
            }
        }
    }

    public static class PlaceholderFragment extends Fragment {

        private TextView mPlayersJoinedTextView;
        private TextView mMacTextView;
        private int mPlayersJoined = 1;
        private static final String PLAYERS_IN_ROOM = "players in room";
        private static final int MAC_DISPLAYED = string.your_mac_is;

        public PlaceholderFragment() {
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {

            if (savedInstanceState != null) {
                mPlayersJoined = savedInstanceState.getInt(PLAYERS_IN_ROOM);
            }
            View rootView = inflater.inflate(layout.fragment_chat_room_placeholder, container, false);
            mPlayersJoinedTextView = (TextView) rootView.findViewById(id.players_joined);
            mPlayersJoinedTextView.setText(getResources().getString(string.playersJoined, mPlayersJoined));
            mMacTextView = ((TextView) rootView.findViewById(id.mac));

            return rootView;
        }

        @Override
        public void onStart() {
            super.onStart();

            switch (BluetoothManager.DEVICE_TYPE) {
                case BluetoothManager.HOST:
                    mPlayersJoinedTextView.setText(getResources().getString(string.playersJoined, mPlayersJoined));
                    mMacTextView.setText(getActivity().getString(MAC_DISPLAYED, BluetoothManager.getMACAddress()));
                    break;
                default:
                    mPlayersJoinedTextView.setVisibility(View.GONE);
                    mMacTextView.setVisibility(View.GONE);
                    break;
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(PLAYERS_IN_ROOM, mPlayersJoined);
        }

        public void playersJoinedIncrement() {
            mPlayersJoined++;
            mPlayersJoinedTextView.setText(getResources().getString(string.playersJoined, mPlayersJoined));
        }

        public void playersJoinedDecrement() {
            mPlayersJoined--;
            mPlayersJoinedTextView.setText(getResources().getString(string.playersJoined, mPlayersJoined));
        }

    }


}//Activity

