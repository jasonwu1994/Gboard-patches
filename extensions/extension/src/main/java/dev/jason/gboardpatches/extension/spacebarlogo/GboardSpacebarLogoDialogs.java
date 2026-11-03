package dev.jason.gboardpatches.extension.spacebarlogo;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

final class GboardSpacebarLogoDialogs {
    private static final String TAG = "GboardPatches";
    interface SelectionConsumer {
        void accept(String value);
    }

    private static final int ICON_TILE_HEIGHT_DP = 120;
    private static final int COLOR_TILE_HEIGHT_DP = 136;

    private GboardSpacebarLogoDialogs() {
    }

    static GboardPatchesSettingsContract.ManagedDialogAction iconDialog(
            Context context, String title, String[] labels, String[] values, Drawable[] previews,
            String currentValue, SelectionConsumer consumer) {
        return onDismiss -> {
            GridLayout grid = new GridLayout(context);
            grid.setColumnCount(2);
            grid.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
            AlertDialog[] holder = new AlertDialog[1];
            for (int index = 0; index < values.length; index++) {
                final String value = values[index];
                boolean preserveColor = GboardSpacebarLogoContract.ICON_GOOGLE_G_COLOR.equals(value)
                        || GboardSpacebarLogoContract.ICON_CUSTOM_IMAGE.equals(value);
                LinearLayout tile = iconTile(context, labels[index], previews[index],
                        value.equals(currentValue), preserveColor);
                tile.setOnClickListener(view -> {
                    runSafely("handle icon selection", () -> {
                        if (holder[0] != null) holder[0].dismiss();
                        new Handler(Looper.getMainLooper()).post(() ->
                                runSafely("apply icon selection", () -> consumer.accept(value)));
                    });
                });
                grid.addView(tile, gridParams(context, ICON_TILE_HEIGHT_DP));
            }
            ScrollView scroll = new ScrollView(context);
            scroll.addView(grid);
            AlertDialog dialog = new AlertDialog.Builder(context).setTitle(title)
                    .setView(scroll)
                    .setNegativeButton(text(context, R.string.gboard_patches_dialog_cancel), null)
                    .create();
            holder[0] = dialog;
            bindDismiss(dialog, onDismiss);
            dialog.show();
            return true;
        };
    }

    static GboardPatchesSettingsContract.ManagedDialogAction paletteDialog(
            Context context, String title, String[] labels, String[] summaries, String[] values,
            String currentSelection, String customValue, Runnable customAction,
            SelectionConsumer consumer) {
        return onDismiss -> {
            GridLayout grid = new GridLayout(context);
            grid.setColumnCount(3);
            grid.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
            AlertDialog[] holder = new AlertDialog[1];
            for (int index = 0; index < values.length; index++) {
                final String value = values[index];
                LinearLayout tile = colorTile(context, labels[index], summaries[index], value,
                        customValue.equals(value), value.equals(currentSelection));
                tile.setOnClickListener(view -> {
                    runSafely("handle color selection", () -> {
                        if (holder[0] != null) holder[0].dismiss();
                        if (customValue.equals(value)) {
                            new Handler(Looper.getMainLooper()).post(
                                    () -> runSafely("open custom color picker", customAction));
                        } else {
                            consumer.accept(value);
                        }
                    });
                });
                grid.addView(tile, gridParams(context, COLOR_TILE_HEIGHT_DP));
            }
            ScrollView scroll = new ScrollView(context);
            scroll.addView(grid);
            AlertDialog dialog = new AlertDialog.Builder(context).setTitle(title)
                    .setView(scroll)
                    .setNegativeButton(text(context, R.string.gboard_patches_dialog_cancel), null)
                    .create();
            holder[0] = dialog;
            bindDismiss(dialog, onDismiss);
            dialog.show();
            return true;
        };
    }

    static GboardPatchesSettingsContract.ManagedDialogAction colorPickerDialog(
            Context context, String title, String opacityLabel, String codeLabel,
            String codeHint, String invalidMessage, String initialValue,
            SelectionConsumer consumer) {
        return onDismiss -> {
            int initialColor = parseColor(initialValue);
            int[] alpha = {Color.alpha(initialColor)};
            boolean[] syncing = {false};
            LinearLayout content = new LinearLayout(context);
            content.setOrientation(LinearLayout.VERTICAL);
            content.setPadding(dp(context, 24), dp(context, 8), dp(context, 24), 0);

            GboardSpacebarLogoColorPickerView picker =
                    new GboardSpacebarLogoColorPickerView(context);
            picker.setColor(initialColor);
            content.addView(picker, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 248)));

            TextView opacity = label(context, opacityLabel);
            LinearLayout.LayoutParams opacityParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            opacityParams.topMargin = dp(context, 14);
            content.addView(opacity, opacityParams);
            SeekBar alphaBar = new SeekBar(context);
            alphaBar.setMax(255);
            alphaBar.setProgress(alpha[0]);
            alphaBar.setMinimumHeight(dp(context, 48));
            content.addView(alphaBar);

            TextView code = label(context, codeLabel);
            content.addView(code);
            EditText input = new EditText(context);
            input.setSingleLine(true);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
            input.setHint(codeHint);
            input.setFilters(new InputFilter[] {new InputFilter.LengthFilter(9)});
            input.setText(formatColor(initialColor));
            input.setSelectAllOnFocus(true);
            input.setMinimumHeight(dp(context, 48));
            content.addView(input);

            Runnable syncInput = () -> {
                runSafely("synchronize custom color input", () -> {
                    if (syncing[0]) return;
                    syncing[0] = true;
                    try {
                        int rgb = picker.getColor();
                        input.setText(formatColor(Color.argb(alpha[0], Color.red(rgb),
                                Color.green(rgb), Color.blue(rgb))));
                        input.setSelection(input.length());
                    } finally {
                        syncing[0] = false;
                    }
                });
            };
            picker.setListener(ignored -> syncInput.run());
            alphaBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar seekBar, int progress,
                        boolean fromUser) { alpha[0] = progress; syncInput.run(); }
                @Override public void onStartTrackingTouch(SeekBar seekBar) { }
                @Override public void onStopTrackingTouch(SeekBar seekBar) { }
            });
            input.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count,
                        int after) { }
                @Override public void onTextChanged(CharSequence s, int start, int before,
                        int count) {
                    runSafely("apply typed custom color", () -> {
                        if (syncing[0]) return;
                        Integer parsed = tryParseColor(s == null ? null : s.toString());
                        if (parsed == null) return;
                        syncing[0] = true;
                        try {
                            alpha[0] = Color.alpha(parsed.intValue());
                            alphaBar.setProgress(alpha[0]);
                            picker.setColor(parsed.intValue());
                        } finally {
                            syncing[0] = false;
                        }
                    });
                }
                @Override public void afterTextChanged(Editable s) { }
            });

            AlertDialog dialog = new AlertDialog.Builder(context).setTitle(title)
                    .setView(content)
                    .setPositiveButton(text(context, R.string.gboard_patches_dialog_save), null)
                    .setNegativeButton(text(context, R.string.gboard_patches_dialog_cancel), null)
                    .create();
            dialog.setOnShowListener(ignored ->
                    runSafely("initialize custom color dialog", () ->
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(view -> {
                        runSafely("save custom color", () -> {
                            String normalized = GboardSpacebarLogoSettings.tryNormalizeColorHex(
                                    input.getText().toString());
                            if (normalized == null) {
                                input.setError(invalidMessage);
                                return;
                            }
                            consumer.accept(normalized);
                            dialog.dismiss();
                        });
                    })));
            bindDismiss(dialog, onDismiss);
            dialog.show();
            return true;
        };
    }

    private static LinearLayout iconTile(Context context, String label, Drawable drawable,
            boolean selected, boolean preserveColor) {
        LinearLayout tile = baseTile(context, selected, ICON_TILE_HEIGHT_DP);
        tile.setGravity(Gravity.CENTER);
        tile.setContentDescription(label);
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        try {
            if (drawable != null) {
                drawable = drawable.mutate();
                if (!preserveColor) drawable.setTint(palette(context).primary);
                image.setImageDrawable(drawable);
            }
        } catch (Throwable ignored) { }
        tile.addView(image, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)));
        TextView title = label(context, label);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(context, 8);
        tile.addView(title, params);
        return tile;
    }

    private static LinearLayout colorTile(Context context, String label, String summary,
            String value, boolean custom, boolean selected) {
        LinearLayout tile = baseTile(context, selected, COLOR_TILE_HEIGHT_DP);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setContentDescription(label + (summary == null || summary.isEmpty()
                ? "" : ", " + summary));
        TextView swatch = new TextView(context);
        swatch.setGravity(Gravity.CENTER);
        swatch.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        if (custom && (summary == null || summary.isEmpty())) {
            swatch.setText("+");
            swatch.setBackground(swatch(context, null, selected));
        } else if (GboardSpacebarLogoSettings.COLOR_VALUE_AUTO.equals(value)) {
            swatch.setText("A");
            swatch.setBackground(swatch(context, null, selected));
        } else {
            swatch.setBackground(swatch(context, custom ? summary : value, selected));
        }
        tile.addView(swatch, new LinearLayout.LayoutParams(dp(context, 52), dp(context, 52)));
        TextView title = label(context, label);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(context, 8);
        tile.addView(title, titleParams);
        TextView sub = label(context, summary == null ? "" : summary);
        sub.setGravity(Gravity.CENTER);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        sub.setTextColor(palette(context).secondary);
        sub.setMinLines(2);
        sub.setMaxLines(2);
        tile.addView(sub);
        return tile;
    }

    private static LinearLayout baseTile(Context context, boolean selected, int heightDp) {
        LinearLayout tile = new LinearLayout(context);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10));
        tile.setMinimumHeight(dp(context, heightDp));
        tile.setClickable(true);
        tile.setFocusable(true);
        tile.setSelected(selected);
        tile.setBackground(tileBackground(context, selected));
        return tile;
    }

    private static GridLayout.LayoutParams gridParams(Context context, int heightDp) {
        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = dp(context, heightDp);
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        params.setMargins(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4));
        return params;
    }

    private static TextView label(Context context, String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        view.setTextColor(palette(context).primary);
        return view;
    }

    private static Drawable tileBackground(Context context, boolean selected) {
        Palette palette = palette(context);
        GradientDrawable content = new GradientDrawable();
        content.setColor(selected ? palette.selected : palette.surface);
        content.setCornerRadius(dp(context, 16));
        content.setStroke(dp(context, selected ? 2 : 1),
                selected ? palette.accent : palette.stroke);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(context, 16));
        return new RippleDrawable(
                ColorStateList.valueOf(palette.pressed), content, mask);
    }

    private static Drawable swatch(Context context, String value, boolean selected) {
        Palette palette = palette(context);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(value == null ? palette.surfaceAlt : parseColor(value));
        drawable.setStroke(dp(context, selected ? 3 : 1),
                selected ? palette.accent : palette.stroke);
        return drawable;
    }

    private static void bindDismiss(AlertDialog dialog, Runnable callback) {
        dialog.setOnDismissListener(ignored ->
                runSafely("dismiss spacebar logo dialog", callback));
    }

    private static void runSafely(String operation, Runnable action) {
        if (action == null) return;
        try {
            action.run();
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to " + operation, throwable);
            } catch (Throwable ignored) {
                // Dialog callbacks must never propagate into the settings host.
            }
        }
    }

    private static String text(Context context, int resId) {
        return GboardSettingsText.get(context, resId);
    }

    private static int parseColor(String value) {
        Integer parsed = tryParseColor(value);
        return parsed == null ? Color.parseColor("#4285F4") : parsed.intValue();
    }

    private static Integer tryParseColor(String value) {
        String normalized = GboardSpacebarLogoSettings.tryNormalizeColorHex(value);
        if (normalized == null) return null;
        try {
            return Color.parseColor(normalized);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String formatColor(int color) {
        return Color.alpha(color) == 255
                ? String.format(Locale.ROOT, "#%02X%02X%02X", Color.red(color),
                        Color.green(color), Color.blue(color))
                : String.format(Locale.ROOT, "#%08X", color);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static Palette palette(Context context) {
        boolean dark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return dark
                ? new Palette(Color.parseColor("#FFF3F5F8"), Color.parseColor("#FFAAB3C0"),
                        Color.parseColor("#FF121821"), Color.parseColor("#FF161B22"),
                        Color.parseColor("#1FFFFFFF"), Color.parseColor("#172554"),
                        Color.parseColor("#FF8AB4F8"), Color.parseColor("#1F8AB4F8"))
                : new Palette(Color.parseColor("#FF101828"), Color.parseColor("#FF5F6B7A"),
                        Color.parseColor("#EEF3FB"), Color.WHITE, Color.parseColor("#140F172A"),
                        Color.parseColor("#E8F0FE"), Color.parseColor("#FF1A73E8"),
                        Color.parseColor("#141A73E8"));
    }

    private static final class Palette {
        final int primary;
        final int secondary;
        final int surfaceAlt;
        final int surface;
        final int stroke;
        final int selected;
        final int accent;
        final int pressed;

        Palette(int primary, int secondary, int surfaceAlt, int surface, int stroke,
                int selected, int accent, int pressed) {
            this.primary = primary;
            this.secondary = secondary;
            this.surfaceAlt = surfaceAlt;
            this.surface = surface;
            this.stroke = stroke;
            this.selected = selected;
            this.accent = accent;
            this.pressed = pressed;
        }
    }
}
