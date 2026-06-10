package com.example.resqtap.highlight;

public class HighlightItem {
    private String id;
    private String title;
    private String imageUrl;
    private String actionUrl;
    private boolean active = true;
    private long createdAt;
    private int order;
    private int fallbackDrawable = 0;

    public HighlightItem() {
    }

    public HighlightItem(String id, String title, String imageUrl, String actionUrl, boolean active, long createdAt, int order) {
        this.id = id;
        this.title = title;
        this.imageUrl = imageUrl;
        this.actionUrl = actionUrl;
        this.active = active;
        this.createdAt = createdAt;
        this.order = order;
    }

    public HighlightItem(String id, String title, int fallbackDrawable, String actionUrl) {
        this.id = id;
        this.title = title;
        this.fallbackDrawable = fallbackDrawable;
        this.actionUrl = actionUrl;
        this.active = true;
    }

    public String getId() {
        return id != null ? id : "";
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getImageUrl() {
        return imageUrl != null ? imageUrl : "";
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getActionUrl() {
        return actionUrl != null ? actionUrl : "";
    }

    public void setActionUrl(String actionUrl) {
        this.actionUrl = actionUrl;
    }
}
