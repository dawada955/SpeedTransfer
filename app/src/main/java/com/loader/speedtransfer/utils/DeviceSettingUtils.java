package com.loader.speedtransfer.utils;

public class DeviceSettingUtils {

    public static String getDeviceName() {
        String manufacturer = android.os.Build.MANUFACTURER;  // 品牌，例如 "Xiaomi"
        String model = android.os.Build.MODEL;                // 型号，例如 "13" 或 "SM-G9910"

        String deviceName;
        if (model.startsWith(manufacturer)) {
            deviceName = model;
        } else {
            deviceName = manufacturer + " " + model;
        }

        return deviceName;
    }

}
