package com.example.resqtap.news;

public class MedicalNewsItem {
    private final String title;
    private final String snippet;
    private final String url;
    private final String imageUrl;
    private final String source;
    private final String publishedAt;

    public MedicalNewsItem(String title, String snippet, String url, String imageUrl, String source, String publishedAt) {
        this.title = title != null ? title.trim() : "";
        this.snippet = snippet != null ? snippet.trim() : "";
        this.url = url != null ? url.trim() : "";
        this.imageUrl = imageUrl != null ? imageUrl.trim() : "";
        this.source = source != null ? source.trim() : "";
        this.publishedAt = publishedAt != null ? publishedAt.trim() : "";
    }

    public String getTitle() {
        return title;
    }

    public String getSnippet() {
        return snippet;
    }

    public String getUrl() {
        return url;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getSource() {
        return source;
    }

    public String getPublishedAt() {
        return publishedAt;
    }
}
