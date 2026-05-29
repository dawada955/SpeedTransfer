package com.loader.speedtransfer.ui;

import java.io.File;

public class ChatMessage {
    private String content;
    private boolean isSender;
    private String senderName;
    private String filename;

    public ChatMessage() {}

    public enum MessageType { TEXT, FILE }
    private MessageType type;

    public enum SendStatus { SENDING, SENT, FAILED }
    private SendStatus sendStatus = SendStatus.SENT;

    public ChatMessage(String content, MessageType type, boolean isSender) {
        this.content = content;
        this.isSender = isSender;
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public boolean isSender() {
        return isSender;
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public void setSender(boolean sender) {
        isSender = sender;
    }

    public SendStatus getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(SendStatus sendStatus) {
        this.sendStatus = sendStatus;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    private File file;
    private String fileRealTimeSize;
    private int progress;

    public enum UploadStatus {
        UPLOADING, COPYING, COMPLETED, FAILED
    }

    private UploadStatus uploadStatus = UploadStatus.UPLOADING;

    public UploadStatus getUploadStatus() {
        return uploadStatus;
    }

    public void setUploadStatus(UploadStatus uploadStatus) {
        this.uploadStatus = uploadStatus;
    }

    public String getFileRealTimeSize() {
        return fileRealTimeSize;
    }

    public void setFileRealTimeSize(String fileRealTimeSize) {
        this.fileRealTimeSize = fileRealTimeSize;
    }

    public int getProgress() {
        return progress;
    }

    public void setProgress(int progress) {
        this.progress = progress;
    }

    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }
}
