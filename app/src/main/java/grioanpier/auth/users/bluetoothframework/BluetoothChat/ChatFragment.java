package grioanpier.auth.users.bluetoothframework.bluetoothChat;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;

import grioanpier.auth.users.bluetoothframework.BluetoothManager;
import grioanpier.auth.users.bluetoothframework.R;
import grioanpier.auth.users.bluetoothframework.SocketManagerService;

public class ChatFragment extends Fragment {
    //TODO: fragment_bluetooth_chat.xml, context ChatRoom should be ChatFragment?
    private static final int CHAT_GLOBAL = 4;
    private static final int CHAT_PRIVATE = 5;

    private static final String LOG_TAG = ChatFragment.class.getSimpleName();
    private SocketManagerService mService;
    private boolean mBound = false;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private static final String CHAT_KEY = "CHAT_KEY";

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;
    private static ArrayList<String> mConversationArrayList;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);


    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mConversationView = (ListView) view.findViewById(R.id.chat_listview);
        mOutEditText = (EditText) view.findViewById(R.id.edit_text_out);

        if (savedInstanceState != null) {
            String[] ch;
            ch = savedInstanceState.getStringArray(CHAT_KEY);
            if (ch != null) {
                mConversationArrayList = (ArrayList<String>) Arrays.asList(ch);
            }
        } else mConversationArrayList = new ArrayList<>();


        // Initialize the array adapter for the conversation thread using (possibly) the previous chat (restore it)
        mConversationArrayAdapter = new ArrayAdapter<>(getActivity(),
                R.layout.message_chat,
                mConversationArrayList);
        mHandler = new ChatHandler(mConversationArrayAdapter);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(new EditText.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    sendMessage(view.getText().toString());
                    return true;
                }
                return false;
            }
        });
        //Sets the soft keyboard to be hidden when the app starts.
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
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
            SocketManagerService.SocketManagerServiceBinder binder = (SocketManagerService.SocketManagerServiceBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.addHandler(mHandler);

        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
        bundle.putStringArrayList(CHAT_KEY, mConversationArrayList);
    }


    @Override
    public void onStop() {
        super.onStop();
        if (mBound) {
            mService.removeHandler(mHandler);
            getActivity().unbindService(mConnection);
            mBound = false;
        }

    }

    /**
     * Sends a content.
     *
     * @param message A string of text to send.
     */
    private void sendMessage(String message) {
        // Check that there's actually something to send
        if (message.length() > 0) {

            String targetName = null;
            if (message.startsWith("\\w")) {
                String[] splits = message.split("\"");
                targetName = splits[1];
                message = splits[2].trim();
                Log.e(LOG_TAG, "Split was: " +splits[2]);
                Log.e(LOG_TAG, "Message was: " + message);
            }

            String deviceName = BluetoothManager.getName();
            deviceName = SocketManagerService.format(deviceName);

            if (mBound) {
                if (targetName == null) {
                    mService.sendGlobalMessage(deviceName + message, CHAT_GLOBAL);
                } else {
                    String targetMAC = mService.getMAC(targetName);
                    if (targetMAC == null) {
                        mService.sendPrivateMessage(deviceName + message, targetName, CHAT_PRIVATE);
                    } else {
                        mService.sendPrivateMessage(deviceName + message, targetMAC, CHAT_PRIVATE);
                    }
                    mConversationArrayAdapter.add("whisperTo(" + targetName + "): " + message);

                }
            }

            mOutEditText.setText("");
            mConversationView.setSelection(mConversationArrayAdapter.getCount() - 1);
        }
    }

    /**
     * The Handler that gets the messages. The messages are first received in {@link SocketManagerService} and forwarded to every available handler
     */
    private ChatHandler mHandler;

    private static class ChatHandler extends Handler {
        final WeakReference<ArrayAdapter<String>> mConversationArrayAdapterWeakReference;

        ChatHandler(ArrayAdapter<String> conversationArrayAdapter) {
            mConversationArrayAdapterWeakReference = new WeakReference<>(conversationArrayAdapter);
        }

        @Override
        public void handleMessage(Message msg) {
            Log.i(LOG_TAG, "Chat Handler start!");

            if (msg.what != CHAT_GLOBAL && msg.what != CHAT_PRIVATE) {
                Log.i(LOG_TAG, "This isn't for me");
                return;
            }

            String message = (String) msg.obj;
            Log.i(LOG_TAG, message);

            //Extract the device name
            int length = SocketManagerService.deformat(message);
            String deviceName = message.substring(3, length + 3);
            message = message.substring(length + 3, message.length());



            if (msg.what == CHAT_GLOBAL) {
                if (deviceName.equals(BluetoothManager.getName()))
                    deviceName = "You";
                mConversationArrayAdapterWeakReference.get().add(deviceName + ": " + message);
            } else {
                mConversationArrayAdapterWeakReference.get().add("whisperFrom(" + deviceName + "): " + message);
            }


            Log.i(LOG_TAG, "Chat Handler end!");
        }
    }


}//Fragment
