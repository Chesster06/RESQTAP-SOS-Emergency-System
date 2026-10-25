package com.example.resqtap.news;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.resqtap.R;
import com.example.resqtap.app.BaseActivity;
import com.example.resqtap.utils.ThemeUtils;

import java.util.List;

public class MedicalNewsActivity extends BaseActivity {

    private RecyclerView rvAllNews;
    private MedicalNewsAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressLoading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeUtils.applySavedNightMode(this);
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_medical_news);

        View root = findViewById(R.id.main);
        View headerBar = findViewById(R.id.header_bar);
        if (root != null) {
            ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
                Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                if (headerBar != null && systemBars.top > 0) {
                    headerBar.setPadding(
                            headerBar.getPaddingLeft(),
                            systemBars.top + (int) (12 * getResources().getDisplayMetrics().density),
                            headerBar.getPaddingRight(),
                            headerBar.getPaddingBottom()
                    );
                    android.view.ViewGroup.LayoutParams lp = headerBar.getLayoutParams();
                    if (lp != null) {
                        lp.height = systemBars.top + (int) (96 * getResources().getDisplayMetrics().density);
                        headerBar.setLayoutParams(lp);
                    }
                }
                v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom);
                return insets;
            });
        }

        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        rvAllNews = findViewById(R.id.rv_all_news);
        swipeRefresh = findViewById(R.id.swipe_refresh);
        progressLoading = findViewById(R.id.progress_loading);

        if (rvAllNews != null) {
            rvAllNews.setLayoutManager(new LinearLayoutManager(this));
            adapter = new MedicalNewsAdapter(this);
            rvAllNews.setAdapter(adapter);
        }

        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeResources(R.color.brand_primary);
            swipeRefresh.setOnRefreshListener(this::loadNews);
        }

        loadNews();
    }

    private void loadNews() {
        if (progressLoading != null && (swipeRefresh == null || !swipeRefresh.isRefreshing())) {
            progressLoading.setVisibility(View.VISIBLE);
        }

        MedicalNewsFetcher.fetchMedicalNews(25, new MedicalNewsFetcher.NewsCallback() {
            @Override
            public void onSuccess(List<MedicalNewsItem> newsList) {
                if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                if (adapter != null) {
                    adapter.submitList(newsList);
                }
            }

            @Override
            public void onError(Exception e) {
                if (progressLoading != null) progressLoading.setVisibility(View.GONE);
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                Toast.makeText(MedicalNewsActivity.this, R.string.medical_news_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
