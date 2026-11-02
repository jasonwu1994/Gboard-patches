package dev.jason.gboardpatches.extension.webclipboard;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

public final class WebClipboardLocalNetworkPermission {
    public static final String NAME="android.permission.ACCESS_LOCAL_NETWORK";
    private WebClipboardLocalNetworkPermission(){}
    public static boolean isGranted(Context context) {
        if(context==null)return false;
        try {
            int target=context.getApplicationInfo().targetSdkVersion;
            return !isRequired(Build.VERSION.SDK_INT,target)
                    || context.checkSelfPermission(NAME)==PackageManager.PERMISSION_GRANTED;
        } catch(Throwable unavailable){return false;}
    }
    static boolean isRequired(int platform,int target){return platform>=37 && target>=37;}
}
