package dev.jason.gboardpatches.extension.lanftp.android;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.content.res.Resources;
import android.os.IBinder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=33,manifest=Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class LanFtpNotificationResourceBoundaryTest {
    private static final String TARGET_PACKAGE="com.touchtype.swiftkey";
    private static final int RELOCATED_ICON=android.R.drawable.ic_menu_upload;
    private static final int LIBRARY_ICON=dev.jason.gboardpatches.extension.R.drawable.ic_lan_ftp_tile;

    @Test public void foregroundNotificationUsesNamedTargetResourceAfterRelocationAndLibraryIdCollision() {
        assertNotEquals(RELOCATED_ICON,LIBRARY_ICON);
        verify(RELOCATED_ICON,false,false,RELOCATED_ICON);
    }
    @Test public void missingCopiedResourceUsesStableAndroidFallback() {
        verify(0,false,false,android.R.drawable.stat_sys_upload);
    }
    @Test public void collidingIdentifierWithAnotherEntryCannotBecomeForegroundIcon() {
        verify(LIBRARY_ICON,true,false,android.R.drawable.stat_sys_upload);
    }
    @Test public void failedResourceLookupUsesStableAndroidFallback() {
        verify(0,false,true,android.R.drawable.stat_sys_upload);
    }
    private static void verify(int namedId,boolean wrongEntry,boolean failLookup,int expected) {
        var lifecycle=Robolectric.buildService(TargetService.class).create();
        try {
            TargetService service=lifecycle.get();
            TargetResources resources=new TargetResources(service.getResources(),namedId,wrongEntry,failLookup);
            service.targetResources=resources;
            new LanFtpNotificationController(service).startForeground();
            Notification notification=Shadows.shadowOf(service).getLastForegroundNotification();
            assertNotNull(notification);
            assertEquals(expected,notification.getSmallIcon().getResId());
            assertEquals("ic_lan_ftp_tile",resources.lookedUpName);
            assertEquals("drawable",resources.lookedUpType);
            assertEquals(TARGET_PACKAGE,resources.lookedUpPackage);
        } finally { lifecycle.destroy(); }
    }
    public static final class TargetService extends Service {
        Resources targetResources;
        @Override public Resources getResources(){return targetResources==null?super.getResources():targetResources;}
        @Override public String getPackageName(){return TARGET_PACKAGE;}
        @Override public IBinder onBind(Intent intent){return null;}
    }
    private static final class TargetResources extends Resources {
        final Resources base;
        final int namedId;final boolean wrongEntry,failLookup;
        String lookedUpName,lookedUpType,lookedUpPackage;
        @SuppressWarnings("deprecation")
        TargetResources(Resources base,int namedId,boolean wrongEntry,boolean failLookup) {
            super(base.getAssets(),base.getDisplayMetrics(),base.getConfiguration());
            this.base=base;this.namedId=namedId;this.wrongEntry=wrongEntry;this.failLookup=failLookup;
        }
        @Override public int getIdentifier(String name,String type,String packageName) {
            if(!"ic_lan_ftp_tile".equals(name) || !"drawable".equals(type)
                    || !TARGET_PACKAGE.equals(packageName)) return base.getIdentifier(name,type,packageName);
            lookedUpName=name;lookedUpType=type;lookedUpPackage=packageName;
            if(failLookup)throw new NotFoundException("simulated unavailable target table");
            return namedId;
        }
        @Override public String getResourcePackageName(int id){
            return id==namedId || id==LIBRARY_ICON ? TARGET_PACKAGE : base.getResourcePackageName(id);
        }
        @Override public String getResourceTypeName(int id){
            return id==namedId || id==LIBRARY_ICON ? "drawable" : base.getResourceTypeName(id);
        }
        @Override public String getResourceEntryName(int id) {
            if(id==LIBRARY_ICON || (id==namedId && wrongEntry))return "unrelated_target_drawable";
            return id==namedId ? "ic_lan_ftp_tile" : base.getResourceEntryName(id);
        }
    }
}
