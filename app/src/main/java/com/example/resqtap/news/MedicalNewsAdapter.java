package com.example.resqtap.news;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.resqtap.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MedicalNewsAdapter extends RecyclerView.Adapter<MedicalNewsAdapter.NewsViewHolder> {

    private final Context context;
    private final List<MedicalNewsItem> items = new ArrayList<>();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(4);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> imageCache = new LruCache<>(40);

    public MedicalNewsAdapter(Context context) {
        this.context = context;
    }

    public void submitList(List<MedicalNewsItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NewsViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_medical_news, parent, false);
        return new NewsViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull NewsViewHolder holder, int position) {
        MedicalNewsItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());

        String snippet = item.getSnippet();
        if (snippet.isEmpty()) {
            holder.tvSnippet.setVisibility(View.GONE);
        } else {
            holder.tvSnippet.setVisibility(View.VISIBLE);
            holder.tvSnippet.setText(snippet);
        }

        String fallbackSource = context != null ? context.getString(R.string.medical_news_source_health) : "Health";
        String meta = (item.getSource().isEmpty() ? fallbackSource : item.getSource()) + " • " + item.getPublishedAt();
        holder.tvMeta.setText(meta);

        // Muat gambar sebenar berita
        String imgUrl = item.getImageUrl();
        holder.imgThumb.setImageDrawable(null);
        holder.imgThumb.setBackgroundColor(0xFFE5E7EB);
        if (imgUrl != null && !imgUrl.trim().isEmpty()) {
            loadImageAsync(imgUrl.trim(), holder.imgThumb);
        }

        holder.itemView.setOnClickListener(v -> {
            String url = item.getUrl();
            if (url != null && !url.isEmpty()) {
                openNewsUrl(url);
            }
        });
    }

    @Override
}
