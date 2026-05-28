package com.loader.speedtransfer.utils;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.core.content.FileProvider;

import com.loader.speedtransfer.ui.CustomToast;

import java.io.File;

public class FileUiUtils {
    public static void openFile(Context context, File file) {
        String mimeType = getMimeType(file.getName());
        if (mimeType == null) {
            mimeType = "*/*";
        }
        Uri uri = FileProvider.getUriForFile(context,
                context.getPackageName() + ".fileprovider", file);

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, mimeType);
        intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            CustomToast.showNoIcon(context, "无法打开该文件类型");
        }
    }

    public static void openDirectory(Context context, File dir) {
        Uri uri = Uri.fromFile(dir); // 部分国产 ROM 支持直接打开文件夹

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "*/*");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(intent);
        } catch (Exception e) {
            CustomToast.showNoIcon(context, "未找到文件管理器");
        }
    }
    public static String getMimeType(String fileName) {
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
    }

}
