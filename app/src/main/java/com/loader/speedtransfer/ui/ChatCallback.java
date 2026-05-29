package com.loader.speedtransfer.ui;

import java.io.File;

public interface ChatCallback {

    void onReceiveMessage(String ip, String content);

    void onDisplayNetwork(String ip, int PORT);

    void onWsUploadStart(String filename, String ip);

    void onWsUploadProgress(String filename, int progress, String speed, long bytes, int remaining);

    void onReceiveFileMessage(ChatMessage fileMessage, File file);

    void onReceiveFileMessageError(ChatMessage fileMessage);

    void onReceiveFileMessageProgress(ChatMessage fileMessage, int progress, long size);

    void onReceiveFileMessageComplete(ChatMessage fileMessage, long size);
}
