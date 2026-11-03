package dev.jason.gboardpatches.extension;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.os.Bundle;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

public final class GboardAccessibilityService extends AccessibilityService {

    private static volatile String currentApp = "";
    private static volatile String currentTitle = "";
    private static volatile String currentActivity = "";
    private static volatile String currentFocusedId = "";
    private static volatile String currentContentDesc = "";
    private static volatile boolean isFocusedEditable = false;
    private static volatile boolean isGroupChat = false;
    private static final List<String> allVisibleText = new ArrayList<>();
    private static volatile String latestContextJson = "{\"status\":\"no_events_yet\"}";

    public static String getLatestContextJson() {
        Log.i("GboardPatches", "getLatestContextJson requested, current value: " + latestContextJson);
        return latestContextJson;
    }

    public static Bundle getScreenContext() {
        Bundle context = new Bundle();
        context.putString("app", currentApp);
        context.putString("title", currentTitle);
        context.putString("activity", currentActivity);
        context.putString("focusedId", currentFocusedId);
        context.putString("contentDesc", currentContentDesc);
        context.putBoolean("isEditable", isFocusedEditable);
        context.putBoolean("isGroup", isGroupChat);
        
        synchronized (allVisibleText) {
            context.putStringArrayList("allText", new ArrayList<>(allVisibleText));
        }
        return context;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        
        CharSequence pkg = event.getPackageName();
        if (pkg != null) {
            String pkgName = pkg.toString();
            if (!pkgName.contains("inputmethod.latin") && 
                !pkgName.contains("android.systemui") && 
                !pkgName.equals(getPackageName())) {
                
                if (!currentApp.equals(pkgName)) {
                    currentApp = pkgName;
                    resetContext();
                }
            }
        }

        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence className = event.getClassName();
            if (className != null && !className.toString().contains("inputmethod")) {
                currentActivity = className.toString();
            }
        }
        
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED 
            || event.getEventType() == AccessibilityEvent.TYPE_VIEW_FOCUSED
            || event.getEventType() == AccessibilityEvent.TYPE_VIEW_CLICKED) {
            scrapeContext();
            updateSystemSettings();
        }
    }

    @Override
    protected void onServiceConnected() {
        Log.i("GboardPatches", "GboardAccessibilityService connected");
        scrapeContext();
        updateSystemSettings();
    }

    private void resetContext() {
        currentTitle = "";
        currentFocusedId = "";
        currentContentDesc = "";
        isGroupChat = false;
        isFocusedEditable = false;
        synchronized (allVisibleText) {
            allVisibleText.clear();
        }
    }

    private void updateSystemSettings() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("{");
            sb.append("\"app\":\"").append(escape(currentApp)).append("\",");
            sb.append("\"title\":\"").append(escape(currentTitle)).append("\",");
            sb.append("\"activity\":\"").append(escape(currentActivity)).append("\",");
            sb.append("\"focusedId\":\"").append(escape(currentFocusedId)).append("\",");
            sb.append("\"contentDesc\":\"").append(escape(currentContentDesc)).append("\",");
            sb.append("\"isEditable\":").append(isFocusedEditable).append(",");
            sb.append("\"isGroup\":").append(isGroupChat).append(",");
            
            sb.append("\"allText\":[");
            synchronized (allVisibleText) {
                for (int i = 0; i < allVisibleText.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append("\"").append(escape(allVisibleText.get(i))).append("\"");
                }
            }
            sb.append("]");
            sb.append("}");
            String json = sb.toString();
            latestContextJson = json;
            Log.d("GboardPatches", "Context updated, length=" + json.length());
        } catch (Throwable e) {
            Log.e("GboardPatches", "Failed to update latest context", e);
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }

    private void scrapeContext() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) {
            Log.w("GboardPatches", "scrapeContext: root is null");
            return;
        }

        synchronized (allVisibleText) {
            allVisibleText.clear();
            traverseNode(root, 0);
        }

        AccessibilityNodeInfo focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (focusedNode != null) {
            updateNodeMetadata(focusedNode);
        }

        if ("com.whatsapp".equals(currentApp)) {
            scrapeWhatsApp(root);
        } else if ("com.microsoft.teams".equals(currentApp)) {
            scrapeTeams(root);
        } else {
            if (currentTitle.isEmpty()) findGenericTitle(root);
        }
    }

    private void updateNodeMetadata(AccessibilityNodeInfo node) {
        if (node == null) return;
        String id = node.getViewIdResourceName();
        if (id != null) currentFocusedId = id;
        CharSequence desc = node.getContentDescription();
        if (desc != null) currentContentDesc = desc.toString();
        isFocusedEditable = node.isEditable();
    }

    private void traverseNode(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 50) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0 && text.length() < 300) {
            allVisibleText.add(text.toString());
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            traverseNode(node.getChild(i), depth + 1);
        }
    }

    private void scrapeWhatsApp(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_name");
        if (!nodes.isEmpty()) {
            CharSequence text = nodes.get(0).getText();
            if (text != null) currentTitle = text.toString();
            isGroupChat = checkWhatsAppGroup(root);
        } else {
            if (allVisibleText.contains("WhatsApp") || allVisibleText.contains("Chats")) {
                currentTitle = "WhatsApp Hauptliste";
                isGroupChat = false;
            }
        }
    }

    private boolean checkWhatsAppGroup(AccessibilityNodeInfo root) {
        List<AccessibilityNodeInfo> statusNodes = root.findAccessibilityNodeInfosByViewId("com.whatsapp:id/conversation_contact_status");
        if (!statusNodes.isEmpty()) {
            CharSequence statusText = statusNodes.get(0).getText();
            if (statusText != null) {
                String text = statusText.toString().toLowerCase();
                if (text.contains(",") || text.contains("du") || text.contains("you")) return true;
                boolean isDirect = text.contains("online") || text.contains("zuletzt") || text.contains("last seen");
                return !isDirect && !text.trim().isEmpty();
            }
        }
        return false;
    }

    private void scrapeTeams(AccessibilityNodeInfo root) {
        String[] teamsIds = {"com.microsoft.teams:id/toolbar_title", "com.microsoft.teams:id/header_title", "com.microsoft.teams:id/title"};
        boolean found = false;
        for (String id : teamsIds) {
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(id);
            if (!nodes.isEmpty()) {
                CharSequence text = nodes.get(0).getText();
                if (text != null && text.length() > 0) {
                    currentTitle = text.toString();
                    found = true;
                    break;
                }
            }
        }

        if (!found) {
            if (allVisibleText.contains("Chat") || allVisibleText.contains("Aktivität")) {
                currentTitle = "Teams Hauptliste";
            } else {
                findGenericTitle(root);
            }
        }
        
        isGroupChat = false;
        for (String s : allVisibleText) {
            if (s.contains("Teilnehmer") || s.contains("Besprechung") || s.contains("Kanal")) {
                isGroupChat = true;
                break;
            }
        }
    }

    private boolean findGenericTitle(AccessibilityNodeInfo node) {
        if (node == null) return false;
        CharSequence cls = node.getClassName();
        if (cls != null && (cls.toString().contains("Toolbar") || cls.toString().contains("ActionBar"))) {
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null && child.getText() != null && !child.getText().toString().isEmpty()) {
                    currentTitle = child.getText().toString();
                    return true;
                }
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (findGenericTitle(node.getChild(i))) return true;
        }
        return false;
    }

    @Override
    public void onInterrupt() {}
}
