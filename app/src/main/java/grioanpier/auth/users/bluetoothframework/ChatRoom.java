package grioanpier.auth.users.bluetoothframework;

import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

public class ChatRoom extends Activity{

    private static final String LOG_TAG = ChatRoom.class.getSimpleName();

    private static int deviceType;
    private boolean hasPromptedDiscoverable = false;
    private final String hasPromptedDiscoverableString = "DiscoverablePrompts";

    private static PlaceholderFragment waitingScreenFragment;
    private ChatFragment bluetoothChatFragment;
    private BluetoothManager btManager;
    private static final String sBluetoothManagerFragmentTag = "bluetoothmanager";
    private static boolean mTwoPane = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_room);

        deviceType = ApplicationHelper.getInstance().DEVICE_TYPE;
        //Those two fragments have been statically added inside the activity's xml!
        bluetoothChatFragment = (ChatFragment) getFragmentManager().findFragmentById(R.id.chat_fragment);
        waitingScreenFragment = (PlaceholderFragment) getFragmentManager().findFragmentById(R.id.waiting_screen_fragment);
        waitingScreenFragment.setUserVisibleHint(false);


        //Not needed.
        mTwoPane=false;

        if (savedInstanceState == null) {
            btManager = new BluetoothManager();
            getFragmentManager().beginTransaction()
                    .add(btManager, sBluetoothManagerFragmentTag)
                    .commit();


            ApplicationHelper.twoPane = mTwoPane;

        } else {
            btManager = (BluetoothManager) getFragmentManager().findFragmentByTag(sBluetoothManagerFragmentTag);
            hasPromptedDiscoverable = savedInstanceState.getBoolean(hasPromptedDiscoverableString);
        }


    }


    @Override
    public void onStart() {
        super.onStart();

        switch (deviceType) {

            case Constants.DEVICE_HOST: {
                //Make sure the Bluetooth is enabled. When it is, start listening for incoming connections.
                btManager.setBluetoothRequestEnableListener(new BluetoothManager.BluetoothRequestEnableListener() {
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
                        if (!ApplicationHelper.getInstance().GAME_HAS_STARTED) {
                            //Start listening for incoming connections as soon as the bluetooth is enabled if the game hasn't started
                            serverListenForConnections();
                        }
                    }
                });
                btManager.ensureEnabled();

                //Prompt the user to make the device discoverable
                btManager.setBluetoothRequestDiscoverableListener(new BluetoothManager.BluetoothRequestDiscoverableListener() {
                    @Override
                    public void onResult(boolean enabled) {
                        if (!enabled)
                            Toast.makeText(getApplicationContext(), "Non-paired devices won't be able to find you", Toast.LENGTH_SHORT).show();
                    }
                });

                if (!ApplicationHelper.getInstance().GAME_HAS_STARTED && !hasPromptedDiscoverable) {
                    btManager.ensureDiscoverable();
                    hasPromptedDiscoverable = true;
                }

                break;
            }
        }

        ApplicationHelper.addHanlder(mHandler);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        ApplicationHelper.removeHandler(mHandler);
    }

    private static final int[] menuIDs = new int[]{
            Menu.FIRST,
            Menu.FIRST + 1
    };

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.clear();
        if (!ApplicationHelper.getInstance().GAME_HAS_STARTED) {
            menu.add(0, menuIDs[0], Menu.NONE, R.string.ensure_discoverable);
        }
        if (mTwoPane && ApplicationHelper.getInstance().GAME_HAS_STARTED) {
            menu.add(0, menuIDs[1], Menu.NONE, R.string.save_story);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        String title = item.getTitle().toString();

        if (title.equals(getString(R.string.ensure_discoverable))) {
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

    private void serverListenForConnections() {
        btManager.setServerListenForConnectionsListener(new BluetoothManager.ServerListenForConnectionsListener() {
            @Override
            public void onConnectionEstablished(boolean established, String name) {
                if (established) {
                    Toast.makeText(getApplicationContext(), "Connected with " + name, Toast.LENGTH_SHORT).show();
                    ApplicationHelper.removeNextAvailableUUID();
                    waitingScreenFragment.playersJoinedIncrement();
                } else {
                    Toast.makeText(getApplicationContext(), "Connection NOT Established!", Toast.LENGTH_SHORT).show();
                }
                UUID uuid = ApplicationHelper.getNextAvailableUUID();
                if (uuid != null) {
                    btManager.prepareServerListenForConnections();
                    btManager.serverListenForConnections(uuid);
                }
            }
        });

        UUID uuid = ApplicationHelper.getNextAvailableUUID();
        if (uuid != null)
            btManager.serverListenForConnections(uuid);
    }


    private ActivityHandler mHandler = new ActivityHandler(this);

    public static class ActivityHandler extends Handler {

        static Context mContext;

        ActivityHandler(Context context) {
            super();
            mContext = context;
        }

        @Override
        public synchronized void handleMessage(Message msg) {

            switch (msg.what) {
                case ApplicationHelper.PLAYER_CONNECTED:
                    if (deviceType != Constants.DEVICE_HOST)
                        waitingScreenFragment.playersJoinedIncrement();
                    break;
                case ApplicationHelper.PLAYER_DISCONNECTED:
                    Toast.makeText(mContext, msg.obj + " disconnected", Toast.LENGTH_SHORT).show();
                    waitingScreenFragment.playersJoinedDecrement();
                    break;
                case ApplicationHelper.ACTIVITY_CODE:
                    String message = (String) msg.obj;

                    int mySwitch = message.charAt(0) - 48;
                    switch (mySwitch) {
                        //Receive the code to start the game
                        case ApplicationHelper.START_GAME:
                            //not applicable
                            break;
                        default:
                            break;

                    } // mySwitch
                    break;

                default:
                    break;
            }//msg.what
        }
    }


    public static class PlaceholderFragment extends Fragment {
        private Button mButton;
        private TextView mPlayersJoinedTextView;
        private TextView mWaitingForHost;
        private TextView mMacTextView;
        private final String LOG_TAG = ChatFragment.class.getSimpleName();

        private int deviceType;
        private int mPlayersJoined = 1;
        private static final String PLAYERS_IN_ROOM = "players in room";
        private static final int PLAYERS_JOINED_STRING_ID = R.string.playersJoined;
        private static final int MAC_DISPLAYED = R.string.your_mac_is;

        public PlaceholderFragment() {}

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            deviceType = ApplicationHelper.getInstance().DEVICE_TYPE;

            if (savedInstanceState != null) {
                mPlayersJoined = savedInstanceState.getInt(PLAYERS_IN_ROOM);
            }
            View rootView = inflater.inflate(R.layout.fragment_chat_room_placeholder, container, false);
            mButton = (Button) rootView.findViewById(R.id.button_start_game);
            mPlayersJoinedTextView = (TextView) rootView.findViewById(R.id.players_joined);
            mWaitingForHost = ((TextView) rootView.findViewById(R.id.waitingForHost));
            mMacTextView = ((TextView) rootView.findViewById(R.id.mac));

            return rootView;
        }

        @Override
        public void onStart() {
            super.onStart();

            if (ApplicationHelper.getInstance().GAME_HAS_STARTED) {
                mPlayersJoinedTextView.setVisibility(View.GONE);
                mWaitingForHost.setVisibility(View.GONE);
                mButton.setText(R.string.resume_story);
                mButton.setVisibility(View.VISIBLE);
                mMacTextView.setVisibility(View.GONE);
            } else {
                switch (deviceType) {
                    case Constants.DEVICE_HOST:
                        mPlayersJoinedTextView.setText(getActivity().getString(PLAYERS_JOINED_STRING_ID, mPlayersJoined));
                        mMacTextView.setText(getActivity().getString(MAC_DISPLAYED, BluetoothAdapter.getDefaultAdapter().getAddress()));
                        break;
                    default:
                        mPlayersJoinedTextView.setVisibility(View.GONE);
                        mButton.setVisibility(View.GONE);
                        mMacTextView.setVisibility(View.GONE);
                        break;
                }
            }
        }

        @Override
        public void onSaveInstanceState(Bundle outState) {
            super.onSaveInstanceState(outState);
            outState.putInt(PLAYERS_IN_ROOM, mPlayersJoined);
        }

        public void playersJoinedIncrement() {
            mPlayersJoined++;
            mPlayersJoinedTextView.setText(getActivity().getString(PLAYERS_JOINED_STRING_ID, mPlayersJoined));
        }

        public void playersJoinedDecrement() {
            mPlayersJoined--;
            mPlayersJoinedTextView.setText(getActivity().getString(PLAYERS_JOINED_STRING_ID, mPlayersJoined));
        }

    }


}//Activity

