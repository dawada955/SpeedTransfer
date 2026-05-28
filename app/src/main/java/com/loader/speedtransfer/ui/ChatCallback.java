package com.loader.speedtransfer.ui;

import java.io.File;

public interface ChatCallback {

    void onReceiveMessage(String ip, String content);

    void onDisplayNetwork(String ip, int PORT);

    void onReceiveFileMessage(ChatMessage fileMessage, File file);

    void onReceiveFileMessageError(ChatMessage fileMessage);

    void onReceiveFileMessageProgress(ChatMessage fileMessage, int progress, long size);

    void onReceiveFileMessageComplete(ChatMessage fileMessage, long size);
}
