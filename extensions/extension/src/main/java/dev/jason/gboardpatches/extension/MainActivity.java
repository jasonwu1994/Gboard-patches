package dev.jason.gboardpatches.extension;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setGravity(Gravity.CENTER);

        TextView tv = new TextView(this);
        boolean enabled = isAccessibilityServiceEnabled(this, GboardAccessibilityService.class);
        tv.setText("Accessibility Service Status: " + (enabled ? "AKTIVIERT" : "DEAKTIVIERT"));
        tv.setTextSize(18);
        tv.setPadding(0, 0, 0, 32);
        
        Button btn = new Button(this);
        btn.setText("Zu den Bedienungshilfen");
        btn.setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        Button btnSettings = new Button(this);
        btnSettings.setText("System-Settings Schreibrecht prüfen");
        btnSettings.setOnClickListener(v -> {
            if (!Settings.System.canWrite(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                tv.setText("Schreibrecht: OK!");
            }
        });

        layout.addView(tv);
        layout.addView(btn);
        layout.addView(btnSettings);
        setContentView(layout);
    }

    private static boolean isAccessibilityServiceEnabled(Context context, Class<?> service) {
        String serviceName = context.getPackageName() + "/" + service.getName();
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabledServices)) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':');
        splitter.setString(enabledServices);
        while (splitter.hasNext()) {
            if (serviceName.equalsIgnoreCase(splitter.next())) return true;
        }
        return false;
    }
}
