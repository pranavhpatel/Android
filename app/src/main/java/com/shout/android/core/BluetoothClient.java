package com.shout.android.core;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.content.Context;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.util.Log;

import com.bridgefy.sdk.client.BFEnergyProfile;
import com.bridgefy.sdk.client.BFEngineProfile;
import com.bridgefy.sdk.client.Bridgefy;
import com.bridgefy.sdk.client.BridgefyClient;
import com.bridgefy.sdk.client.Config;
import com.bridgefy.sdk.client.Device;
import com.bridgefy.sdk.client.Message;
import com.bridgefy.sdk.client.MessageListener;
import com.bridgefy.sdk.client.RegistrationListener;
import com.bridgefy.sdk.client.Session;
import com.bridgefy.sdk.client.StateListener;
import com.shout.android.ChatMessage;
import com.shout.android.R;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

public class BluetoothClient {

    private ArrayList<ChatMessageListener> chatMessageListeners;
    private ArrayList<ConnectionListener> connectionListeners;
    private HashMap<String, Device> deviceMap;
    private String username = Build.MODEL;
    private String userID = "";

    public int getDevicesConnected() {
        return devicesConnected + 1;
    }

    private int devicesConnected = 0;
    private boolean isConnected = false;
    private boolean isStarted = false;
    private static final BluetoothClient INSTANCE = new BluetoothClient();
    private ForegroundBackgroundListener foregroundBackgroundListener;

    public static BluetoothClient getInstance() {
        return INSTANCE;
    }

    private BluetoothClient(){
        foregroundBackgroundListener = new ForegroundBackgroundListener();
        chatMessageListeners = new ArrayList<>();
        connectionListeners = new ArrayList<>();
        deviceMap = new HashMap<>();

    }


    public ForegroundBackgroundListener getForegroundBackgroundListener() {
        return foregroundBackgroundListener;
    }

    public String getUserID() {
        return userID;
    }

    public void registerChatMessageListener(ChatMessageListener listener){
        chatMessageListeners.add(listener);
    }

    public void unRegisterChatMessageListener(ChatMessageListener listener){
        chatMessageListeners.remove(listener);
    }

    public void registerConnectionListener(ConnectionListener listener){
        connectionListeners.add(listener);
    }

    public void unRegisterConnectionListener(ConnectionListener listener){
        connectionListeners.remove(listener);
    }

    /**
     * Initialize and tries to start the BluetoothClient.
     * If start fails due to permissions having to be requested
     * {@link Activity#onRequestPermissionsResult(int, String[], int[])} will be called with a request code of 0 on the calling activity
     * In that method call {@link #startScanning(Activity)} to start the BluetoothClient
     *
     * @param context  the context of the calling activity
     * @param activity the calling activity
     */
    public void initialize(Context context, Activity activity) {
        Bridgefy.initialize(context, new RegistrationListener() {
            @Override
            public void onRegistrationSuccessful(BridgefyClient bridgefyClient) {
                userID = bridgefyClient.getUserUuid();
                startScanning(activity);
            }

            @Override
            public void onRegistrationFailed(int errorCode, String message) {

            }
        });
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Connect client to mesh
     */
    public void connect() {
        isConnected = true;
        HashMap<String, Object> map = new HashMap<>();
        map.put("type", MessageType.ConnectMessage.getValue());
        map.put("username", username);
        map.put("content", "");
        deviceMap.forEach((id, device) -> device.sendMessage(map));
    }

    /**
     * Disconnect client from mesh
     */
    public void disconnect() {
        isConnected = true;
        HashMap<String, Object> map = new HashMap<>();
        map.put("type", MessageType.DisconnectMessage.getValue());
        map.put("username", username);
        map.put("content", "");
        deviceMap.forEach((id, device) -> device.sendMessage(map));
    }

    /**
     * Send chatMessage to all connected devices
     *
     * @param chatMessage the message to be sent
     */
    public void sendMessage(ChatMessage chatMessage){
        if (isStarted && isConnected) {
            deviceMap.forEach((id, device) -> {
                device.sendMessage(chatMessage.getMap());
                Log.i("testing", id + ":" + device.getDeviceName() + ":" + chatMessage.getContent());
            });
            Bridgefy.sendBroadcastMessage(chatMessage.getMap());
        }

        chatMessageListeners.forEach(listener -> listener.onChatMessageReceived(chatMessage));
    }


    private Notification createNotification(Activity activity, ChatMessage chatMessage) {
        Context c = activity.getApplicationContext();
        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(activity.getApplicationContext(), "com.shout.android.notifications")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Message from " + chatMessage.getUsername())
                .setContentText(chatMessage.getContent())
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        mBuilder.setOnlyAlertOnce(true);
        if (c.getSharedPreferences(c.getString(R.string.pref_file_key), Context.MODE_PRIVATE)
                .getBoolean(c.getString(R.string.pref_file_vibrate), true)) {
            mBuilder.setVibrate(new long[]{500, 100, 500});
        }
        if (c.getSharedPreferences(c.getString(R.string.pref_file_key), Context.MODE_PRIVATE)
                .getBoolean(c.getString(R.string.pref_file_key_message_sound), true)) {
            Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            mBuilder.setSound(alarmSound);
        }
        return mBuilder.build();
    }


    /**
     * Should only be called if starting failed due to permissions needing to be requested
     *
     * @param activity the calling activity
     */
    public void startScanning(Activity activity){
        Config.Builder builder = new Config.Builder();
        builder.setEngineProfile(BFEngineProfile.BFConfigProfileHighDensityNetwork);
        builder.setEnergyProfile(BFEnergyProfile.HIGH_PERFORMANCE);
        Bridgefy.start(new MessageListener() {
            @Override
            public void onMessageReceived(Message message) {
                MessageType type = MessageType.getType(((Number) message.getContent().get("type")).intValue());
                switch (type) {
                    case ChatMessage:
                        ChatMessage chatMessage = new ChatMessage(message, type);
                        chatMessageListeners.forEach(listener -> listener.onChatMessageReceived(chatMessage));
                        Context c = activity.getApplicationContext();
                        boolean show = c.getSharedPreferences(c.getString(R.string.pref_file_key), Context.MODE_PRIVATE)
                                .getBoolean(c.getString(R.string.pref_file_key_show_notifications), true);

                        if (show && foregroundBackgroundListener.isBackground()) {
                            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(activity.getApplicationContext());
                            notificationManager.notify((int) new Date().getTime(), createNotification(activity, chatMessage));
                        }
                        break;
                    case ConnectMessage:
                        connectionListeners.forEach(listener -> listener.deviceConnected((String) message.getContent().get("username"), message.getDateSent(), message.getSenderId()));
                        break;
                    case DisconnectMessage:
                        connectionListeners.forEach(listener -> listener.deviceLost((String) message.getContent().get("username"), message.getDateSent(), message.getSenderId()));
                        break;
                }

            }

        }, new StateListener() {
            @Override
            public void onStarted() {
                isStarted = true;
                Log.e("testing", "started");
            }

            @Override
            public void onStartError(String message, int errorCode) {
                if (errorCode == StateListener.INSUFFICIENT_PERMISSIONS) {
                    ActivityCompat.requestPermissions(activity,
                            new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0);
                }
            }

            @Override
            public void onStopped() {
                isStarted = false;
            }

            @Override
            public void onDeviceConnected(Device device, Session session) {
                deviceMap.put(device.getUserId(), device);
                devicesConnected++;
                connectionListeners.forEach(listener -> listener.connectedDeviceCountChanged(devicesConnected + 1));
            }

            @Override
            public void onDeviceLost(Device device) {
                deviceMap.remove(device.getUserId());
                devicesConnected--;
                connectionListeners.forEach(listener -> listener.connectedDeviceCountChanged(devicesConnected + 1));
            }
        }, builder.build());
    }

}
