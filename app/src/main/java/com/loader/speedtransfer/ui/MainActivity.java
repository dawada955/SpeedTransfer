package com.loader.speedtransfer.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;

import com.loader.speedtransfer.field.CustomField;
import com.loader.speedtransfer.net.AndroidHttpServer;
import com.loader.speedtransfer.R;
import com.loader.speedtransfer.net.NsdHelper;
import com.loader.speedtransfer.net.UdpHeartbeatSender;
import com.loader.speedtransfer.utils.DeviceSettingUtils;
import com.loader.speedtransfer.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements ChatCallback {
    private static final int REQUEST_PERMISSIONS = 1;
    private AndroidHttpServer httpServer;
    public TextView tv_NetworkAddressPort;
    private RecyclerView recyclerView;
    private EditText editText;
    private Button btnSend;
    private ChatAdapter adapter;
    private List<ChatMessage> messageList = new ArrayList<>();
    private View btn_add;
    ActivityResultLauncher<Intent> launcher;
    private NsdHelper nsdHelper;
    private UdpHeartbeatSender heartbeatSender;
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

        // 检查并请求权限
        if (checkPermissions()) {
            // 启动HTTP服务器
            startHttpServer();

            heartbeatSender = new UdpHeartbeatSender();

            nsdHelper = new NsdHelper(this, (heartBeatSign, serviceName, hostAddress, port) -> {
                Log.d("MainActivity", "Discovered PC: " + hostAddress + ":" + port);
                // 发现PC后，开始发送心跳
                heartbeatSender.startHeartbeat(heartBeatSign, hostAddress, port, DeviceSettingUtils.getDeviceName());
            });
            nsdHelper.discoverServices();
        }
    }
    @SuppressLint("ClickableViewAccessibility")
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

                if(s.length() > 0) {
                    btnSend.setClickable(true);
                    btnSend.setBackgroundResource(R.drawable.bg_btn_send_enable);
                }else {
                    btnSend.setClickable(false);
                    btnSend.setBackgroundResource(R.drawable.bg_btn_send_disable);
                }
            }

            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
        });

        btnSend.setOnClickListener(v -> {
            String content = editText.getText().toString().trim();
            if (!content.isEmpty()) {
                sendMessage(content);
                editText.setText("");
            }
        });
        View rootView = findViewById(R.id.main); // 根布局 id
        View bottomBar = findViewById(R.id.bottom_bar);

        // 控制键盘弹起时输入框与聊天框的弹起
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int screenHeight = rootView.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            if (keypadHeight > screenHeight * 0.15) {
                // 键盘弹出：顶起输入框、增加聊天区 padding
                bottomBar.setTranslationY(-keypadHeight);

                recyclerView.setPadding(
                        recyclerView.getPaddingLeft(),
                        recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(),
                        keypadHeight + bottomBar.getHeight() - 120 // 确保不遮挡消息
                );

                recyclerView.post(() -> {
                    if (recyclerView.getAdapter() != null) {
                        recyclerView.scrollToPosition(recyclerView.getAdapter().getItemCount() - 1);
                    }
                });

            } else {
                // 键盘收起：还原位置与padding
                bottomBar.setTranslationY(0);

                recyclerView.setPadding(
                        recyclerView.getPaddingLeft(),
                        recyclerView.getPaddingTop(),
                        recyclerView.getPaddingRight(),
                        bottomBar.getHeight()
                );
            }
        });

        launcher = FilePickerUtil.registerFilePicker(this, (uris, names) -> {
            for (int i = 0; i < uris.size(); i++) {
                Log.d("选中的文件", names.get(i) + ": " + uris.get(i));
                FileUtils.copyUriToDirectory(this, uris.get(i), CustomField.DownloadDir);
            }
            CustomToast.showNoIcon(this, "文件添加成功...");
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

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
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
                startHttpServer();
            } else {
                CustomToast.showWithIcon(this, "需要所有权限才能运行服务器", R.drawable.speed_transfer_pink);
            }
        }
    }

    private void sendMessage(String content) {
        messageList.add(new ChatMessage(content, ChatMessage.MessageType.TEXT, true)); // 自己发的
        adapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
        new Thread(() -> httpServer.ws_handleBroadcastMessage(content)).start();
    }

    private void startHttpServer() {
        try {
            httpServer = new AndroidHttpServer(this);
            CustomToast.showWithIcon(this, "HTTP服务器已启动", R.drawable.speed_transfer_pink);
        } catch (IOException e) {
            e.printStackTrace();
            CustomToast.showWithIcon(this, "启动服务器失败: ", R.drawable.speed_transfer_pink);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (httpServer != null) {
            httpServer.stop();
            nsdHelper.stopDiscovery();
            heartbeatSender.stopHeartbeat();
        }
    }

    @Override
    public void onReceiveMessage(String ip, String content) {
        recyclerView.postDelayed(() -> {
            messageList.add(new ChatMessage(content, ChatMessage.MessageType.TEXT, false)); // 对方发的
            adapter.notifyItemInserted(messageList.size() - 1);
            recyclerView.scrollToPosition(messageList.size() - 1);
        }, 1000);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onDisplayNetwork(String ip, int PORT) {
        tv_NetworkAddressPort.setText(ip + ":" + PORT);
    }

    @Override
    public void onReceiveFileMessage(ChatMessage fileMessage, File file) {
        fileMessage.setType(ChatMessage.MessageType.FILE);
        fileMessage.setSender(false); // 接收方气泡
        fileMessage.setFile(file);
        fileMessage.setUploadStatus(ChatMessage.UploadStatus.UPLOADING);

        runOnUiThread(() -> {
            fileMessage.setProgress(100); // 初始进度
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