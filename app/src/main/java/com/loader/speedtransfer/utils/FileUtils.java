package com.loader.speedtransfer.utils;

import android.content.Context;
import android.database.Cursor;
import android.icu.text.DecimalFormat;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class FileUtils {
    public static String getReadableFileSize(long size) {
        if (size <= 0) return "0 B";
        final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
        int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
        return new DecimalFormat("#,##0.#").format(size / Math.pow(1024, digitGroups)) + " " + units[digitGroups];
    }

    public static void copyUriToDirectory(Context context, Uri sourceUri, File targetDir) {
        try {
            // 获取文件名
            String fileName = getFileNameFromUri(context, sourceUri);
            if (fileName == null) fileName = "file_" + System.currentTimeMillis();

            // 创建目标文件
            File targetFile = new File(targetDir, fileName);
            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
            FileOutputStream outputStream = new FileOutputStream(targetFile);

            // 复制数据
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 从Uri中提取文件名
    private static String getFileNameFromUri(Context context, Uri uri) {
        Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
            String fileName = cursor.getString(nameIndex);
            cursor.close();
            return fileName;
        }
        return null;
    }
}

