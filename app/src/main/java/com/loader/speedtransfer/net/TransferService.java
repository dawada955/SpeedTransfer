package com.loader.speedtransfer.net;

import static com.loader.speedtransfer.utils.NetworkUtils.getLocalIpAddress;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.loader.speedtransfer.R;
import com.loader.speedtransfer.field.CustomField;
import com.loader.speedtransfer.ui.ChatCallback;
import com.loader.speedtransfer.ui.ChatMessage;
import com.loader.speedtransfer.ui.MainActivity;
import com.loader.speedtransfer.utils.DeviceSettingUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class TransferService extends Service implements ChatCallback {
    private static final String TAG = "TransferService";
    private static final String CHANNEL_ID = "speed_transfer_channel";
    private static final int NOTIFICATION_ID = 1001;

    private AndroidHttpServer httpServer;
    private NsdHelper nsdHelper;
    private UdpHeartbeatSender heartbeatSender;
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private ChatCallback activityCallback;
    private final List<Runnable> pendingMessages = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "TransferService onCreate");
        createNotificationChannel();
        acquireLocks();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "TransferService onStartCommand");

        if (httpServer == null) {
            startServer();
        }

        startForeground(NOTIFICATION_ID, buildNotification(getLocalIpAddress()));

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new TransferBinder();
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "TransferService onDestroy");
        stopServer();
        releaseLocks();
        super.onDestroy();
    }

    public class TransferBinder extends android.os.Binder {
        public TransferService getService() {
            return TransferService.this;
        }
    }

    public void setActivityCallback(ChatCallback callback) {
        this.activityCallback = callback;
        if (httpServer != null) {
            String ip = getLocalIpAddress();
            callback.onDisplayNetwork(ip, CustomField.SERVICE_PORT);
        }
        // 刷新缓冲的 WS 消息
        for (Runnable r : pendingMessages) {
            r.run();
        }
        pendingMessages.clear();
    }

    public void clearActivityCallback() {
        this.activityCallback = null;
    }

    public void sendChatMessage(String content) {
        if (httpServer != null) {
            new Thread(() -> httpServer.ws_handleBroadcastMessage(content)).start();
        }
    }

    public boolean isServerRunning() {
        return httpServer != null;
    }

    private void startServer() {
        try {
            httpServer = new AndroidHttpServer(this, this);
            String ip = getLocalIpAddress();
            Log.i(TAG, "HTTP/WebSocket 服务器已启动: ws://" + ip + ":" + CustomField.SERVICE_PORT);

            heartbeatSender = new UdpHeartbeatSender();
            nsdHelper = new NsdHelper(this, (heartBeatSign, serviceName, hostAddress, port) -> {
                Log.d(TAG, "发现 PC: " + hostAddress + ":" + port);
                heartbeatSender.startHeartbeat(heartBeatSign, hostAddress, port, DeviceSettingUtils.getDeviceName());
            });
            nsdHelper.discoverServices();

            onDisplayNetwork(ip, CustomField.SERVICE_PORT);
        } catch (IOException e) {
            Log.e(TAG, "服务器启动失败", e);
        }
    }

    private void stopServer() {
        if (httpServer != null) {
            httpServer.stop();
            httpServer = null;
        }
        if (nsdHelper != null) {
            nsdHelper.stopDiscovery();
            nsdHelper = null;
        }
        if (heartbeatSender != null) {
            heartbeatSender.stopHeartbeat();
            heartbeatSender = null;
        }
    }

    private void acquireLocks() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SpeedTransfer::WakeLock");
        wakeLock.acquire();

        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wm != null) {
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "SpeedTransfer::WifiLock");
            wifiLock.acquire();
        }
    }

    private void releaseLocks() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            wakeLock = null;
        }
        if (wifiLock != null && wifiLock.isHeld()) {
            wifiLock.release();
            wifiLock = null;
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "传输服务",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("SpeedTransfer 后台传输服务");
            channel.setShowBadge(false);
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String ip) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String content = (ip != null && !ip.isEmpty()) ? ip + ":" + CustomField.SERVICE_PORT : "服务运行中";

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.speed_transfer_pink)
                .setContentTitle("微传 · 传输中")
                .setContentText(content)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .build();
    }

    private void updateNotification(String ip) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, buildNotification(ip));
        }
    }

    @Override
    public void onWsUploadStart(String filename, String ip) {
        if (activityCallback != null) {
            activityCallback.onWsUploadStart(filename, ip);
        } else {
            pendingMessages.add(() -> activityCallback.onWsUploadStart(filename, ip));
        }
    }

    @Override
    public void onWsUploadProgress(String filename, int progress, String speed, long bytes, int remaining) {
        if (activityCallback != null) {
            activityCallback.onWsUploadProgress(filename, progress, speed, bytes, remaining);
        }
        // 进度消息不缓冲，活动恢复后通过后续消息更新
    }

    @Override
    public void onReceiveMessage(String ip, String content) {
        if (activityCallback != null) {
            activityCallback.onReceiveMessage(ip, content);
        }
    }

    @Override
    public void onDisplayNetwork(String ip, int port) {
        updateNotification(ip);
        if (activityCallback != null) {
            activityCallback.onDisplayNetwork(ip, port);
        }
    }

    @Override
    public void onReceiveFileMessage(ChatMessage fileMessage, File file) {
        if (activityCallback != null) {
            activityCallback.onReceiveFileMessage(fileMessage, file);
        }
    }

    @Override
    public void onReceiveFileMessageError(ChatMessage fileMessage) {
        if (activityCallback != null) {
            activityCallback.onReceiveFileMessageError(fileMessage);
        }
    }

    @Override
    public void onReceiveFileMessageProgress(ChatMessage fileMessage, int progress, long size) {
        if (activityCallback != null) {
            activityCallback.onReceiveFileMessageProgress(fileMessage, progress, size);
        }
    }

    @Override
    public void onReceiveFileMessageComplete(ChatMessage fileMessage, long size) {
        if (activityCallback != null) {
            activityCallback.onReceiveFileMessageComplete(fileMessage, size);
        }
    }
}
