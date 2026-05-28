package com.loader.speedtransfer.ui;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import androidx.recyclerview.widget.RecyclerView;

import com.loader.speedtransfer.R;
import com.loader.speedtransfer.utils.FileUiUtils;
import com.loader.speedtransfer.utils.FileUtils;

import java.io.File;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TYPE_UNKNOWN = 0;
    private static final int TYPE_SENDER = 1;
    private static final int TYPE_RECEIVER = 2;
    private static final int TYPE_FILE_RECEIVER = 3;
    private final Context context;
    private final List<ChatMessage> messageList;

    public ChatAdapter(Context context, List<ChatMessage> messageList) {
        this.messageList = messageList;

        this.context = context;
    }

    @Override
    public int getItemCount() {
        return messageList.size();
    }

    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messageList.get(position);
        if (message.getType() == ChatMessage.MessageType.FILE) {
            return TYPE_FILE_RECEIVER;
        }else if(message.getType() == ChatMessage.MessageType.TEXT) {
            return message.isSender() ? TYPE_SENDER : TYPE_RECEIVER;
        }
        return TYPE_UNKNOWN;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        switch (viewType) {
            case TYPE_SENDER:
                return new SenderViewHolder(inflater.inflate(R.layout.item_chat_sender, parent, false));
            case TYPE_RECEIVER:
                return new ReceiverViewHolder(inflater.inflate(R.layout.item_chat_receiver, parent, false));
            case TYPE_FILE_RECEIVER:
                return new FileSenderViewHolder(inflater.inflate(R.layout.item_chat_file_receiver, parent, false));
            default:
                throw new IllegalArgumentException("Unknown view type");
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messageList.get(position);

        if(message.getType() == ChatMessage.MessageType.TEXT) {
            PopupTextView popupTv = holder.itemView.findViewById(R.id.text_message);
            popupTv.setText(message.getContent());

            // 发送状态指示器
            if (holder instanceof SenderViewHolder) {
                SenderViewHolder svh = (SenderViewHolder) holder;
                switch (message.getSendStatus()) {
                    case SENDING:
                        svh.progressSend.setVisibility(View.VISIBLE);
                        svh.iconSendError.setVisibility(View.GONE);
                        break;
                    case FAILED:
                        svh.progressSend.setVisibility(View.GONE);
                        svh.iconSendError.setVisibility(View.VISIBLE);
                        break;
                    case SENT:
                    default:
                        svh.progressSend.setVisibility(View.GONE);
                        svh.iconSendError.setVisibility(View.GONE);
                        break;
                }
            } else if (holder instanceof ReceiverViewHolder) {
                ReceiverViewHolder rvh = (ReceiverViewHolder) holder;
                String name = message.getSenderName();
                rvh.senderName.setText(name != null && !name.isEmpty() ? name : "客户端");
            }

            // 注册删除监听（可选）
            popupTv.setOnDeleteListener(() -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    messageList.remove(pos);
                    notifyItemRemoved(pos);
                }
            });
            // 监听点击自身 itemView，用于判断是否点击了当前激活气泡以外的其他气泡
            holder.itemView.findViewById(R.id.text_message).setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    PopupTextView popup = holder.itemView.findViewById(R.id.text_message);
                    if (popup != null && popup != PopupTextView.getCurrentActive()) {
                        PopupTextView.clearGlobalSelection();
                    }
                }
                return false;
            });
            popupTv.SetHolderItemView(holder.itemView);

            // 注册底部区域等点击清除
            MainActivity activity = (MainActivity) context;
            PopupTextView.registerClearListenerWithFallback(activity.findViewById(R.id.editText_message));
            PopupTextView.registerClearListenerWithFallback(activity.findViewById(R.id.btn_send));
            PopupTextView.registerClearListenerWithFallback(activity.findViewById(R.id.bottom_bar));
            PopupTextView.registerClearListeners(
                    activity.findViewById(R.id.recyclerView_chat),
                    activity.findViewById(R.id.main));

        }else if(message.getType() == ChatMessage.MessageType.FILE) {

            if (holder instanceof FileSenderViewHolder) {
                FileSenderViewHolder fileHolder = (FileSenderViewHolder) holder;
                String name = message.getSenderName();
                fileHolder.senderName.setText(name != null && !name.isEmpty() ? name : "客户端");
                File file = message.getFile();
                fileHolder.fileName.setText(file.getName());
                fileHolder.fileSize.setText(FileUtils.getReadableFileSize(file.length()));
                fileHolder.fileStatus.setText(" 上传中 · ");

                // 根据文件类型设置图标
                int iconResId = com.loader.speedtransfer.utils.FileIconUtils.getIconForFile(file.getName());
                if (iconResId != 0) {
                    fileHolder.iconFile.setImageResource(iconResId);
                }

                // 点击文件打开（完成状态可打开，其他状态下也可尝试）
                fileHolder.iconFile.setOnClickListener(v -> {
                    if (file.isDirectory()) {
                        FileUiUtils.openDirectory(context, file);
                    } else if (file.isFile() && file.exists()) {
                        FileUiUtils.openFile(context, file);
                    }
                });

                fileHolder.iconFile.setOnTouchListener((v, event) -> {
                    View bubble = fileHolder.itemView.findViewById(R.id.file_bubble);
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            bubble.animate().scaleX(0.98f).scaleY(0.98f).setDuration(150).start();
                            bubble.setBackgroundResource(R.drawable.bg_bubble_file_pressed);
                            break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            bubble.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                            bubble.setBackgroundResource(R.drawable.bg_bubble_file);
                            break;
                    }
                    return false;
                });

                fileHolder.fileName.setOnLongClickListener(v -> {
                    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("label", ((TextView)v).getText().toString());
                    clipboard.setPrimaryClip(clip);
                    CustomToast.showNoIcon(context, "已复制");
                    return true;
                });

                switch (message.getUploadStatus()) {
                    case UPLOADING:
                        fileHolder.progressBar.setVisibility(View.VISIBLE);
                        fileHolder.iconFailed.setVisibility(View.GONE);
                        fileHolder.fileLayoutEnd.setVisibility(View.VISIBLE);
                        fileHolder.fileSize.setText(message.getFileRealTimeSize());
                        fileHolder.progressBar.setProgress(message.getProgress());
                        break;
                    case FAILED:
                        fileHolder.progressBar.setVisibility(View.GONE);
                        fileHolder.iconFailed.setVisibility(View.VISIBLE);
                        fileHolder.fileLayoutEnd.setVisibility(View.VISIBLE);
                        fileHolder.fileStatus.setText("上传失败 · ");
                        break;
                    case COMPLETED:
                        fileHolder.progressBar.setProgress(100);
                        fileHolder.progressBar.setVisibility(View.GONE);
                        fileHolder.iconFailed.setVisibility(View.GONE);
                        fileHolder.fileLayoutEnd.setVisibility(View.GONE);
                        fileHolder.fileStatus.setText("上传完成 · ");
                        break;
                }

            }else if (holder instanceof SenderViewHolder) {
                ((SenderViewHolder) holder).message.setText(message.getContent());
            } else {
                ((ReceiverViewHolder) holder).message.setText(message.getContent());
            }
        }

    }

    static class SenderViewHolder extends RecyclerView.ViewHolder {
        PopupTextView message;
        ProgressBar progressSend;
        ImageView iconSendError;

        public SenderViewHolder(View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.text_message);
            progressSend = itemView.findViewById(R.id.progress_send_status);
            iconSendError = itemView.findViewById(R.id.icon_send_error);
        }
    }

    static class ReceiverViewHolder extends RecyclerView.ViewHolder {
        PopupTextView message;
        TextView senderName;

        public ReceiverViewHolder(View itemView) {
            super(itemView);
            message = itemView.findViewById(R.id.text_message);
            senderName = itemView.findViewById(R.id.tv_sender_name);
        }
    }

    static class FileSenderViewHolder extends RecyclerView.ViewHolder {
        ImageView iconFile;
        TextView fileName;
        TextView fileSize;
        TextView fileStatus;
        ProgressBar progressBar;
        ImageView iconFailed;
        FrameLayout fileLayoutEnd;
        TextView senderName;

        public FileSenderViewHolder(View itemView) {
            super(itemView);
            iconFile = itemView.findViewById(R.id.image_file_icon);
            fileName = itemView.findViewById(R.id.text_file_name);
            fileSize = itemView.findViewById(R.id.text_file_size);
            progressBar = itemView.findViewById(R.id.progress_upload);
            iconFailed = itemView.findViewById(R.id.icon_failed);
            fileStatus = itemView.findViewById(R.id.text_file_status);
            fileLayoutEnd = itemView.findViewById(R.id.file_layout_end);
            senderName = itemView.findViewById(R.id.tv_sender_name);
        }
    }

}
