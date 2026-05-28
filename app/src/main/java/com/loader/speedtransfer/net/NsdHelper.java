package com.loader.speedtransfer.net;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import java.nio.charset.StandardCharsets;

public class NsdHelper {
    private static final String TAG = "NsdHelper";
    private static final String SERVICE_TYPE = "_pcservicewt._tcp.";  // 与PC服务名保持一致

    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private NsdManager.ResolveListener resolveListener;

    private ServiceFoundCallback callback;

    public interface ServiceFoundCallback {
        void onServiceFound(String heartBeatSign, String serviceName, String hostAddress, int port);
    }

    public NsdHelper(Context context, ServiceFoundCallback callback) {
        this.nsdManager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.callback = callback;
    }

    public void initializeDiscoveryListener() {
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                Log.d(TAG, "Service discovery success: " + service);
                if (service.getServiceType().equals(SERVICE_TYPE)) {
                    Log.d(TAG, "Found desired service -> Resolving...");
                    nsdManager.resolveService(service, resolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                Log.e(TAG, "Service lost: " + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Stop Discovery failed: Error code:" + errorCode);
                nsdManager.stopServiceDiscovery(this);
            }
        };

        resolveListener = new NsdManager.ResolveListener() {
            @Override
            public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                Log.e(TAG, "Resolve failed: " + errorCode);
            }

            @Override
            public void onServiceResolved(NsdServiceInfo serviceInfo) {
                String hostAddress = serviceInfo.getHost().getHostAddress();
                int port = serviceInfo.getPort();
                String serviceName = serviceInfo.getServiceName();
                String heartBeatSign = new String(serviceInfo.getAttributes().get("heart_beat_sign"), StandardCharsets.UTF_8);
                Log.d(TAG, "Service resolved: " + serviceName + " -> " + hostAddress + ":" + port);
                if (callback != null) {
                    callback.onServiceFound(heartBeatSign, serviceName, hostAddress, port);
                }
            }
        };
    }

    public void discoverServices() {
        initializeDiscoveryListener();
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
    }

    public void stopDiscovery() {
        if (discoveryListener != null) {
            nsdManager.stopServiceDiscovery(discoveryListener);
        }
    }
}
