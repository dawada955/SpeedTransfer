package com.loader.speedtransfer.net;

import static com.loader.speedtransfer.field.CustomField.SERVICE_PORT;

import android.util.Log;

import com.loader.speedtransfer.utils.NetworkUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpHeartbeatSender {
    private static final String TAG = "UdpHeartbeatSender";

    private boolean running = false;
    private Thread sendThread;

    public void startHeartbeat(String heartBeatSign, String pcIp, int port, String deviceName) {
        running = true;
        sendThread = new Thread(() -> {
            try {
                DatagramSocket socket = new DatagramSocket();
                InetAddress pcAddress = InetAddress.getByName(pcIp);
                byte[] data = (heartBeatSign + "|" + deviceName + "|" + NetworkUtils.getLocalIpAddress() + "|" + SERVICE_PORT).getBytes();
                Log.d("dv", deviceName);
                while (running) {
                    DatagramPacket packet = new DatagramPacket(data, data.length, pcAddress, port);
                    socket.send(packet);
                    Log.d(TAG, "Heartbeat sent to " + pcIp + ":" + port);
                    Thread.sleep(1000);  // 每秒发送一次
                }
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        sendThread.start();
    }

    public void stopHeartbeat() {
        running = false;
        if (sendThread != null) {
            sendThread.interrupt();
        }
    }
}
