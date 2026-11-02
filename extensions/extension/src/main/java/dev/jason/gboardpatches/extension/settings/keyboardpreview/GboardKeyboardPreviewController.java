package dev.jason.gboardpatches.extension.settings.keyboardpreview;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.util.Log;

import java.util.List;
import java.util.function.Supplier;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Shared Patches-shell affordance for opening an editor that immediately shows Gboard. */
public final class GboardKeyboardPreviewController {
    private static final String TAG = "GboardPatches";
    private static final String[] RICH_CONTENT_MIME_TYPES = new String[] {
            "image/gif",
            "image/*"
    };
    private static final int BUTTON_SIZE_DP = 56;
    private static final int BUTTON_MARGIN_DP = 16;
    private static final int CONTENT_CLEARANCE_DP = 88;

    private final Activity activity;
    private final Runnable onDialogShown;
    private final Runnable onDialogDismissed;
    private final int accentColor;
    private final int iconColor;
    private ImageButton launcher;

    public GboardKeyboardPreviewController(Activity activity,
            Supplier<List<String>> targetPackageNames,
            Runnable onDialogShown,
            Runnable onDialogDismissed,
            int accentColor,
            int iconColor) {
        this.activity = activity;
        this.onDialogShown = onDialogShown;
        this.onDialogDismissed = onDialogDismissed;
        this.accentColor = accentColor;
        this.iconColor = iconColor;
    }

    public void attachTo(FrameLayout root) {
        runSafely("attach keyboard preview", () -> {
            if (root == null || launcher != null) return;
            launcher = buildLauncher();
            root.addView(launcher, launcherLayoutParams(0, 0));
        });
    }

    public void updateSafeAreaInsets(int rightInset, int bottomInset) {
        runSafely("update keyboard preview insets", () -> {
            if (launcher != null) {
                launcher.setLayoutParams(launcherLayoutParams(rightInset, bottomInset));
            }
        });
    }

    public int contentBottomPadding(int bottomInset) {
        try {
            return Math.max(0, bottomInset) + dp(CONTENT_CLEARANCE_DP);
        } catch (Throwable throwable) {
            logWarningSafely("Failed to resolve keyboard preview content padding", throwable);
            return Math.max(0, bottomInset);
        }
    }

    public void detach() {
        runSafely("detach keyboard preview", () -> {
            if (launcher != null && launcher.getParent() instanceof ViewGroup) {
                ((ViewGroup) launcher.getParent()).removeView(launcher);
            }
            launcher = null;
        });
    }

    private ImageButton buildLauncher() {
        ImageButton button = new ImageButton(activity);
        button.setBackground(buildLauncherBackground());
        button.setImageDrawable(loadKeyboardDrawable());
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(16), dp(16), dp(16), dp(16));
        String action = text(R.string.gboard_patches_keyboard_preview_action);
        button.setContentDescription(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) button.setTooltipText(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) button.setElevation(dp(8));
        button.setOnClickListener(view ->
                runSafely("open keyboard preview", this::showPreviewDialog));
        return button;
    }

    private FrameLayout.LayoutParams launcherLayoutParams(int rightInset, int bottomInset) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(BUTTON_SIZE_DP), dp(BUTTON_SIZE_DP), Gravity.END | Gravity.BOTTOM);
        params.rightMargin = dp(BUTTON_MARGIN_DP) + Math.max(0, rightInset);
        params.bottomMargin = dp(BUTTON_MARGIN_DP) + Math.max(0, bottomInset);
        return params;
    }

    private Drawable buildLauncherBackground() {
        GradientDrawable content = new GradientDrawable();
        content.setColor(accentColor);
        content.setCornerRadius(dp(16));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(16));
        return new RippleDrawable(
                ColorStateList.valueOf(withAlpha(iconColor, 0.16f)), content, mask);
    }

    private Drawable loadKeyboardDrawable() {
        return new KeyboardIconDrawable(iconColor);
    }

    private void showPreviewDialog() {
        LinearLayout container = new LinearLayout(activity);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(24), dp(8), dp(24), dp(8));

        EditText input = new RichContentSinkEditText(activity);
        input.setContentDescription(text(
                R.string.gboard_patches_keyboard_preview_input_accessibility));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        container.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(text(R.string.gboard_patches_keyboard_preview_title))
                .setView(container)
                .setPositiveButton(text(R.string.gboard_patches_dialog_close), null)
                .create();
        dialog.setOnShowListener(ignored -> {
            runSafely("initialize keyboard preview dialog", () -> {
                TextView positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
                if (positiveButton != null) positiveButton.setTextColor(accentColor);
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setSoftInputMode(
                            WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                }
                input.post(() -> runSafely("show keyboard from preview", () -> {
                    input.requestFocus();
                    InputMethodManager manager = (InputMethodManager) activity.getSystemService(
                            Context.INPUT_METHOD_SERVICE);
                    if (manager != null) {
                        manager.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
                    }
                }));
            });
        });
        dialog.setOnDismissListener(ignored -> runCallbackSafely(
                "notify keyboard preview dismissed", onDialogDismissed));
        try {
            dialog.show();
            runCallbackSafely("notify keyboard preview shown", onDialogShown);
        } catch (Throwable throwable) {
            logWarningSafely("Failed to show keyboard preview dialog", throwable);
        }
    }

    private void runCallbackSafely(String operation, Runnable callback) {
        runSafely(operation, callback);
    }

    private void runSafely(String operation, Runnable action) {
        if (action == null) return;
        try {
            action.run();
        } catch (Throwable throwable) {
            logWarningSafely("Failed to " + operation, throwable);
        }
    }

    private void logWarningSafely(String message, Throwable throwable) {
        try {
            Log.w(TAG, message, throwable);
        } catch (Throwable ignored) {
            // Shared settings-shell callbacks must not affect the host app.
        }
    }

    private String text(int resourceId) {
        return GboardSettingsText.get(activity, resourceId);
    }

    private int withAlpha(int color, float alphaFraction) {
        int alpha = Math.round(Color.alpha(color) * alphaFraction);
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private final class KeyboardIconDrawable extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        KeyboardIconDrawable(int color) {
            paint.setColor(color);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            if (canvas == null || getBounds().isEmpty()) return;
            int saveCount = canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 960f, getBounds().height() / 960f);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(80f);
            canvas.drawRoundRect(120f, 240f, 840f, 720f, 40f, 40f, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int column = 0; column < 5; column++) {
                float left = 200f + (column * 120f);
                drawKeyboardKey(canvas, left, 320f, left + 80f, 400f);
                drawKeyboardKey(canvas, left, 440f, left + 80f, 520f);
            }
            drawKeyboardKey(canvas, 320f, 560f, 640f, 640f);
            canvas.restoreToCount(saveCount);
        }

        private void drawKeyboardKey(Canvas canvas, float left, float top,
                float right, float bottom) {
            canvas.drawRect(left, top, right, bottom, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
            invalidateSelf();
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
            invalidateSelf();
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }

        @Override
        public int getIntrinsicWidth() {
            return dp(24);
        }

        @Override
        public int getIntrinsicHeight() {
            return dp(24);
        }
    }

    private static final class RichContentSinkEditText extends EditText {
        RichContentSinkEditText(Context context) {
            super(context);
        }

        @Override
        public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
            InputConnection connection = null;
            try {
                connection = super.onCreateInputConnection(outAttrs);
                if (connection == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
                    return connection;
                }
                outAttrs.contentMimeTypes = RICH_CONTENT_MIME_TYPES.clone();
                return buildRichContentSink(connection);
            } catch (Throwable throwable) {
                try {
                    Log.w(TAG, "Failed to create keyboard preview input connection", throwable);
                } catch (Throwable ignored) {
                    // Preserve stock editor behavior when rich-content setup fails.
                }
                return connection;
            }
        }

        @TargetApi(Build.VERSION_CODES.N_MR1)
        private InputConnection buildRichContentSink(InputConnection connection) {
            return new InputConnectionWrapper(connection, false) {
                @Override
                public boolean commitContent(InputContentInfo inputContentInfo,
                        int flags, Bundle opts) {
                    return true;
                }
            };
        }
    }
}
