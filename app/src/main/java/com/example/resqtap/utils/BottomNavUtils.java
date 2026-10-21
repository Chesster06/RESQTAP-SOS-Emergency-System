package com.example.resqtap.utils;
import com.example.resqtap.R;

import com.example.resqtap.chat.QuickMessageActivity;
import com.example.resqtap.home.MainActivity;
import com.example.resqtap.profile.ProfileActivity;
import com.example.resqtap.room.RoomListActivity;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.IdRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;


/**
 * BottomNavUtils
 * Helper animasi dan transition untuk bottom navigation bar.
 */
public final class BottomNavUtils {
    private static final int NAV_HEIGHT_DP = 76;
    private static final int NAV_SIDE_MARGIN_DP = 16;
    private static final int NAV_BOTTOM_MARGIN_DP = 12;
    private static final int NAV_CONTENT_BOTTOM_PADDING_DP = 0;

    private BottomNavUtils() {
    }

    /** Setup dan konfigurasi . */
    public static void setup(BottomNavigationView nav, Activity activity, @IdRes int selectedItemId) {
        if (nav == null || activity == null) return;

        try {
            activity.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        } catch (Exception ignored) {
        }

        ColorStateList iconTint = ContextCompat.getColorStateList(activity, R.color.bottom_nav_icon_tint);
        ColorStateList textTint = ContextCompat.getColorStateList(activity, R.color.bottom_nav_text_tint);
        nav.setItemIconTintList(iconTint);
        nav.setItemTextColor(textTint);
        nav.setMinimumHeight(dp(nav, NAV_HEIGHT_DP));

        if (selectedItemId != 0) {
            try {
                nav.setSelectedItemId(selectedItemId);
            } catch (Exception ignored) {
            }
        }

        applyResponsiveInsets(nav);
        tuneItemSpacing(nav);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == selectedItemId) return true;

            if (id == R.id.nav_bottom_home) {
                switchTo(activity, MainActivity.class);
                return true;
            }
            if (id == R.id.nav_bottom_quick_message) {
                switchTo(activity, QuickMessageActivity.class);
                return true;
            }
            if (id == R.id.nav_bottom_room) {
                switchTo(activity, RoomListActivity.class);
                return true;
            }
            if (id == R.id.nav_bottom_friends) {
                switchTo(activity, com.example.resqtap.friend.FriendsActivity.class);
                return true;
            }
            if (id == R.id.nav_bottom_profile) {
                switchTo(activity, ProfileActivity.class);
                return true;
            }
            return false;
        });
    }

    private static void applyResponsiveInsets(BottomNavigationView nav) {
        final int baseLeft = dp(nav, NAV_SIDE_MARGIN_DP);
        final int baseRight = dp(nav, NAV_SIDE_MARGIN_DP);
        final int baseBottom = dp(nav, NAV_BOTTOM_MARGIN_DP);
        final int navHeight = dp(nav, NAV_HEIGHT_DP);
        final int contentBottomPadding = dp(nav, NAV_CONTENT_BOTTOM_PADDING_DP);

        nav.setPadding(nav.getPaddingLeft(), nav.getPaddingTop(), nav.getPaddingRight(), contentBottomPadding);
        ViewCompat.setOnApplyWindowInsetsListener(nav, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.LayoutParams rawParams = v.getLayoutParams();
            if (rawParams instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rawParams;
                params.height = navHeight;
                params.leftMargin = baseLeft + bars.left;
                params.rightMargin = baseRight + bars.right;
                params.bottomMargin = baseBottom + bars.bottom;
                v.setLayoutParams(params);
            }
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), contentBottomPadding);
            return insets;
        });
        ViewCompat.requestApplyInsets(nav);
    }

    private static void tuneItemSpacing(BottomNavigationView nav) {
        nav.setClipChildren(false);
        nav.setClipToPadding(false);
        nav.post(() -> {
            int iconContainerId = com.google.android.material.R.id.navigation_bar_item_icon_container;
            int labelsGroupId = com.google.android.material.R.id.navigation_bar_item_labels_group;
            int largeLabelId = com.google.android.material.R.id.navigation_bar_item_large_label_view;
            int smallLabelId = com.google.android.material.R.id.navigation_bar_item_small_label_view;

            setClipOff(nav);

            for (View iconContainer : findDescendantsById(nav, iconContainerId)) {
                iconContainer.setTranslationY(dp(nav, 3));
            }
            for (View labelsGroup : findDescendantsById(nav, labelsGroupId)) {
                labelsGroup.setTranslationY(dp(nav, 5));
            }
            for (View label : findDescendantsById(nav, largeLabelId)) {
                TextView textView = asTextView(label);
                if (textView != null) {
                    textView.setIncludeFontPadding(false);
                    textView.setSingleLine(true);
                    textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
                    textView.setLineSpacing(0f, 1f);
                }
            }
            for (View label : findDescendantsById(nav, smallLabelId)) {
                TextView textView = asTextView(label);
                if (textView != null) {
                    textView.setIncludeFontPadding(false);
                    textView.setSingleLine(true);
                    textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11);
                    textView.setLineSpacing(0f, 1f);
                }
            }
        });
    }

    private static void setClipOff(View root) {
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        group.setClipChildren(false);
        group.setClipToPadding(false);
        for (int i = 0; i < group.getChildCount(); i++) {
            setClipOff(group.getChildAt(i));
        }
    }

    private static List<View> findDescendantsById(View root, int id) {
        List<View> views = new ArrayList<>();
        collectDescendantsById(root, id, views);
        return views;
    }

    private static void collectDescendantsById(View root, int id, List<View> views) {
        if (root == null) return;
        if (root.getId() == id) views.add(root);
        if (!(root instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            collectDescendantsById(group.getChildAt(i), id, views);
        }
    }

    private static TextView asTextView(View view) {
        return view instanceof TextView ? (TextView) view : null;
    }

    private static int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static float dp(View view, float value) {
        return value * view.getResources().getDisplayMetrics().density;
    }

    private static void switchTo(Activity from, Class<?> to) {
        if (from.getClass().equals(to)) return;
        Intent intent = new Intent(from, to);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        from.startActivity(intent);
        from.finish();
        try {
            from.overridePendingTransition(0, 0);
        } catch (Exception ignored) {
        }
    }
}
