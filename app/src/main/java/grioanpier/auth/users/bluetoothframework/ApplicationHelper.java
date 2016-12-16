package grioanpier.auth.users.bluetoothframework;

import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeMap;
import java.util.UUID;

import grioanpier.auth.users.bluetoothframework.Loaders.ConnectedThread;

/**
 * This is the Boss https://www.youtube.com/watch?v=NisCkxU544c
 */
public class ApplicationHelper extends Application {
    private static final ArrayList<UUID> sAvailableUUIDs = new ArrayList<>(Arrays.asList(Constants.sUUIDs));
    private SocketManagerService socketManagerService;
    private static ApplicationHelper singleton;
    private final static String LOG_TAG = ApplicationHelper.class.getSimpleName();
    private static ApplicationHandler applicationHandler;
    public static String DEVICE_NAME = BluetoothAdapter.getDefaultAdapter().getName();
    public boolean isHost = false;
    public int DEVICE_TYPE;
    public static boolean GAME_HAS_STARTED = false;
    private int whoIsPlaying;
    public static boolean myTurn = false;
    public static String STORY_HEAD;
    public static boolean twoPane = false;

    public int getWhoIsPlaying() {
        return whoIsPlaying;
    }

    public static ApplicationHelper getInstance() {
        return singleton;
    }

    public static UUID getNextAvailableUUID() {
        if (sAvailableUUIDs.isEmpty())
            return null;
        else
            return sAvailableUUIDs.get(0);
    }

    /**
     * @return true if there was something to remove, false otherwise.
     */
    public static boolean removeNextAvailableUUID() {
        if (sAvailableUUIDs.isEmpty())
            return false;
        else {
            sAvailableUUIDs.remove(0);
            return true;
        }
    }

    //Clears everything.
    public void prepareNewGame() {
        applicationHandler = new ApplicationHandler(getApplicationContext(), socketManagerService);

        sAvailableUUIDs.clear();
        sAvailableUUIDs.ensureCapacity(10);
        sAvailableUUIDs.addAll(Arrays.asList(Constants.sUUIDs));

        if (socketManagerService!=null)
            socketManagerService.clear();

        isHost = false;
        GAME_HAS_STARTED = false;
        whoIsPlaying = -1;
        myTurn = false;
        DEVICE_TYPE = -1;

        story = new ArrayList<>();
        chat = new ArrayList<>();

    }

    //Prepares what is needed for the new Story.
    public void prepareNewStory() {
        //The host is positioned at -1
        whoIsPlaying = -1;
        GAME_HAS_STARTED = true;
        if (isHost)
            myTurn = true;
        story = new ArrayList<>();
    }

    private int getNextPlayer() {
        whoIsPlaying++;
        if (whoIsPlaying == socketManagerService.threadsSize())
            whoIsPlaying = -1;

        return whoIsPlaying;
    }

    public void notifyNextPlayer() {
        int next = getNextPlayer();
        write(String.valueOf(YOUR_TURN), SINGLE_RECEIVER, next);
    }

    /**
     * Called when a player leaves and ensures the correct player is up next.
     */
    private void ensureGameIsPlaying() {
        //Notifying the next player increases the counter internally so it must be decreased first.
        whoIsPlaying--;
        notifyNextPlayer();
    }

    private ServiceConnection mConnection = new ServiceConnection() {

        public void onServiceConnected(ComponentName className,
                                       IBinder binder) {
            Log.i(LOG_TAG, "Service connected!");
            SocketManagerService.MyBinder b = (SocketManagerService.MyBinder) binder;
            socketManagerService = b.getService();
            socketManagerService.clear();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.i(LOG_TAG, "Service disconnected!");
            if (socketManagerService!=null)
                socketManagerService.clear();
            socketManagerService = null;
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        singleton = this;
        //Start the SocketManagerService that holds the various bluetooth threads.
        startService(new Intent(this, SocketManagerService.class));

        Intent intent= new Intent(this, SocketManagerService.class);
        bindService(intent, mConnection,
                Context.BIND_AUTO_CREATE);
    }


    @Override
    public void onTerminate() {
        super.onTerminate();

        Log.e(LOG_TAG, "onTerminate");
        unbindService(mConnection);
    }

    public void addPlayerSocket(BluetoothSocket btSocket) {
        socketManagerService.addPlayerSocket(btSocket, applicationHandler);
    }

    public void setHostSocket(BluetoothSocket btSocket) {
        socketManagerService.setHostSocket(btSocket, applicationHandler);
    }

    public String getHostAddress() {
        return socketManagerService.getHostAddress();
    }

    private static Handler chatHandler;

    public void setChatHandler(Handler handler) {
        chatHandler = handler;
    }

    public void unregisterChatHandler() {
        if (chatHandler != null)
            chatHandler = null;
    }

    private static Handler storyHandler;

    public void setStoryHandler(Handler handler) {
        storyHandler = handler;
    }

    public void unregisterStoryHandler() {
        if (storyHandler != null)
            storyHandler = null;
    }

    private static Handler activityHandler;

    public void setActivityHandler(Handler handler) {
        activityHandler = handler;
    }

    public void unregisterActivityHandler() {
        if (activityHandler != null)
            activityHandler = null;
    }

    public String getStory() {
        StringBuilder builder = new StringBuilder();
        for (String line : story) {
            builder.append(line).append(" ");
        }
        return builder.toString();
    }

    /**
     * Synchronization lock to be used by the write methods in order to avoid possible messages that are sent the same time
     * and things mess up.
     */
    private final static Object Write_Lock = new Object();


    /**
     * Formats the message in the form of [{@param message.size}][{@param message}]. The size of the message should be less than 4 decimals (0-999)
     * For example, "Hello World!" will be formatted to "012Hello World!"
     *
     * @param message The message to format
     * @return The formatted message
     */
    public static String format(String message) {
        StringBuilder builder = new StringBuilder();

        if (message.length() < 10) {
            // 01,02,...09
            builder.append(0).append(0);
        } else if (message.length() < 100) {
            //010, 050, 099
            builder.append(0);
        } else if (message.length() > 999)
            throw new IllegalArgumentException("The size of the message cannot be bigger than 999 characters!");

        builder.append(message.length());
        builder.append(message);

        return builder.toString();

    }

    /**
     * De-formats the message in the form of [code.size][code][string]. The size of the code can be any String, as long as its size is less than 4 decimals (0-999)
     * Use the returned integer to calculate the start and end indexes of the code, which will be at index1=3 and index2=3+size.
     * <p/>
     * For example,
     * String message = "012Hello World!This is a messageBlahBlahBlah";
     * int length = deformat(message);
     * String code = message.substring(3,length+3); // code == "Hello World!"
     * String rest = message.substring(length + 3, message.length()); // rest == "BlahBlahBlah"
     *
     * @param message The message to be de-formatted.
     * @return The size of the code.
     */
    public static int deformat(String message) {
        int int1 = message.charAt(0) - 48;
        int int2 = message.charAt(1) - 48;
        int int3 = message.charAt(2) - 48;

        Log.i(LOG_TAG, "Deformatting " + (100 * int1 + 10 * int2 + int3));

        return (100 * int1 + 10 * int2 + int3);
    }

    /**
     * When the user clicks "send", this method is invoked.
     *
     * @param message the message to send.
     * @param source  the source of the message. It can be {CHAT} or {STORY}
     */
    public synchronized void write(String message, int source) {
        Log.i(LOG_TAG, "Write(" + message + ")");

        synchronized (Write_Lock) {
            //Format the message.
            StringBuilder builder = new StringBuilder();
            builder.append(format(Integer.toString(source)))
                    .append(message);
            byte[] buffer = builder.toString().getBytes();

            if (isHost)
                //The message is relayed, if needed, inside the obtainMessage method.
                applicationHandler.obtainMessage(THREAD_READ, buffer.length, -1, buffer).sendToTarget();
            else
                //This list only has a single thread.
                socketManagerService.writeToAll(builder.toString());
        }
    }


    private synchronized void relay(String message) {
        socketManagerService.writeToAll(message);
    }

    private void write(String message, int source, int threadIndex) {
        synchronized (Write_Lock) {

            StringBuilder builder = new StringBuilder();

            builder.append(format(Integer.toString(source)))
                    .append(message);

            Log.i(LOG_TAG, "Write with threadIndex: " + builder.toString());

            byte[] buffer = builder.toString().getBytes();

            //It's the host turn. This method is only called by the host, therefor send it directly to the host's Handler
            if (threadIndex == -1)
                applicationHandler.obtainMessage(THREAD_READ, buffer.length, -1, buffer).sendToTarget();
            else
                socketManagerService.writeTo(builder.toString(), threadIndex);
        }//Write lock
    }

    public ArrayList<String> story = new ArrayList<>();
    public ArrayList<String> chat = new ArrayList<>();

    public static <T extends Handler> void addHanlder(T handler) {
        applicationHandler.addHandler(handler);
    }

    public static <T extends Handler> void removeHandler(T handler) {
        applicationHandler.removeHandler(handler);
    }

    public static class ApplicationHandler extends Handler {

        private Context mContext;
        private SocketManagerService mSocketManagerService;
        private TreeMap<Integer, Handler> mHandlers;

        public ApplicationHandler(Context context, SocketManagerService socketManagerService) {
            super();
            mContext = context;
            mSocketManagerService = socketManagerService;
            mHandlers = new TreeMap<>();
        }

        private <T extends Handler> void addHandler(T handler) {
            mHandlers.put(handler.hashCode(), handler);
        }

        private <T extends Handler> void removeHandler(T handler) {
            mHandlers.remove(handler.hashCode());
        }


        @Override
        public synchronized void handleMessage(Message msg) {

            switch (msg.what) {
                case THREAD_READ:
                    Log.i(LOG_TAG, "Message received: " + new String((byte[]) msg.obj, 0, msg.arg1));
                    int numOfBytes = msg.arg1;
                    String message = new String((byte[]) msg.obj, 0, numOfBytes);

                    int length = deformat(message);
                    int source = Integer.valueOf(message.substring(3, length + 3));
                    message = message.substring(length + 3, message.length());
                    Log.i(LOG_TAG, "Message: " + message);
                    Log.i(LOG_TAG, "Source: " + source);


                    for (Handler handler : mHandlers.values())
                        handler.obtainMessage(source, message).sendToTarget();

                    //If this isn't meant for just on person, also relay it to the other devices.
                    if (ApplicationHelper.getInstance().isHost && source!=SINGLE_RECEIVER )
                        mSocketManagerService.writeToAll(format(Integer.toString(source)) + message);


                    break;
                case THREAD_DISCONNECTED:
                    Log.i(LOG_TAG, "Thread disconnected! " + msg.arg1);
                    //ConnectedThread calls ConnectedThread.cancel() internally which closes the streams and the socket.
                    //Remove it from the list as well. The thread returns its hash code in msg.arg1
                    mSocketManagerService.removeThread(msg.arg1);

                    //Also remove the socket from our list.
                    String who;
                    if (mSocketManagerService.removePlayerSocket(msg.arg1)) {
                        //The user who left was a player.
                        who = (String) msg.obj;
                        //Check if it was the player's who left turn
                        //and recalculate the next player's turn;
                        ApplicationHelper.getInstance().ensureGameIsPlaying();

                    } else {
                        mSocketManagerService.removeHostSocket();
                        who = "The host";

                        //Wrap up the game
                        ApplicationHelper.getInstance().GAME_HAS_STARTED = false;
                        ApplicationHelper.getInstance().prepareNewGame();

                        //TODO force story save
                        Intent intent = new Intent(mContext, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    }

                    if (activityHandler != null) {
                        //Inform the activity handler who disconnected.
                        activityHandler.obtainMessage(PLAYER_DISCONNECTED, who).sendToTarget();
                    }

                    break;
                case THREAD_STREAM_ERROR:
                    Log.i(LOG_TAG, "Thread Stream Error");
                    Toast.makeText(mContext, "Stream couldn't be retrieved", Toast.LENGTH_SHORT).show();
                    break;
                default:

                    break;
            }

        }

        /*
        @Override
        public synchronized void handleMessage(Message msg) {

            switch (msg.what) {
                case THREAD_READ:
                    Log.i(LOG_TAG, "Message received: " + new String((byte[]) msg.obj, 0, msg.arg1));
                    int numOfBytes = msg.arg1;
                    StringBuilder builder = new StringBuilder(new String((byte[]) msg.obj, 0, numOfBytes));
                    int messageType = builder.charAt(0) - 48;
                    String message;

                    if (ApplicationHelper.getInstance().isHost) {
                        Log.i(LOG_TAG, "isHost");
                        if (messageType == SINGLE_RECEIVER) {
                            Log.i(LOG_TAG, "Host_Only");
                            builder.deleteCharAt(0);
                            messageType = builder.charAt(0) - 48;
                        } else {
                            ApplicationHelper.getInstance().relay(builder.toString());
                        }
                    } else {
                        Log.i(LOG_TAG, "!Host");
                    }

                    switch (messageType) {
                        case CHAT:
                            //The length is saved in the 2nd and 3rd byte.
                            //Reconstruct it: 56 = 5*10 + 6
                            int nameLength = deformat(builder.substring(1));
                            String deviceName = builder.substring(4, nameLength + 3);
                            message = builder.substring(nameLength + 4, builder.length());

                            if (chatHandler != null) {
                                if (deviceName.equals(DEVICE_NAME)) {
                                    chatHandler.obtainMessage(MESSAGE_ME, "You: " + message).sendToTarget();
                                } else {
                                    chatHandler.obtainMessage(MESSAGE_OTHER, deviceName + ": " + message).sendToTarget();
                                }
                            }
                            break;
                        case STORY:
                            if (storyHandler != null)
                                storyHandler.obtainMessage(STORY, builder.substring(1, builder.length())).sendToTarget();
                            break;
                        case STORY_CODE:
                            if (storyHandler != null) //This will be null if the host has left the "Game" screen
                                storyHandler.obtainMessage(STORY_CODE, builder.substring(1, builder.length())).sendToTarget();
                            break;
                        case ACTIVITY_CODE:
                            if (activityHandler != null) {
                                activityHandler.obtainMessage(ACTIVITY_CODE, builder.substring(1, builder.length())).sendToTarget();
                            }
                            break;
                        default:
                            break;
                    }
                    break;
                case THREAD_DISCONNECTED:
                    Log.i(LOG_TAG, "Thread disconnected! " + msg.arg1);
                    //ConnectedThread calls ConnectedThread.cancel() internally which closes the streams and the socket.
                    //Remove it from the list as well. The thread returns its hash code in msg.arg1
                    mSocketManagerService.removeThread(msg.arg1);

                    //Also remove the socket from our list.
                    String who;
                    if (mSocketManagerService.removePlayerSocket(msg.arg1)) {
                        //The user who left was a player.
                        who = (String) msg.obj;
                        //Check if it was the player's who left turn
                        //and recalculate the next player's turn;
                        ApplicationHelper.getInstance().ensureGameIsPlaying();

                    } else {
                        mSocketManagerService.removeHostSocket();
                        who = "The host";

                        //Wrap up the game
                        ApplicationHelper.getInstance().GAME_HAS_STARTED = false;
                        ApplicationHelper.getInstance().prepareNewGame();

                        //TODO force story save
                        Intent intent = new Intent(mContext, StartingScreen.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        mContext.startActivity(intent);
                    }

                    if (activityHandler != null) {
                        //Inform the activity handler who disconnected.
                        activityHandler.obtainMessage(PLAYER_DISCONNECTED, who).sendToTarget();
                    }

                    break;
                case THREAD_STREAM_ERROR:
                    Log.i(LOG_TAG, "Thread Stream Error");
                    Toast.makeText(mContext, "Stream couldn't be retrieved", Toast.LENGTH_SHORT).show();
                    break;
                default:

                    break;
            }

        }
        * */

    }

    //Receive a message from a ConnectedThread.
    private static final int THREAD_READ = ConnectedThread.THREAD_READ;
    //Receive a message from a ConnectedThread that it has disconnected
    private static final int THREAD_DISCONNECTED = ConnectedThread.THREAD_DISCONNECTED;
    //Receive a message from a ConnectedThread that it couldn't retrieve the Input or Output Stream
    private static final int THREAD_STREAM_ERROR = ConnectedThread.THREAD_STREAM_ERROR;

    //Codes for the various handlers.
    public static final int CHAT = 0;
    public static final int STORY = 1;
    public static final int ACTIVITY_CODE = 3;

    /*Codes for the activity handler*/
    public static final int YOUR_TURN = 6; //It's the player's turn to play.
    public static final int PASS = 7; //The player has passed his turn. This is used by spectators.
    public static final int START_GAME = 8; //The host has started the game. Next screen please!
    public static final int SINGLE_RECEIVER = 9; //This is intended only for the host, don't relay!

    //These are provided as int in the msg.arg
    public static final int PLAYER_CONNECTED = 10;
    public static final int PLAYER_DISCONNECTED = 11;




}

