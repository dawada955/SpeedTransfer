package com.loader.speedtransfer.field;

import android.os.Environment;

import java.io.File;

public class CustomField {

    public final static int SERVICE_PORT = 8099;

    public final static File DownloadDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS), "WeTransfer/上传文件");

    public final static File UploadDir = new File(Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_DOWNLOADS), "WeTransfer/接收文件");
}
