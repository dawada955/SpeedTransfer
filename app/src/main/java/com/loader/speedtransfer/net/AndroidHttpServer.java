package com.loader.speedtransfer.net;

import static com.loader.speedtransfer.utils.NetworkUtils.getLocalIpAddress;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.loader.speedtransfer.field.CustomField;
import com.loader.speedtransfer.ui.ChatCallback;
import com.loader.speedtransfer.ui.ChatMessage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fi.iki.elonen.NanoWSD;

public class AndroidHttpServer extends NanoWSD {
    private static final String TAG = "AndroidHttpServer";
    private final Context context;
    private static final int PORT = CustomField.SERVICE_PORT;
    private ChatCallback chatCallback;
    private List<MyWebSocket> webSockets = new ArrayList<>();
    private String ipAddress;
    @SuppressLint("SetTextI18n")
    public AndroidHttpServer(Context context, ChatCallback callback) throws IOException {
        super(PORT);
        this.context = context;
        this.chatCallback = callback;

        // 避免系统 /data/local/tmp 空间不足导致大文件上传失败
        File tempDir = new File(context.getCacheDir(), "nanohttpd_tmp");
        tempDir.mkdirs();
        System.setProperty("java.io.tmpdir", tempDir.getAbsolutePath());

        start(SOCKET_READ_TIMEOUT, false);
        Log.i(TAG, "HTTP/WebSocket服务器已启动: ws://" + getLocalIpAddress() + ":" + PORT);
    }

    @Override
    public Response serve(IHTTPSession session) {
        // 如果是 WebSocket 升级请求，交给 NanoWSD 处理
        if ("websocket".equalsIgnoreCase(session.getHeaders().get("upgrade"))) {
            Log.d(TAG, "检测到 WebSocket 请求，交由 NanoWSD 处理");
            return super.serve(session);
        }

        Method method = session.getMethod();
        String uri = session.getUri();
        this.ipAddress = session.getRemoteIpAddress();

        Log.i(TAG, "请求URI: " + uri + " 方法: " + method);

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
        public MyWebSocket(IHTTPSession handshake) {
            super(handshake);
        }

        @Override
        protected void onOpen() {
            System.out.println("WebSocket 连接已打开");
        }

        @Override
        protected void onClose(WebSocketFrame.CloseCode code, String reason, boolean initiatedByRemote) {
            webSockets.remove(this);
        }

        @Override
        protected void onMessage(WebSocketFrame message) {
            String messageText = message.getTextPayload();
            System.out.println("收到客户端消息: " + messageText);
            // 你可以在这里处理客户端发来的消息
        }

        @Override
        protected void onPong(WebSocketFrame pong) {
            // 处理 pong 消息（ping-pong）
        }

        @Override
        protected void onException(IOException exception) {
            Log.e("Error", exception.toString());
        }
    }
    @Override
    protected WebSocket openWebSocket(IHTTPSession handshake) {
        Log.d(TAG, "尝试建立 WebSocket 连接: " + handshake.getUri());
        MyWebSocket webSocket = new MyWebSocket(handshake);
        webSockets.add(webSocket);
        return webSocket;
    }

    public void ws_handleBroadcastMessage(String message) {
        Log.d("httpMod", "WebSocket 客户端数量: " + webSockets.size());
        for (MyWebSocket ws : webSockets) {
            if (ws.isOpen()) {
                try {
                    ws.send(message);
                    Log.d("httpMod", "广播成功: " + message);
                } catch (IOException e) {
                    Log.e("httpMod", "广播失败: " + e.getMessage());
                    webSockets.remove(ws);
                }
            }
        }
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

            File uploadDir = CustomField.UploadDir;
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            File destFile = new File(uploadDir, fileName);

            long fileSize = new File(tmpFilePath).length();
            long freeSpace = uploadDir.getFreeSpace();
            if (freeSpace < fileSize + 50L * 1024 * 1024) {
                Log.e(TAG, "存储空间不足: 可用=" + freeSpace + " 需要=" + (fileSize + 50*1024*1024));
                return newFixedLengthResponse(new Response.IStatus() {
                    @Override public int getRequestStatus() { return 507; }
                    @Override public String getDescription() { return "Insufficient Storage"; }
                }, MIME_PLAINTEXT, "存储空间不足，可用 " + (freeSpace / 1024 / 1024) + "MB");
            }

            chatMsg = new ChatMessage();
            chatMsg.setSenderName(ipAddress);
            chatCallback.onReceiveFileMessage(chatMsg, destFile);

            try (InputStream in = new FileInputStream(tmpFilePath);
                 OutputStream out = new FileOutputStream(destFile)) {

                byte[] buffer = new byte[65536];
                int len;
                long total = 0;
                long lastUpdateTime = System.currentTimeMillis();

                while ((len = in.read(buffer)) > 0) {
                    out.write(buffer, 0, len);
                    total += len;

                    long now = System.currentTimeMillis();
                    if (now - lastUpdateTime >= 200) {
                        int progress = fileSize > 0 ? (int) ((total * 100) / fileSize) : -1;
                        chatCallback.onReceiveFileMessageProgress(chatMsg, progress, total);
                        lastUpdateTime = now;
                    }
                }
                out.flush();

                chatCallback.onReceiveFileMessageComplete(chatMsg, total);
                Log.i(TAG, "文件上传完成: " + fileName + " (" + total + " bytes)");
            }

            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "文件上传成功: " + fileName);

        } catch (IOException | ResponseException e) {
            Log.e(TAG, "上传异常: " + e.getMessage());
            if (chatMsg != null && e instanceof IOException &&
                    e.getMessage() != null &&
                    (e.getMessage().contains("No space") || e.getMessage().contains("ENOSPC"))) {
                chatCallback.onReceiveFileMessageError(chatMsg);
                return newFixedLengthResponse(new Response.IStatus() {
                    @Override public int getRequestStatus() { return 507; }
                    @Override public String getDescription() { return "Insufficient Storage"; }
                }, MIME_PLAINTEXT, "存储空间不足");
            }
            if (chatMsg != null) {
                chatCallback.onReceiveFileMessageError(chatMsg);
            }
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "Upload failed");
        }
    }


    private Response handleListFiles(IHTTPSession session) {
        try {
            // 获取下载目录
            File downloadDir = CustomField.DownloadDir;

            // 如果目录不存在，则创建
            if (!downloadDir.exists()) {
                downloadDir.mkdirs();
            }

            // 初始化文件元数据列表
            ArrayList<String> filenames = new ArrayList<>();
            ArrayList<Long> fileSizes = new ArrayList<>();  // 文件大小

            // 遍历目录中的所有文件
            for (File file : downloadDir.listFiles()) {
                if (file.isFile()) {  // 只处理文件
                    filenames.add(URLEncoder.encode(file.getName(), "UTF-8"));  // 编码文件名以处理特殊字符
                    fileSizes.add(file.length());
                    Log.d("fileLog_", file.getName());
                }
                Log.d("fileLog", file.getName());
            }

            // 构建 JSON 响应
            StringBuilder jsonResponse = new StringBuilder("[");
            for (int i = 0; i < filenames.size(); i++) {
                jsonResponse.append("{")
                        .append("\"filename\": \"").append(filenames.get(i)).append("\", ")
                        .append("\"size\": ").append(fileSizes.get(i))
                        .append("}");
                if (i < filenames.size() - 1) {
                    jsonResponse.append(", ");
                }
            }
            jsonResponse.append("]");

            // 返回 JSON 响应
            return newFixedLengthResponse(Response.Status.OK, "application/json", jsonResponse.toString());
        } catch (Exception e) {
            Log.e(TAG, "列出文件错误", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Internal Server Error");
        }
    }
    private Response handleDownload(IHTTPSession session) {
        Map<String, String> params = session.getParms();
        String fileName = params.get("file");

        // 文件名安全检查（网页3）
        if (fileName == null || fileName.isEmpty() || fileName.contains("../") || fileName.contains("/")) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "400 Invalid Filename");
        }

        try {
            File downloadDir = CustomField.DownloadDir;
            File file = new File(downloadDir, fileName);

            if (!file.exists()) {
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 File Not Found");
            }

            // 设置响应头（网页6、7）
            Response response = newChunkedResponse(Response.Status.OK, "application/octet-stream", new FileInputStream(file));
            String encodedFileName = URLEncoder.encode(fileName, "UTF-8").replace("+", "%20");
            response.addHeader("Content-Disposition",
                    "attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);

            // 设置文件类型头（网页7）
            String mimeType = URLConnection.guessContentTypeFromName(fileName);
            if (mimeType != null) {
                response.setMimeType(mimeType);
            }

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
            // 获取HTML文本
            StringBuilder sb = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("example_beauty_05.html")));
            String line;
            while((line = br.readLine()) != null) { sb.append(line).append('\n'); }
            String htmlStr = sb.toString();

            return newFixedLengthResponse(Response.Status.OK, "text/html", htmlStr);

        } catch (Exception e) {
            Log.e(TAG, "列出文件错误", e);
            return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, "500 Internal Server Error");
        }
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