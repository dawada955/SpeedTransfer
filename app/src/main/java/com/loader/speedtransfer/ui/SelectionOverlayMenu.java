package com.loader.speedtransfer.ui;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.loader.speedtransfer.R;

/**
 * 用于替代 PopupWindow 的选择菜单视图，避免系统窗口被回收。
 */
public class SelectionOverlayMenu extends LinearLayout {

    public interface OnActionListener {
        void onSelectAll();
        void onCopy();
        void onDelete();
    }

    private TextView btnSelectAll, btnCopy, btnDelete;
    private ImageView arrowUp, arrowDown;
    private OnActionListener listener;

    private boolean showingAbove = false;
    private boolean playedOnce = false;

    public SelectionOverlayMenu(Context context) {
        super(context);
        init(context);
    }

    public SelectionOverlayMenu(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public SelectionOverlayMenu(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.layout_selection_popup, this, true);
        setOrientation(VERTICAL);
        setBackgroundColor(Color.TRANSPARENT);
        setVisibility(GONE);

        btnSelectAll = findViewById(R.id.btn_select_all);
        btnCopy = findViewById(R.id.btn_copy);
        btnDelete = findViewById(R.id.btn_delete);
        arrowUp = findViewById(R.id.arrow_up);
        arrowDown = findViewById(R.id.arrow_down);

        btnSelectAll.setOnClickListener(v -> {
            if (listener != null) listener.onSelectAll();
        });
        btnCopy.setOnClickListener(v -> {
            if (listener != null) listener.onCopy();
        });
        btnDelete.setOnClickListener(v -> {
            if (listener != null) listener.onDelete();
        });
    }

    public void setOnActionListener(OnActionListener listener) {
        this.listener = listener;
    }

    /**
     * 在 anchor 上方或下方显示菜单（自动居中）
     */
    public void showAt(View anchor, boolean showAbove) {
        if (anchor.getParent() instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) anchor.getRootView();

            if (getParent() != root) {
                if (getParent() != null) {
                    ((ViewGroup) getParent()).removeView(this);
                }

                root.post(() -> {
                    if (this.getParent() == null) {
                        root.addView(this, new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        ));
                    }
                    updatePosition(anchor, showAbove);
                    animateInOnce(showAbove);
                });
            } else {
                updatePosition(anchor, showAbove);
                animateInOnce(showAbove);
            }
        }
    }
    private void updatePosition(View anchor, boolean showAbove) {
        int[] anchorLoc = new int[2];
        anchor.getLocationOnScreen(anchorLoc);

        int widthSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        measure(widthSpec, heightSpec);

        int menuHeight = getMeasuredHeight();

        int y = showAbove
                ? anchorLoc[1] - menuHeight - 10
                : anchorLoc[1] + anchor.getHeight() + 10;

        TextView messageView = anchor.findViewById(R.id.text_message);
        if (messageView == null) return;
        int[] location = new int[2];
        messageView.getLocationOnScreen(location);
        int x = calculateXOffset(anchor, messageView, location);
        setX(x);
        setY(y);

        arrowUp.setVisibility(showAbove ? GONE : VISIBLE);
        arrowDown.setVisibility(showAbove ? VISIBLE : GONE);
    }

    private int calculateXOffset(View anchor, TextView messageView, int[] location) {
        int screenWidth = anchor.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = this.getMeasuredWidth();
        int xCenter = location[0] + (messageView.getWidth() - popupWidth) / 2;

        int edgeGap = 15;
        if (xCenter < edgeGap) return edgeGap;
        if (xCenter + popupWidth > screenWidth) return screenWidth - popupWidth - edgeGap;
        return xCenter;
    }

    private void animateInOnce(boolean showAbove) {
        if (playedOnce) {
            setVisibility(VISIBLE);
            return;
        }

        playedOnce = true;
        clearAnimation();
        setVisibility(VISIBLE);

        setScaleX(1f); // 起始缩放稍小 横向位移
        setScaleY(showAbove ? 0.9f : 1.1f);  // 纵向位移
        setAlpha(0f);    // 起始透明

        animate()
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(160) // 稍短更自然
                .setInterpolator(new OvershootInterpolator(1.05f)) // 微小弹性
                .start();
    }


    public void dismiss() {
        clearAnimation();
        playedOnce = false;
        setVisibility(GONE);
    }

    public boolean isShowing() {
        return getVisibility() == VISIBLE;
    }
}
