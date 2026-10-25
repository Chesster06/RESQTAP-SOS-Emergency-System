package com.example.resqtap.highlight;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

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

public class HighlightAdapter extends RecyclerView.Adapter<HighlightAdapter.HighlightViewHolder> {

    private final Context context;
    private final List<HighlightItem> items = new ArrayList<>();
    private final ExecutorService imageExecutor = Executors.newFixedThreadPool(3);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> imageCache = new LruCache<>(25);

    public HighlightAdapter(Context context) {
        this.context = context;
    }

    public void submitList(List<HighlightItem> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HighlightViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_highlight_card, parent, false);
        return new HighlightViewHolder(v);
    }

    private int bannerHeightPx = 0;

    private int getBannerHeightPx() {
        if (bannerHeightPx == 0) {
            bannerHeightPx = (int) (180 * context.getResources().getDisplayMetrics().density + 0.5f);
        }
        return bannerHeightPx;
    }

    private void applyDynamicAspectRatio(View itemView, int width, int height) {
        if (width <= 0 || height <= 0 || itemView == null) return;
        int h = getBannerHeightPx();
        float ratio = (float) width / (float) height;
        // Keep ratio within a reasonable bound (0.4 portrait to 2.6 landscape)
        ratio = Math.max(0.42f, Math.min(ratio, 2.6f));
        int targetW = Math.round(h * ratio);

        ViewGroup.LayoutParams lp = itemView.getLayoutParams();
        if (lp != null && (lp.width != targetW || lp.height != h)) {
            lp.width = targetW;
            lp.height = h;
            itemView.setLayoutParams(lp);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull HighlightViewHolder holder, int position) {
        HighlightItem item = items.get(position);

        // Fallback default image based on position
        int defRes = R.drawable.img_highlight_1;
        if (item.getFallbackDrawable() != 0) {
            defRes = item.getFallbackDrawable();
        } else if (position % 4 == 1) {
            defRes = R.drawable.img_highlight_2;
        } else if (position % 4 == 2) {
            defRes = R.drawable.img_highlight_3;
        } else if (position % 4 == 3) {
            defRes = R.drawable.img_highlight_4;
        }
        holder.imgBanner.setImageResource(defRes);
        android.graphics.drawable.Drawable defDrawable = ContextCompat.getDrawable(context, defRes);
        if (defDrawable != null && defDrawable.getIntrinsicWidth() > 0 && defDrawable.getIntrinsicHeight() > 0) {
            applyDynamicAspectRatio(holder.itemView, defDrawable.getIntrinsicWidth(), defDrawable.getIntrinsicHeight());
        }

        String imgUrl = item.getImageUrl();
        if (imgUrl != null && !imgUrl.trim().isEmpty()) {
            imgUrl = imgUrl.trim();
            String cacheKey = (item.getId() != null ? item.getId() : "") + "_" + imgUrl.hashCode();
            Bitmap cached = imageCache.get(cacheKey);

            if (cached != null) {
                holder.imgBanner.setImageBitmap(cached);
                applyDynamicAspectRatio(holder.itemView, cached.getWidth(), cached.getHeight());
            } else if (imgUrl.startsWith("data:image") || imgUrl.contains(";base64,")) {
                try {
                    int commaIdx = imgUrl.indexOf(",");
                    String b64 = commaIdx >= 0 ? imgUrl.substring(commaIdx + 1) : imgUrl;
                    byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
                    Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bmp != null) {
                        imageCache.put(cacheKey, bmp);
                        holder.imgBanner.setImageBitmap(bmp);
                        applyDynamicAspectRatio(holder.itemView, bmp.getWidth(), bmp.getHeight());
                    }
                } catch (Exception e) {
                    android.util.Log.e("HighlightAdapter", "Failed to decode base64 banner", e);
                }
            } else if (imgUrl.startsWith("http://") || imgUrl.startsWith("https://")) {
                loadImageAsync(imgUrl, holder.imgBanner, holder.itemView);
            } else {
                // Only check keywords if it's a local asset path reference
                String lower = imgUrl.toLowerCase();
                int matchedRes = 0;
                if (lower.contains("highlight_1") || lower.contains("cpr") || lower.contains("people_first")) {
                    matchedRes = R.drawable.img_highlight_1;
                } else if (lower.contains("highlight_2") || lower.contains("darah") || lower.contains("different")) {
                    matchedRes = R.drawable.img_highlight_2;
                } else if (lower.contains("highlight_3") || lower.contains("peka") || lower.contains("access_for_all")) {
                    matchedRes = R.drawable.img_highlight_3;
                } else if (lower.contains("highlight_4") || lower.contains("sos")) {
                    matchedRes = R.drawable.img_highlight_4;
                }
                if (matchedRes != 0) {
                    holder.imgBanner.setImageResource(matchedRes);
                    android.graphics.drawable.Drawable md = ContextCompat.getDrawable(context, matchedRes);
                    if (md != null && md.getIntrinsicWidth() > 0 && md.getIntrinsicHeight() > 0) {
                        applyDynamicAspectRatio(holder.itemView, md.getIntrinsicWidth(), md.getIntrinsicHeight());
                    }
                }
            }
        }

        holder.itemView.setOnClickListener(v -> {
            String url = item.getActionUrl();
            if (url != null && !url.trim().isEmpty()) {
                openUrl(url.trim());
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void openUrl(String urlStr) {
        if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
            urlStr = "https://" + urlStr;
        }
        try {
            CustomTabsIntent.Builder builder = new CustomTabsIntent.Builder();
            builder.setShowTitle(true);
            builder.setToolbarColor(ContextCompat.getColor(context, R.color.brand_primary));
            CustomTabsIntent customTabsIntent = builder.build();
            customTabsIntent.launchUrl(context, Uri.parse(urlStr));
        } catch (Exception e) {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlStr));
                context.startActivity(intent);
            } catch (Exception ignored) {
            }
        }
    }

    private void loadImageAsync(String urlStr, ImageView targetView, View itemView) {
        Bitmap cached = imageCache.get(urlStr);
        if (cached != null) {
            targetView.setImageBitmap(cached);
            applyDynamicAspectRatio(itemView, cached.getWidth(), cached.getHeight());
            return;
        }

        imageExecutor.execute(() -> {
            try {
                URL url = new URL(urlStr);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestProperty("User-Agent", "ResQTap-App/1.0");
                if (conn.getResponseCode() == 200) {
                    InputStream is = conn.getInputStream();
                    Bitmap bitmap = BitmapFactory.decodeStream(is);
                    is.close();
                    conn.disconnect();
                    if (bitmap != null) {
                        imageCache.put(urlStr, bitmap);
                        mainHandler.post(() -> {
                            targetView.setImageBitmap(bitmap);
                            applyDynamicAspectRatio(itemView, bitmap.getWidth(), bitmap.getHeight());
                        });
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    static class HighlightViewHolder extends RecyclerView.ViewHolder {
        ImageView imgBanner;

        public HighlightViewHolder(@NonNull View itemView) {
            super(itemView);
            imgBanner = itemView.findViewById(R.id.img_highlight_banner);
        }
    }
}
