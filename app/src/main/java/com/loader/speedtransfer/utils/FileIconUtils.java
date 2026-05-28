package com.loader.speedtransfer.utils;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.LruCache;
import android.util.TypedValue;

import com.loader.speedtransfer.R;

import java.io.File;

public class FileIconUtils {

    private static final int THUMBNAIL_CACHE_SIZE = 30;
    private static final int EXT_ICON_CACHE_SIZE = 40;

    private static final LruCache<String, Bitmap> thumbnailCache = new LruCache<>(THUMBNAIL_CACHE_SIZE);
    private static final LruCache<String, Bitmap> extIconCache = new LruCache<>(EXT_ICON_CACHE_SIZE);

    private static final int[] EXT_COLORS = {
            0xFF607D8B, 0xFF795548, 0xFF9E9E9E, 0xFF009688,
            0xFF673AB7, 0xFFFF5722, 0xFF3F51B5, 0xFFE91E63,
    };

    public static int getIconForFile(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return 0;
        }

        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();

        switch (ext) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "webp":
            case "bmp":
            case "svg":
            case "heic":
            case "ico":
            case "tiff":
            case "tif":
                return R.drawable.ic_file_image;

            case "mp4":
            case "avi":
            case "mkv":
            case "mov":
            case "flv":
            case "wmv":
            case "webm":
            case "3gp":
            case "m4v":
            case "mpg":
            case "mpeg":
                return R.drawable.ic_file_video;

            case "mp3":
            case "wav":
            case "aac":
            case "flac":
            case "ogg":
            case "wma":
            case "m4a":
            case "opus":
            case "mid":
            case "midi":
                return R.drawable.ic_file_audio;

            case "pdf":
                return R.drawable.ic_file_pdf;

            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
            case "bz2":
            case "xz":
            case "jar":
            case "war":
                return R.drawable.ic_file_archive;

            case "doc":
            case "docx":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
            case "csv":
            case "odt":
            case "ods":
            case "odp":
                return R.drawable.ic_file_pdf;

            case "apk":
                return R.drawable.ic_file_archive;

            case "txt":
            case "log":
            case "md":
            case "json":
            case "xml":
            case "html":
            case "css":
            case "js":
            case "java":
            case "kt":
            case "py":
            case "c":
            case "cpp":
            case "h":
                return R.drawable.ic_file_pdf;

            default:
                return 0;
        }
    }

    public static boolean isImageFile(String fileName) {
        if (fileName == null || !fileName.contains(".")) return false;
        String ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        switch (ext) {
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "webp":
            case "bmp":
                return true;
            default:
                return false;
        }
    }

    public static int getColorForExt(String ext) {
        if (ext == null || ext.isEmpty()) return EXT_COLORS[0];
        return EXT_COLORS[Math.abs(ext.hashCode()) % EXT_COLORS.length];
    }

    public static Bitmap generateExtensionIcon(String ext, int sizePx) {
        String key = ext + "_" + sizePx;
        Bitmap cached = extIconCache.get(key);
        if (cached != null) return cached;

        Bitmap bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        int color = getColorForExt(ext);
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(color);
        bgPaint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(0, 0, sizePx, sizePx), sizePx / 6f, sizePx / 6f, bgPaint);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sizePx * 0.38f);
        textPaint.setFakeBoldText(true);

        String label = ext.length() > 4 ? ext.substring(0, 4).toUpperCase() : ext.toUpperCase();
        Paint.FontMetrics fm = textPaint.getFontMetrics();
        float textY = sizePx / 2f - (fm.ascent + fm.descent) / 2f;
        canvas.drawText(label, sizePx / 2f, textY, textPaint);

        extIconCache.put(key, bitmap);
        return bitmap;
    }

    public static Bitmap loadThumbnail(File file, int maxWidth, int maxHeight) {
        if (file == null || !file.exists()) return null;

        String key = file.getAbsolutePath() + "_" + maxWidth;
        Bitmap cached = thumbnailCache.get(key);
        if (cached != null) return cached;

        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            opts.inSampleSize = calculateInSampleSize(opts, maxWidth, maxHeight);
            opts.inJustDecodeBounds = false;

            Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (bitmap != null) {
                bitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, maxHeight, true);
                thumbnailCache.put(key, bitmap);
            }
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    public static void clearCache() {
        thumbnailCache.evictAll();
        extIconCache.evictAll();
    }

    public static int dpToPx(Resources res, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, res.getDisplayMetrics());
    }
}
