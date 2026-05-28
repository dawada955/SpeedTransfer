package com.loader.speedtransfer.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.widget.AppCompatTextView;

public class PopupTextView extends AppCompatTextView {

    private int selectionStart = -1;
    private int selectionEnd = -1;
    private boolean isSelecting = false;
    private boolean isDraggingStart = false;
    private boolean isDraggingEnd = false;
    private float lastTouchX, lastTouchY;
    private static final float HANDLE_RADIUS = 14f;
    private static final float HANDLE_TOUCH_RADIUS = 60f;

    private SelectionOverlayMenu popup;

    private OnDeleteListener deleteListener;

    private View holderItemView;
    private boolean justShown = false;
    private static PopupTextView currentActive = null;
    private final String SelectAreaColor = "#44FFFFFF";
    private final String SelectHandleHeadColor = "#FAFAFA";
    private final String SelectHandleBodyColor = "#88FFFFFF";


    public void SetHolderItemView(View holderItemView) {
        this.holderItemView = holderItemView;
    }

    public void setOnDeleteListener(OnDeleteListener deleteListener) {
        this.deleteListener = deleteListener;
    }

    public interface OnDeleteListener {
        void onDeleteRequest();
    }

    public static PopupTextView getCurrentActive() {
        return currentActive;
    }
    public static void clearGlobalSelection() {
        if (currentActive != null) {
            currentActive.clearSelection();
            currentActive = null;
        }
    }

    public static void registerClearListeners(View... views) {
        @SuppressLint("ClickableViewAccessibility") View.OnTouchListener clearer = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                clearGlobalSelection();
            }
            return false;
        };
        for (View view : views) {
            if (view != null) view.setOnTouchListener(clearer);
        }
    }
    // 一键递归注册父容器
    @SuppressLint("ClickableViewAccessibility")
    public static void registerClearListenerWithFallback(View view) {
        if (view == null) return;
        if (view.isEnabled() && view.isClickable()) {
            view.setOnTouchListener((v, e) -> {
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    clearGlobalSelection();
                }
                return false;
            });
        } else {
            View parent = (View) view.getParent();
            while (parent != null) {
                if (parent.isClickable() || parent instanceof ViewGroup) {
                    parent.setOnTouchListener((v, e) -> {
                        if (e.getAction() == MotionEvent.ACTION_DOWN) {
                            clearGlobalSelection();
                        }
                        return false;
                    });
                    break;
                }
                if (!(parent.getParent() instanceof View)) break;
                parent = (View) parent.getParent();
            }
        }
    }

    public PopupTextView(Context context) {
        super(context);
        init();
    }

    public PopupTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PopupTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void init() {
        setTextIsSelectable(false);
        setSingleLine(false);
        setMaxLines(Integer.MAX_VALUE);

        setOnLongClickListener(v -> {
            clearGlobalSelection();        // 清除旧的
            currentActive = this;          // ✅ 设置当前活跃气泡

            if (getLayout() == null) return false;

            int offset = getPreciseOffset(lastTouchX, lastTouchY);
            if (offset < 0 || offset > getText().length()) return false;

            selectionStart = offset;
            selectionEnd = Math.min(offset + 1, getText().length());
            isSelecting = true;
            invalidate();
            post(()->{
                this.showPopup();
                justShown = true; // ✅ 标记刚刚已经弹出过
            });
            return true;
        });

    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        lastTouchX = event.getX();
        lastTouchY = event.getY();

        if (isSelecting) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                int[] location = new int[2];
                getLocationOnScreen(location);
                float x = event.getRawX();
                float y = event.getRawY();
                boolean inside = x >= location[0] && x <= location[0] + getWidth()
                        && y >= location[1] && y <= location[1] + getHeight();

                if (!inside) {
                    clearSelection();
                    return false;
                }
            }

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (isInHandle(event, true)) {
                        isDraggingStart = true;

                        getParent().requestDisallowInterceptTouchEvent(true);
                    } else if (isInHandle(event, false)) {
                        isDraggingEnd = true;

                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (popup != null && popup.isShowing()) {
                        popup.dismiss();
                        Log.d("tag1", "1");
                    }
                    break;

                case MotionEvent.ACTION_MOVE:
                    int offset = getPreciseOffset(event.getX(), event.getY());
                    offset = adjustOffsetToNearestChar(offset);
                    if (isDraggingStart) {
                        selectionStart = offset;
                    } else if (isDraggingEnd) {

                        Log.d("DragOffset", String.valueOf(offset));
                        selectionEnd = offset;
                    }
                    invalidate();
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDraggingStart = false;
                    isDraggingEnd = false;
                    post(this::showPopup);
                    getParent().requestDisallowInterceptTouchEvent(false);
                    break;
            }
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    public void clearSelection() {
        isSelecting = false;Log.d("clear", "clearSelection called: " + this);
        selectionStart = selectionEnd = -1;
        if (popup != null) {
            popup.dismiss();
            Log.d("tag1", "2");
        }
        invalidate();
    }

    private int getPreciseOffset(float x, float y) {
        Layout layout = getLayout();
        if (layout == null) return -1;

        x -= getTotalPaddingLeft();
        y -= getTotalPaddingTop();

        x = Math.max(0, x);
        y = Math.max(0, y);

        int lineCount = layout.getLineCount();
        int lastLineBottom = layout.getLineBottom(lineCount - 1);
        if (y > lastLineBottom) y = lastLineBottom - 1;

        int line = layout.getLineForVertical((int) y);
        int lineEnd = layout.getLineEnd(line);
        if (x >= layout.getLineRight(line) - 5) {
            return lineEnd;
        }

        int offset = layout.getOffsetForHorizontal(line, x);

        return Math.min(offset, getText().length());
    }

    private int adjustOffsetToNearestChar(int offset) {
        // 返回点击的字在气泡中的位置
        if (offset <= 0) return 0;
        if (offset >= getText().length()) return getText().length();
        char cur = getText().charAt(offset);
        if (Character.isWhitespace(cur)) return offset;
        Log.d("updateSelect_", String.valueOf(cur) + offset);
        return offset;
    }

    private boolean isInHandle(MotionEvent e, boolean isStart) {  // 检测按下区域是否为选区把柄
        if (getLayout() == null) return false;
        int offset = isStart ? selectionStart : selectionEnd;
        Layout layout = getLayout();
        int line = layout.getLineForOffset(offset);

        float x = layout.getPrimaryHorizontal(offset) + getTotalPaddingLeft();
        float y = layout.getLineBottom(line) + getTotalPaddingTop() - 10f;

        float dx = e.getX() - x;
        float dy = e.getY() - y;

        return Math.hypot(dx, dy) <= HANDLE_TOUCH_RADIUS;
    }

    private void showPopup() {
        if (justShown) {
            justShown = false;
            return;
        }

        if (!isSelecting || selectionStart == selectionEnd) return;

        if (popup != null && popup.isShowing()) {
            popup.dismiss();
        }
        if (popup == null) {
            popup = new SelectionOverlayMenu(getContext());
            popup.setOnActionListener(new SelectionOverlayMenu.OnActionListener() {
                @Override
                public void onSelectAll() {
                    selectionStart = 0;
                    selectionEnd = getText().length();
                    invalidate();
                }

                @Override
                public void onCopy() {
                    if (selectionStart >= 0 && selectionEnd > selectionStart) {
                        String selected = getText().subSequence(Math.min(selectionStart, selectionEnd), Math.max(selectionStart, selectionEnd)).toString();
                        ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                        clipboard.setPrimaryClip(ClipData.newPlainText("text", selected));
                    }
                    clearSelection();
                }

                @Override
                public void onDelete() {
                    if (deleteListener != null) {
                        deleteListener.onDeleteRequest();
                    }
                    clearSelection();
                }
            });

            // 加入父布局
            ViewGroup parent = (ViewGroup) holderItemView.getRootView();
            if (popup.getParent() == null && parent != null) {
                parent.addView(popup);
            }
        }

        // 控制显示位置
        boolean showAbove = holderItemView.getTop() > holderItemView.getHeight() + 20;
        popup.showAt(holderItemView, showAbove);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (!isSelecting) return;

        if (selectionStart >= 0 && selectionEnd >= 0) {
            Layout layout = getLayout();
            int start = Math.min(selectionStart, selectionEnd);
            int end = Math.max(selectionStart, selectionEnd);
            int lineStart = layout.getLineForOffset(start);
            int lineEnd = layout.getLineForOffset(end);

            Paint paint = new Paint();
            paint.setColor(Color.parseColor(SelectAreaColor));
            paint.setStyle(Paint.Style.FILL);

            for (int i = lineStart; i <= lineEnd; i++) {
                int lineStartOffset = (i == lineStart) ? start : layout.getLineStart(i);
                int lineEndOffset = (i == lineEnd) ? end : layout.getLineEnd(i);

                float left = layout.getPrimaryHorizontal(lineStartOffset) + getTotalPaddingLeft();
                float right;
                if (lineEndOffset == layout.getLineEnd(i)) {
                    right = layout.getLineRight(i) + getTotalPaddingLeft();
                } else {
                    right = layout.getPrimaryHorizontal(lineEndOffset) + getTotalPaddingLeft();
                }

                int top = layout.getLineTop(i) + getTotalPaddingTop();
                int bottom = layout.getLineBottom(i) + getTotalPaddingTop();

                canvas.drawRect(left, top, right, bottom, paint);

                Log.d("paint", left + " " + top + " " + right + " "+  bottom);
            }

            drawHandle(canvas, true);  //画首把柄
            drawHandle(canvas, false);  //画尾把柄
        }
    }

    private void drawHandle(Canvas canvas, boolean isStart) {
        if (getLayout() == null) return;
        int offset = isStart ? selectionStart : selectionEnd;
        Layout layout = getLayout();
        int line = layout.getLineForOffset(offset);

        float x = layout.getPrimaryHorizontal(offset) + getTotalPaddingLeft();
        float y = layout.getLineBottom(line) + getTotalPaddingTop() - 60f;

        Log.d("paintHandle", line + " " + layout.getLineBottom(line) + " " + getTotalPaddingTop());

        Paint BodyPaint = new Paint();
        BodyPaint.setColor(Color.parseColor(SelectHandleBodyColor));
        BodyPaint.setStyle(Paint.Style.FILL);
        BodyPaint.setAntiAlias(true);

        Paint HeadPaint = new Paint();
        HeadPaint.setColor(Color.parseColor(SelectHandleHeadColor));
        HeadPaint.setStyle(Paint.Style.FILL);
        HeadPaint.setAntiAlias(true);

        float barHeight = 60f;
        canvas.drawRect(x - 2, y, x + 2, y + barHeight, BodyPaint);
        canvas.drawCircle(x, y, HANDLE_RADIUS, HeadPaint);
    }
}