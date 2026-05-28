package com.loader.speedtransfer.ui;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.loader.speedtransfer.R;
import com.loader.speedtransfer.utils.FileIconUtils;
import com.loader.speedtransfer.utils.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileShareAdapter extends RecyclerView.Adapter<FileShareAdapter.ViewHolder> {

    private final List<File> fileList = new ArrayList<>();
    private OnDeleteClickListener deleteListener;

    public interface OnDeleteClickListener {
        void onDelete(File file, int position);
    }

    public void setOnDeleteClickListener(OnDeleteClickListener listener) {
        this.deleteListener = listener;
    }

    public void setFiles(List<File> files) {
        fileList.clear();
        if (files != null) {
            fileList.addAll(files);
        }
        notifyDataSetChanged();
    }

    public void removeItem(int position) {
        if (position >= 0 && position < fileList.size()) {
            fileList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public int getItemCount() {
        return fileList.size();
    }

    public File getFile(int position) {
        if (position >= 0 && position < fileList.size()) {
            return fileList.get(position);
        }
        return null;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file_share, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File file = fileList.get(position);
        String fileName = file.getName();
        holder.tvFileName.setText(fileName);
        holder.tvFileSize.setText(FileUtils.getReadableFileSize(file.length()));

        int iconResId = FileIconUtils.getIconForFile(fileName);

        if (FileIconUtils.isImageFile(fileName)) {
            holder.ivFileIcon.setImageResource(R.drawable.ic_file_image);
            loadThumbnailAsync(file, holder);
        } else if (iconResId != 0) {
            holder.ivFileIcon.setImageResource(iconResId);
        } else {
            String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.') + 1) : "?";
            int sizePx = FileIconUtils.dpToPx(holder.itemView.getResources(), 36);
            Bitmap bmp = FileIconUtils.generateExtensionIcon(ext, sizePx);
            holder.ivFileIcon.setImageBitmap(bmp);
        }

        int pos = position;
        holder.ivDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDelete(file, pos);
            }
        });
    }

    private void loadThumbnailAsync(File file, ViewHolder holder) {
        final String path = file.getAbsolutePath();
        holder.ivFileIcon.setTag(path);
        int sizePx = FileIconUtils.dpToPx(holder.itemView.getResources(), 36);

        new Thread(() -> {
            Bitmap thumb = FileIconUtils.loadThumbnail(file, sizePx, sizePx);
            if (thumb != null) {
                holder.ivFileIcon.post(() -> {
                    if (path.equals(holder.ivFileIcon.getTag())) {
                        holder.ivFileIcon.setImageBitmap(thumb);
                    }
                });
            }
        }).start();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFileIcon;
        TextView tvFileName;
        TextView tvFileSize;
        ImageView ivDelete;

        ViewHolder(View itemView) {
            super(itemView);
            ivFileIcon = itemView.findViewById(R.id.iv_file_icon);
            tvFileName = itemView.findViewById(R.id.tv_file_name);
            tvFileSize = itemView.findViewById(R.id.tv_file_size);
            ivDelete = itemView.findViewById(R.id.iv_delete);
        }
    }
}
