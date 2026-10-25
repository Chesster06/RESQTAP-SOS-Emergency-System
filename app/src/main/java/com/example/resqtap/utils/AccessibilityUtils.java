package com.example.resqtap.utils;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.view.accessibility.AccessibilityManager;
import java.util.List;


/**
 * AccessibilityUtils
 * Utility untuk TalkBack accessibility & high contrast.
 */
public class AccessibilityUtils {
    /** Semak status TalkBackEnabled. */
    public static boolean isTalkBackEnabled(Context context) {
        AccessibilityManager am = (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (am == null) return false;
        List<AccessibilityServiceInfo> enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_SPOKEN);
        for (AccessibilityServiceInfo service : enabledServices) {
            if (service.getId().contains("TalkBack") || service.getId().contains("talkback")) {
                return true;
            }
        }
        return false;
    }
}