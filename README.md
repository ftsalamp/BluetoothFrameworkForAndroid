# Bluetooth Framework For Android
Α framework for easily connecting **multiple** Android devices via bluetooth and sending String messages (not chat messages, anything really). Right now it can support 10 devices.

* Enable the device's bluetooth.
* Make the device discoverable.
* Discover other devices.
* Connect with other devices.
* Send String messages.

#How it works
One device takes up the role of the host. The other devices connect to the host. Behind the scenes, every message is sent to the host who appropriately forwards it.  

#How to use
1) Add BluetoothManager to your activity.
```java
@Override
protected void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  if (savedInstanceState == null) {
    getFragmentManager().beginTransaction()
    .add(new BluetoothManager(), "Bluetooth Manager Tag")
    .commit();
  }
}
```
2)Use BluetoothManager to be discovered/discover other nearby devices.
```java
//Discover other devices
bluetoothManager.ensureDiscoverable(); //The device is now discoverable by other devices
//Be discovered
bluetoothManager.setBluetoothGetAvailableDevicesListener(new BluetoothGetAvailableDevicesListener() { //Set the listener
  @Override
  public void onDeviceFound(BluetoothDevice device) { //A device was found.
    addDeviceInList(device);
  }
});
bluetoothManager.getAvailableDevices(); //Start searching for other devices. 
```
3a)Accept incoming connections (host).
```java
bluetoothManager.setServerListenForConnectionsListener(new ServerListenForConnectionsListener() { //Set the listener
    @Override
    public void onConnectionEstablished(boolean established, String name) { //Connected with a device
        if (established) {
            Toast.makeText(context, "Connected with " + name, Toast.LENGTH_SHORT).show();
        }
    }
});
btManager.serverListenForConnections(true); //if true, when a device connects, it will start listening for incoming connections again
```
3b)Connect to the host.
```java
btManager.setConnectListener(new ConnectListener() { //Set the listener
  @Override
  public void onConnected(boolean connected, String deviceName) { //Connected to the device (the host)
    if(connected){
      Toast.makeText(context, "Connected to " + deviceName, Toast.LENGTH_SHORT).show();
    }
  }
});
bluetoothManager.connect(host's MAC); //Connect to the specified MAC (the host)
```
4)Bind to the SocketManagerService to receive and send messages.
```java
private SocketManagerService mService; //You can access the Service's method via this object.
private boolean mBound = false;        //True if the service is bound and you can use it.
//Don't forget to start the service
startService(new Intent(this, SocketManagerService.class));

// Defines callbacks for service binding, passed to bindService()
private final ServiceConnection mConnection = new ServiceConnection() {
  @Override
  public void onServiceConnected(ComponentName className, IBinder service) {
    SocketManagerServiceBinder binder = (SocketManagerServiceBinder) service;
    mService = binder.getService();
    mBound = true;
    mService.addHandler(mHandler); //Messages are received through the handler. More in a bit.
  }
  @Override
  public void onServiceDisconnected(ComponentName arg0) {
    mBound = false;
  }
};

// Bind to SocketManagerService onStart
@Override
public void onStart() {
    super.onStart();
    Intent intent = new Intent(context, SocketManagerService.class);
    context.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
}

//Unbind onStop.
@Override
public void onStop() {
  super.onStop();
  if (mBound) {
    mService.removeHandler(mHandler);
    context.unbindService(mConnection);
    mBound = false;
  }
}
```
5)Send messages
```java
//Global message, to every device, including the sending device.
 mService.sendGlobalMessage(message, appCode); //int appCode is user defined.

//Private message, send only to the specified device.
String targetMAC = mService.getMAC(targetName); //Gets the MAC from the list of connected devices.
if (targetMAC == null) { //If the device isn't the host, getMAC will return null
    mService.sendPrivateMessage(message, targetName, CHAT_PRIVATE); //The host will search for the correct device.
} else {
    mService.sendPrivateMessage(message, targetMAC, CHAT_PRIVATE); //targetMAC is known, send the message.
}
```
6)Receive messages
```java
private MyHandler mHandler;
private static class ChatHandler extends Handler {
  final WeakReference<T> weakRef; //Use this if you want to refer to an object in your activity or fragment.
                                  //ie. if you were making a chat app, you could put your chat adapter.
  ChatHandler(T object) {
    weakRef = new WeakReference<>(object);
  }
  @Override
    public void handleMessage(Message msg) {
      if (msg.what != appCode) return; //Ignore the appCodes that aren't relevenant.
      String message = (String) msg.obj;
    }
  }
```
