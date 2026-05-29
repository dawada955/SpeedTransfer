package com.loader.speedtransfer.net;

import static com.loader.speedtransfer.utils.NetworkUtils.getLocalIpAddress;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.loader.speedtransfer.field.CustomField;
import com.loader.speedtransfer.ui.ChatCallback;
import com.loader.speedtransfer.ui.ChatMessage;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import fi.iki.elonen.NanoWSD;

public class AndroidHttpServer extends NanoWSD {
    private static final String TAG = "AndroidHttpServer";
    private final Context context;
    private static final int PORT = CustomField.SERVICE_PORT;
    private ChatCallback chatCallback;
    private List<MyWebSocket> webSockets = new ArrayList<>();
    private String ipAddress;
    private ConcurrentHashMap<String, ChatMessage> uploadMessageMap = new ConcurrentHashMap<>();

    @SuppressLint("SetTextI18n")
    public AndroidHttpServer(Context context, ChatCallback callback) throws IOException {
        super(PORT);
        this.context = context;
        this.chatCallback = callback;

        File tempDir = new File(context.getCacheDir(), "nanohttpd_tmp");
        tempDir.mkdirs();
        System.setProperty("java.io.tmpdir", tempDir.getAbsolutePath());

        start(SOCKET_READ_TIMEOUT, false);
        Log.i(TAG, "HTTP/WebSocket服务器已启动: ws://" + getLocalIpAddress() + ":" + PORT);
    }

    @Override
    public Response serve(IHTTPSession session) {
        if ("websocket".equalsIgnoreCase(session.getHeaders().get("upgrade"))) {
            return super.serve(session);
        }

        Method method = session.getMethod();
        String uri = session.getUri();
        this.ipAddress = session.getRemoteIpAddress();

        if (Method.GET.equals(method)) {
            if (uri.startsWith("/download")) {
                return handleDownload(session);
            } else if (uri.startsWith("/fileList")) {
                return handleListFiles(session);
            }
            return handleHtmlContent(session);
        } else if (Method.POST.equals(method)) {
            if (uri.startsWith("/upload")) {
                return handleUpload(session);
            } else if (uri.startsWith("/chat")) {
                return handleReceiveChat(session);
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found");
    }

    public class MyWebSocket extends WebSocket {
        private String remoteIp;

        public MyWebSocket(IHTTPSession handshake) {
            super(handshake);
            this.remoteIp = handshake.getRemoteIpAddress();
        }

        @Override
        protected void onOpen() {
            Log.d(TAG, "WebSocket 连接已打开, 客户端: " + remoteIp);
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            webSockets.remove(this);
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            String text = message.getTextPayload();
            try {
                JSONObject json = new JSONObject(text);
                String type = json.optString("type");
                String filename = json.optString("filename");
                if (filename == null || filename.isEmpty()) return;

                switch (type) {
                    case "upload_start":
                        chatCallback.onWsUploadStart(filename, remoteIp);
                        break;
                    case "upload_progress":
                        int progress = json.optInt("progress", 0);
                        String speed = json.optString("speed", "");
                        long bytes = json.optLong("bytes", 0);
                        int remaining = json.optInt("remaining", 0);
                        chatCallback.onWsUploadProgress(filename, progress, speed, bytes, remaining);
                        break;
                }
            } catch (Exception e) {
                Log.w(TAG, "WS消息解析失败: " + e.getMessage());
            }
        }

        @Override
        protected void onPong(WebSocketFrame pong) {}

        @Override
        protected void onException(IOException exception) {
            Log.e(TAG, "WS异常: " + exception.toString());
        }
    }

    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        MyWebSocket webSocket = new MyWebSocket(handshake);
        webSockets.add(webSocket);
        return webSocket;
    }

    public void ws_handleBroadcastMessage(String message) {
        for (MyWebSocket ws : webSockets) {
            if (ws.isOpen()) {
                try {
                    ws.send(message);
                } catch (IOException e) {
                    webSockets.remove(ws);
                }
            }
        }
    }

    public void putUploadMessage(String filename, ChatMessage msg) {
        uploadMessageMap.put(filename, msg);
    }

    public ChatMessage getUploadMessage(String filename) {
        return uploadMessageMap.get(filename);
    }

    public void removeUploadMessage(String filename) {
        uploadMessageMap.remove(filename);
    }

    private Response handleUpload(IHTTPSession session) {
        ChatMessage chatMsg = null;
        try {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);

            String tmpFilePath = files.get("file");
            if (tmpFilePath == null) {
                return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "400 Bad Request");
            }

            String fileName = session.getParms().get("originalFilename");
            if (fileName != null && !fileName.isEmpty()) {
                fileName = URLDecoder.decode(fileName, "UTF-8");
            }
            if (fileName == null || fileName.isEmpty()) {
                String cd = session.getHeaders().get("content-disposition");
                if (cd != null) {
                    for (String part : cd.split(";")) {
                        if (part.trim().startsWith("filename=")) {
                            fileName = part.substring(part.indexOf('=') + 1).trim().replace("\"", "");
                            break;
                        }
                    }
                }
            }
            if (fileName == null || fileName.isEmpty()) {
                fileName = new File(tmpFilePath).getName();
            }

            File shareDir = CustomField.ShareDir;
            if (!shareDir.exists()) {
                shareDir.mkdirs();
            }
            File destFile = new File(shareDir, fileName);

            long fileSize = new File(tmpFilePath).length();
            long freeSpace = shareDir.getFreeSpace();
            if (freeSpace < fileSize + 50L * 1024 * 1024) {
                Log.e(TAG, "存储空间不足: 可用=" + freeSpace + " 需要=" + (fileSize + 50*1024*1024));
                return newFixedLengthResponse(new Response.IStatus() {
                    @Override public int getRequestStatus() { return 507; }
                    @Override public String getDescription() { return "Insufficient Storage"; }
                }, MIME_PLAINTEXT, "存储空间不足");
            }

            // 复用 WS 已创建的气泡
            chatMsg = uploadMessageMap.remove(fileName);
            if (chatMsg == null) {
                chatMsg = new ChatMessage();
                chatMsg.setFilename(fileName);
            }
            chatMsg.setSenderName(ipAddress);
            chatMsg.setUploadStatus(ChatMessage.UploadStatus.COPYING);
            chatCallback.onReceiveFileMessage(chatMsg, destFile);

            try (InputStream in = new FileInputStream(tmpFilePath);
                 OutputStream out = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[65536];
                int len;
                long total = 0;
                long lastProgressTime = System.currentTimeMillis();
                long copyStartTime = lastProgressTime;

                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                    total += len;

                    long now = System.currentTimeMillis();
                    if (now - lastProgressTime >= 200 && fileSize > 0) {
                        int progress = (int) ((total * 100) / fileSize);
                        long elapsedMs = now - copyStartTime;
                        double speedBps = elapsedMs > 0 ? (total * 1000.0 / elapsedMs) : 0;
                        String speedStr = formatSpeed(speedBps);
                        long remaining = speedBps > 0 ? (long) ((fileSize - total) / speedBps) : 0;

                        chatCallback.onReceiveFileMessageProgress(chatMsg, progress, total);
                        wsBroadcastCopyProgress(fileName, progress, speedStr, total, remaining);
                        lastProgressTime = now;
                    }
                }
                out.flush();

                chatCallback.onReceiveFileMessageComplete(chatMsg, total);
                wsBroadcastCopyComplete(fileName);
                Log.i(TAG, "文件上传完成: " + fileName + " (" + total + " bytes)");
            }

            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "文件上传成功: " + fileName);

        } catch (IOException | ResponseException e) {
            Log.e(TAG, "上传异常: " + e.getMessage());
            if (chatMsg != null) {
                chatCallback.onReceiveFileMessageError(chatMsg);
            }
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed");
        }
    }

    private Response handleListFiles(IHTTPSession session) {
        try {
            File shareDir = CustomField.ShareDir;
            if (!shareDir.exists()) {
                shareDir.mkdirs();
            }

            ArrayList<String> filenames = new ArrayList<>();
            ArrayList<Long> fileSizes = new ArrayList<>();

            for (File file : shareDir.listFiles()) {
                if (file.isFile()) {
                    filenames.add(URLEncoder.encode(file.getName(), "UTF-8"));
                    fileSizes.add(file.length());
                }
            }

            StringBuilder jsonResponse = new StringBuilder("[");
            for (int i = 0; i < filenames.size(); i++) {
                jsonResponse.append("{")
                        .append("\"filename\": \"").append(filenames.get(i)).append("\", ")
                        .append("\"size\": ").append(fileSizes.get(i))
                        .append("}");
                if (i < filenames.size() - 1) jsonResponse.append(", ");
            }
            jsonResponse.append("]");

            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse.toString());
        } catch (Exception e) {
            Log.e(TAG, "列出文件错误", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Internal Server Error");
        }
    }

    private Response handleDownload(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String fileName = params.get("file");

        if (fileName == null || fileName.isEmpty() || fileName.contains("../") || fileName.contains("/")) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "400 Invalid Filename");
        }

        try {
            File shareDir = CustomField.ShareDir;
            File file = new File(shareDir, fileName);

            if (!file.exists()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 File Not Found");
            }

            Response response = newChunkedResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(file));
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            response.addHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);

            String mimeType = URLConnection.guessContentTypeFromName(fileName);
            if (mimeType != null) response.setMimeType(mimeType);

            return response;
        } catch (IOException e) {
            Log.e(TAG, "下载文件错误", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Internal Error");
        }
    }

    private Response handleReceiveChat(IHTTPSession session) {
        try {
            Map<String, String> body = new HashMap<>();
            session.parseBody(body);
            String postData = body.get("postData");
            String message = extractMessage(postData);
            chatCallback.onReceiveMessage(ipAddress, message);
            return newFixedLengthResponse(Response.Status.OK, "application/json", "{\"status\":\"ok\"}");
        } catch (Exception e) {
            e.printStackTrace();
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/plain", "出错：" + e.getMessage());
        }
    }

    private Response handleHtmlContent(IHTTPSession session) {
        try {
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("example_beauty_05.html")));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
            return newFixedLengthResponse(Response.Status.OK, "text/html", sb.toString());
        } catch (Exception e) {
            Log.e(TAG, "列出文件错误", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Internal Server Error");
        }
    }

    private void wsBroadcastCopyProgress(String filename, int progress, String speed, long bytes, long remaining) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "copy_progress");
            json.put("filename", filename);
            json.put("progress", progress);
            json.put("speed", speed);
            json.put("bytes", bytes);
            json.put("remaining", remaining);
            ws_handleBroadcastMessage(json.toString());
        } catch (Exception ignored) {}
    }

    private void wsBroadcastCopyComplete(String filename) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", "copy_complete");
            json.put("filename", filename);
            ws_handleBroadcastMessage(json.toString());
        } catch (Exception ignored) {}
    }

    private String formatSpeed(double bytesPerSec) {
        if (bytesPerSec < 1024) return String.format("%.0f B/s", bytesPerSec);
        double kbps = bytesPerSec / 1024;
        if (kbps < 1024) return String.format("%.1f KB/s", kbps);
        double mbps = kbps / 1024;
        return String.format("%.1f MB/s", mbps);
    }

    private String extractMessage(String json) {
        if (json == null) return "";
        int index = json.indexOf("\"message\"");
        if (index == -1) return "";
        int start = json.indexOf(":", index) + 1;
        int end = json.indexOf("\"", start + 1);
        return json.substring(start + 1, end);
    }
}
