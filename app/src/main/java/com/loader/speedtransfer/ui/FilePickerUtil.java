package com.loader.speedtransfer.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Button;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;

import java.util.ArrayList;
import java.util.List;

public class FilePickerUtil {

    public interface OnFilesPickedListener {
        void onFilesPicked(List<Uri> uris, List<String> displayNames);
    }

    public static ActivityResultLauncher<Intent> registerFilePicker(
            Fragment fragment,
            OnFilesPickedListener listener
    ) {
        return fragment.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleResult(fragment.requireContext(), result, listener)
        );
    }

    public static ActivityResultLauncher<Intent> registerFilePicker(
            ComponentActivity activity,
            OnFilesPickedListener listener
    ) {
        return activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> handleResult(activity, result, listener)
        );
    }

    public static void bindFilePickerButton(Button button, ActivityResultLauncher<Intent> launcher, String mimeType) {
        button.setOnClickListener(v -> {
            Intent intent = createFileIntent(mimeType);
            launcher.launch(intent);
        });
    }

    private static void handleResult(Context context, ActivityResult result, OnFilesPickedListener listener) {
        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
            Intent data = result.getData();
            List<Uri> uris = new ArrayList<>();
            List<String> names = new ArrayList<>();

            if (data.getClipData() != null) { // 多选
                for (int i = 0; i < data.getClipData().getItemCount(); i++) {
                    Uri uri = data.getClipData().getItemAt(i).getUri();
                    uris.add(uri);
                    names.add(getFileName(context, uri));
                }
            } else if (data.getData() != null) { // 单选
                Uri uri = data.getData();
                uris.add(uri);
                names.add(getFileName(context, uri));
            }

            if (!uris.isEmpty()) {
                listener.onFilesPicked(uris, names);
            } else {
                Log.w("FilePicker", "No files picked");
            }
        }
    }

    public static Intent createFileIntent(String mimeType) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType != null ? mimeType : "*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true); // 支持多选
        return intent;
    }

    @SuppressLint("Range")
    public static String getFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
}
