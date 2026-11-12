package dev.jason.gboardpatches.extension;

import android.app.Application;
import android.util.Log;

public class ExtensionApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("ExtensionApp", "ExtensionApplication.onCreate() called");
        GboardHttpServer.start(this);
    }
}
