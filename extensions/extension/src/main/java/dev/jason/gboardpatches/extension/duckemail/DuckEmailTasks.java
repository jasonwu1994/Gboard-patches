package dev.jason.gboardpatches.extension.duckemail;

import android.content.Context;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class DuckEmailTasks {
    static final ExecutorService NETWORK = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "GboardDuckEmail");
        thread.setDaemon(true);
        return thread;
    });
    static String error(Context context, Exception exception) {
        int text = R.string.gboard_duck_email_network_error;
        if (exception instanceof DuckEmailApi.ApiException api) {
            text = switch (api.status) {
                case 400, 401, 403 -> R.string.gboard_duck_email_auth_error;
                case 429 -> R.string.gboard_duck_email_rate_limit;
                default -> R.string.gboard_duck_email_network_error;
            };
        }
        return GboardSettingsText.get(context, text);
    }
    private DuckEmailTasks() {}
}
