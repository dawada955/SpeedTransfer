package com.loader.speedtransfer.ui;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.loader.speedtransfer.R;

import java.util.LinkedList;
import java.util.Queue;

public class CustomToast {

    private static final int DURATION = 2000; // 每条 toast 显示 2 秒
    private static final Handler handler = new Handler(Looper.getMainLooper());
    private static final Queue<ToastItem> toastQueue = new LinkedList<>();
    private static boolean isShowing = false;

    public static void showWithIcon(Context context, String message, int iconResId) {
        toastQueue.add(new ToastItem(context, message, iconResId));
        processQueue();
    }

    public static void showNoIcon(Context context, String message) {
        toastQueue.add(new ToastItem(context, message, -1));
        processQueue();
    }

    private static void processQueue() {
        if (isShowing || toastQueue.isEmpty()) return;

        isShowing = true;
        ToastItem item = toastQueue.poll();
        View view;

        if (item.iconResId != -1) {
            view = LayoutInflater.from(item.context).inflate(R.layout.toast_with_icon, null);
            ImageView icon = view.findViewById(R.id.toast_icon);
            TextView text = view.findViewById(R.id.toast_text);
            icon.setImageResource(item.iconResId);
            text.setText(item.message);
        } else {
            view = LayoutInflater.from(item.context).inflate(R.layout.toast_no_icon, null);
            TextView text = view.findViewById(R.id.toast_text);
            text.setText(item.message);
        }

        applyFadeAnimation(view);

        Toast toast = new Toast(item.context);
        toast.setView(view);
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.show();

        handler.postDelayed(() -> {
            isShowing = false;
            processQueue(); // 显示下一个
        }, DURATION);
    }

    private static void applyFadeAnimation(View view) {
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(300);
        fadeIn.setFillAfter(true);

        AlphaAnimation fadeOut = new AlphaAnimation(1f, 0f);
        fadeOut.setStartOffset(DURATION - 400);
        fadeOut.setDuration(300);
        fadeOut.setFillAfter(true);

        view.startAnimation(fadeIn);
        view.postDelayed(() -> view.startAnimation(fadeOut), DURATION - 400);
    }

    private static class ToastItem {
        Context context;
        String message;
        int iconResId;

        ToastItem(Context context, String message, int iconResId) {
            this.context = context.getApplicationContext(); // 防止内存泄漏
            this.message = message;
            this.iconResId = iconResId;
        }
    }
}
