package com.loader.speedtransfer.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.loader.speedtransfer.field.CustomField;
import com.loader.speedtransfer.net.TransferService;
import com.loader.speedtransfer.R;
import com.loader.speedtransfer.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ChatCallback {
    private static final int REQUEST_PERMISSIONS = 1;

    private TransferService transferService;
    private boolean serviceBound = false;

    public TextView tv_NetworkAddressPort;
    private RecyclerView recyclerView;
    private EditText editText;
    private Button btnSend;
    private ChatAdapter adapter;
    private List<ChatMessage> messageList = new ArrayList<>();
    private View btn_add;
    ActivityResultLauncher<Intent> launcher;

    private RecyclerView recyclerViewFiles;
    private FrameLayout fileListWrapper;
    private FileShareAdapter fileShareAdapter;
    private TextView tvFileToggleBar;
    private boolean fileListExpanded = true;
    private boolean fileListAnimating = false;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TransferService.TransferBinder binder = (TransferService.TransferBinder) service;
            transferService = binder.getService();
            transferService.setActivityCallback(MainActivity.this);
            serviceBound = true;
            Log.d("MainActivity", "TransferService 绑定成功");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            transferService = null;
            serviceBound = false;
            Log.d("MainActivity", "TransferService 断开");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initComponent();
        refreshFileList();

        if (checkPermissions()) {
            startTransferService();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (serviceBound) {
            checkBatteryOptimization();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (editText != null) {
            editText.clearFocus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (serviceBound && transferService != null) {
            transferService.clearActivityCallback();
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }

    private void startTransferService() {
        Intent serviceIntent = new Intent(this, TransferService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @SuppressLint({"ClickableViewAccessibility", "BatteryLife"})
    private void initComponent() {
        tv_NetworkAddressPort = findViewById(R.id.tv_NetworkAddressPort);
        recyclerView = findViewById(R.id.recyclerView_chat);
        editText = findViewById(R.id.editText_message);
        btnSend = findViewById(R.id.btn_send);

        adapter = new ChatAdapter(this, messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                btnSend.setClickable(s.length() > 0);

                if (s.length() > 0) {
                    btnSend.setClickable(true);
                    btnSend.setBackgroundResource(R.drawable.bg_btn_send_enable);
                } else {
                    btnSend.setClickable(false);
                    btnSend.setBackgroundResource(R.drawable.bg_btn_send_disable);
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btnSend.setOnClickListener(v -> {
            String content = editText.getText().toString().trim();
            if (!content.isEmpty()) {
                sendMessage(content);
                editText.setText("");
            }
        });
        View rootView = findViewById(R.id.main);
        View bottomBar = findViewById(R.id.bottom_bar);

        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                bottomBar.setTranslationY(-keypadHeight);

                recyclerView.setPadding(
                        recyclerView.getPaddingLeft(),
                        recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(),
                        keypadHeight + bottomBar.getHeight() - 120
                );

                recyclerView.post(() -> {
                    if (recyclerView.getAdapter() != null) {
                        recyclerView.scrollToPosition(recyclerView.getAdapter().getItemCount() - 1);
                    }
                });

            } else {
                bottomBar.setTranslationY(0);

                recyclerView.setPadding(
                        recyclerView.getPaddingLeft(),
                        recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(),
                        bottomBar.getHeight()
                );
            }
        });

        // --- IP 点击复制 ---
        tv_NetworkAddressPort.setOnClickListener(v -> {
            String text = tv_NetworkAddressPort.getText().toString();
            if (text != null && !text.isEmpty() && !text.startsWith("xxx")) {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(ClipData.newPlainText("ip", text));
                CustomToast.showNoIcon(this, "IP 已复制");
            }
        });

        // --- 共享文件列表 ---
        fileListWrapper = findViewById(R.id.file_list_wrapper);
        recyclerViewFiles = findViewById(R.id.recyclerView_files);
        tvFileToggleBar = findViewById(R.id.tv_file_toggle_bar);

        fileShareAdapter = new FileShareAdapter();
        recyclerViewFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewFiles.setAdapter(fileShareAdapter);

        fileShareAdapter.setOnDeleteClickListener((file, position) -> {
            if (file.delete()) {
                fileShareAdapter.removeItem(position);
                int remaining = fileShareAdapter.getItemCount();
                updateToggleBarText(remaining);
                if (remaining == 0) {
                    fileListExpanded = true;
                    animateFileList(false);
                }
                CustomToast.showNoIcon(this, "已删除");
            }
        });

        tvFileToggleBar.setOnClickListener(v -> {
            if (fileShareAdapter.getItemCount() == 0) {
                return;
            }
            toggleFileList();
        });

        // --- 文件选择器 ---
        launcher = FilePickerUtil.registerFilePicker(this, (uris, names) -> {
            for (int i = 0; i < uris.size(); i++) {
                Log.d("选中的文件", names.get(i) + ": " + uris.get(i));
                FileUtils.copyUriToDirectory(this, uris.get(i), CustomField.DownloadDir);
            }
            refreshFileList();
            CustomToast.showNoIcon(this, "文件添加成功");
        });
        btn_add = findViewById(R.id.btn_add);
        btn_add.setOnClickListener(v -> {
            Intent intent = FilePickerUtil.createFileIntent("*/*");
            launcher.launch(intent);
        });
    }

    private boolean checkPermissions() {
        String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.INTERNET
        };

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSIONS) {
            boolean allGranted = true;
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                startTransferService();
            } else {
                CustomToast.showWithIcon(this, "需要所有权限才能运行服务器", R.drawable.speed_transfer_pink);
            }
        }
    }

    private void sendMessage(String content) {
        ChatMessage msg = new ChatMessage(content, ChatMessage.MessageType.TEXT, true);
        msg.setSendStatus(ChatMessage.SendStatus.SENDING);
        msg.setSenderName("服务器");
        messageList.add(msg);
        int pos = messageList.size() - 1;
        adapter.notifyItemInserted(pos);
        recyclerView.scrollToPosition(pos);

        if (serviceBound && transferService != null) {
            transferService.sendChatMessage(content);
        }

        recyclerView.postDelayed(() -> {
            if (msg.getSendStatus() == ChatMessage.SendStatus.SENDING) {
                msg.setSendStatus(ChatMessage.SendStatus.SENT);
                int idx = messageList.indexOf(msg);
                if (idx != -1) {
                    adapter.notifyItemChanged(idx);
                }
            }
        }, 800);
    }

    // --- 共享文件列表管理 ---
    private void refreshFileList() {
        File downloadDir = CustomField.DownloadDir;
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        File[] files = downloadDir.listFiles(File::isFile);
        List<File> fileList = new ArrayList<>();
        if (files != null) {
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            fileList.addAll(Arrays.asList(files));
        }

        fileShareAdapter.setFiles(fileList);
        int count = fileList.size();
        updateToggleBarText(count);

        if (count == 0) {
            fileListExpanded = true;
            fileListWrapper.setVisibility(View.GONE);
        } else {
            fileListExpanded = true;
            showFileListImmediate();
        }
    }

    private void updateToggleBarText(int count) {
        String arrow = fileListExpanded ? "▼" : "▶";
        tvFileToggleBar.setText(arrow + " 共享文件 (" + count + ")");
    }

    private void toggleFileList() {
        if (fileListAnimating) return;
        fileListExpanded = !fileListExpanded;
        updateToggleBarText(fileShareAdapter.getItemCount());
        animateFileList(fileListExpanded);
    }

    private void showFileListImmediate() {
        int h = measureWrapperHeight();
        ViewGroup.LayoutParams lp = fileListWrapper.getLayoutParams();
        lp.height = h;
        fileListWrapper.setLayoutParams(lp);
        fileListWrapper.setVisibility(View.VISIBLE);
    }

    private void animateFileList(boolean show) {
        if (show) {
            // 设置为 VISIBLE + 高度 0，等布局完成后再测量并展开
            fileListWrapper.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams lp = fileListWrapper.getLayoutParams();
            lp.height = 0;
            fileListWrapper.setLayoutParams(lp);

            fileListWrapper.post(() -> {
                int target = measureWrapperHeight();
                runHeightAnim(0, target, true);
            });
        } else {
            int start = fileListWrapper.getHeight();
            runHeightAnim(start, 0, false);
        }
    }

    private void runHeightAnim(int from, int to, boolean show) {
        fileListAnimating = true;
        ValueAnimator anim = ValueAnimator.ofInt(from, to);
        anim.addUpdateListener(a -> {
            ViewGroup.LayoutParams lp = fileListWrapper.getLayoutParams();
            lp.height = (int) a.getAnimatedValue();
            fileListWrapper.setLayoutParams(lp);
        });
        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                fileListAnimating = false;
                if (!show) {
                    fileListWrapper.setVisibility(View.GONE);
                }
            }
        });
        anim.setDuration(250);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
    }

    private int measureWrapperHeight() {
        View parent = (View) fileListWrapper.getParent();
        int parentWidth = parent.getWidth();
        if (parentWidth == 0) {
            parentWidth = getResources().getDisplayMetrics().widthPixels;
        }
        int widthSpec = View.MeasureSpec.makeMeasureSpec(parentWidth, View.MeasureSpec.EXACTLY);
        int maxH = dpToPx(200);
        int heightSpec = View.MeasureSpec.makeMeasureSpec(maxH, View.MeasureSpec.AT_MOST);
        fileListWrapper.measure(widthSpec, heightSpec);
        return Math.min(fileListWrapper.getMeasuredHeight(), maxH);
    }

    private int dpToPx(int dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    // --- 电池优化白名单引导 ---
    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                new AlertDialog.Builder(this)
                        .setTitle("保持后台运行")
                        .setMessage("为保障文件传输在后台正常进行，建议关闭电池优化限制。")
                        .setPositiveButton("去设置", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("暂不", null)
                        .show();
            }
        }
    }

    // --- ChatCallback 实现 ---
    @Override
    public void onReceiveMessage(String ip, String content) {
        recyclerView.postDelayed(() -> {
            ChatMessage msg = new ChatMessage(content, ChatMessage.MessageType.TEXT, false);
            msg.setSenderName(ip);
            messageList.add(msg);
            adapter.notifyItemInserted(messageList.size() - 1);
            recyclerView.scrollToPosition(messageList.size() - 1);
        }, 1000);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onDisplayNetwork(String ip, int port) {
        tv_NetworkAddressPort.setText(ip + ":" + port);
    }

    @Override
    public void onReceiveFileMessage(ChatMessage fileMessage, File file) {
        fileMessage.setType(ChatMessage.MessageType.FILE);
        fileMessage.setSender(false);
        fileMessage.setFile(file);
        fileMessage.setUploadStatus(ChatMessage.UploadStatus.UPLOADING);

        runOnUiThread(() -> {
            fileMessage.setProgress(100);
            messageList.add(fileMessage);
            int position = messageList.size() - 1;
            adapter.notifyItemInserted(position);
        });
    }

    @Override
    public void onReceiveFileMessageProgress(ChatMessage fileMessage, int progress, long size) {
        new Handler(Looper.getMainLooper()).post(() -> {
            int pos = messageList.indexOf(fileMessage);
            if (pos != -1) {
                fileMessage.setProgress(progress);
                fileMessage.setFileRealTimeSize(FileUtils.getReadableFileSize(size));
                adapter.notifyItemChanged(pos);
            }
        });
    }

    @Override
    public void onReceiveFileMessageComplete(ChatMessage fileMessage, long size) {
        new Handler(Looper.getMainLooper()).post(() -> {
            fileMessage.setUploadStatus(ChatMessage.UploadStatus.COMPLETED);
            int pos = messageList.indexOf(fileMessage);
            if (pos != -1) {
                fileMessage.setFileRealTimeSize(FileUtils.getReadableFileSize(size));
                adapter.notifyItemChanged(pos);
            }
        });
    }

    @Override
    public void onReceiveFileMessageError(ChatMessage fileMessage) {
        new Handler(Looper.getMainLooper()).post(() -> {
            fileMessage.setUploadStatus(ChatMessage.UploadStatus.FAILED);
            int pos = messageList.indexOf(fileMessage);
            if (pos != -1) {
                adapter.notifyItemChanged(pos);
            }
        });
    }
}
