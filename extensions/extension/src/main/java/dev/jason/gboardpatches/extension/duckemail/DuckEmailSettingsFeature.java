package dev.jason.gboardpatches.extension.duckemail;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;
import static dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract.*;

public final class DuckEmailSettingsFeature implements Feature {
    private final Context context;
    private volatile boolean busy;
    private long visibleEpoch;
    public DuckEmailSettingsFeature(Context context) { this.context = context; }
    @Override public String getEntryTitle() { return text(R.string.gboard_duck_email_title); }
    @Override public String getEntrySummary() { return text(R.string.gboard_duck_email_summary); }
    @Override public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(context,
                GboardPatchesFeatureAvailability.FEATURE_DUCK_EMAIL);
    }
    @Override public void onHidden(FeatureHost host) { visibleEpoch++; }

    @Override public Screen buildScreen(FeatureHost host) {
        DuckEmailStore store = new DuckEmailStore(host.getContext());
        List<Row> rows = new ArrayList<>();
        rows.add(new ToggleRow(text(R.string.gboard_duck_email_enable),
                text(R.string.gboard_duck_email_restart), !busy, store.enabled(), store::enabled));
        rows.add(new ToggleRow(text(R.string.gboard_duck_email_copy_generated),
                text(R.string.gboard_duck_email_copy_generated_summary), !busy,
                store.copyGeneratedEmail(), store::copyGeneratedEmail));
        rows.add(new ActionRow(text(R.string.gboard_duck_email_send_code),
                text(R.string.gboard_duck_email_address_hint), !busy,
                () -> showInput(host, text(R.string.gboard_duck_email_send_code),
                        text(R.string.gboard_duck_email_address_hint),
                        store.pending().isEmpty() ? store.username() : store.pending(), true, value -> {
                            final String user;
                            try { user = DuckEmailApi.username(value); }
                            catch (IllegalArgumentException ignored) {
                                showMessage(host, text(R.string.gboard_duck_email_address_hint));
                                return;
                            }
                            String revision = store.revision();
                            run(host, () -> {
                                new DuckEmailApi().sendCode(user);
                                if (!store.pending(user, revision)) throw new IllegalStateException();
                            }, () -> {
                                showMessage(host, text(R.string.gboard_duck_email_code_sent));
                                enterCode(host, store);
                            });
                        })));
        rows.add(new ActionRow(text(R.string.gboard_duck_email_enter_code),
                store.pending().isEmpty() ? text(R.string.gboard_duck_email_code_hint)
                        : store.pending() + "@duck.com", !busy && !store.pending().isEmpty(),
                () -> enterCode(host, store)));
        if (store.signedIn() || !store.pending().isEmpty()) {
            rows.add(new ActionRow(text(R.string.gboard_duck_email_sign_out),
                    text(R.string.gboard_duck_email_sign_out_summary), !busy, () -> {
                        store.signOut();
                        refresh(host);
                    }));
        }
        if (!store.lastAddress().isEmpty()) {
            rows.add(new ActionRow(text(R.string.gboard_duck_email_last_address),
                    store.lastAddress(), !busy, () -> {
                        ClipboardManager clipboard = host.getContext()
                                .getSystemService(ClipboardManager.class);
                        if (clipboard != null) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("Duck email", store.lastAddress()));
                            showMessage(host, text(R.string.gboard_duck_email_copied));
                        }
                    }));
        }
        String status = busy ? text(R.string.gboard_duck_email_working)
                : store.signedIn() ? store.username() + "@duck.com"
                : text(R.string.gboard_duck_email_signed_out);
        return new Screen(getEntryTitle(), text(R.string.gboard_patches_header_badge),
                getEntryTitle(), getEntrySummary(),
                List.of(new StatusBlock(text(R.string.gboard_duck_email_account), status,
                        StatusTone.INFO)),
                List.of(new Section(text(R.string.gboard_duck_email_account), rows)));
    }

    private void enterCode(FeatureHost host, DuckEmailStore store) {
        String user = store.pending();
        if (user.isEmpty() || busy) return;
        showInput(host, text(R.string.gboard_duck_email_enter_code),
                text(R.string.gboard_duck_email_code_hint), "", false, value -> {
                    final String code;
                    try { code = DuckEmailApi.passphrase(value); }
                    catch (IllegalArgumentException ignored) {
                        showMessage(host, text(R.string.gboard_duck_email_code_hint));
                        return;
                    }
                    String revision = store.revision();
                    run(host, () -> {
                        DuckEmailApi.Account account = new DuckEmailApi().signIn(user, code);
                        if (!store.save(account, revision)) throw new IllegalStateException();
                    }, () -> showMessage(host, text(R.string.gboard_duck_email_signed_in)));
                });
    }

    private void showInput(FeatureHost host, String title, String hint, String initial,
            boolean email, TextValueConsumer consumer) {
        showManagedDialog(host, onDismiss -> {
            Context owner = host.getContext();
            EditText input = new EditText(owner);
            input.setInputType(InputType.TYPE_CLASS_TEXT | (email
                    ? InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    : InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
            if (Build.VERSION.SDK_INT >= 26) {
                input.setImeOptions(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
                input.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            }
            input.setHint(hint);
            input.setText(initial);
            input.setSingleLine(true);
            int padding = Math.round(24 * owner.getResources().getDisplayMetrics().density);
            LinearLayout container = new LinearLayout(owner);
            container.setPadding(padding, padding / 2, padding, 0);
            container.addView(input, new LinearLayout.LayoutParams(-1, -2));
            Runnable[] pendingAction = {null};
            AlertDialog dialog = new AlertDialog.Builder(owner).setTitle(title).setView(container)
                    .setPositiveButton(text(email ? R.string.gboard_duck_email_send_code_button
                            : R.string.gboard_duck_email_sign_in_button), null)
                    .setNegativeButton(text(R.string.gboard_patches_dialog_cancel), null).create();
            dialog.setOnDismissListener(ignored -> {
                input.setText("");
                onDismiss.run();
                if (pendingAction[0] != null) new Handler(Looper.getMainLooper()).post(pendingAction[0]);
            });
            dialog.show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
                final String value;
                try {
                    value = email ? DuckEmailApi.username(input.getText().toString())
                            : DuckEmailApi.passphrase(input.getText().toString());
                } catch (IllegalArgumentException ignored) {
                    input.setError(hint);
                    return;
                }
                pendingAction[0] = () -> consumer.accept(value);
                dialog.dismiss();
            });
            input.requestFocus();
            if (dialog.getWindow() != null) dialog.getWindow().setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            return true;
        });
    }

    interface Work { void run() throws Exception; }
    private void run(FeatureHost host, Work work, Runnable success) {
        if (busy) return;
        busy = true;
        long epoch = visibleEpoch;
        refresh(host);
        Handler main = new Handler(Looper.getMainLooper());
        DuckEmailTasks.NETWORK.execute(() -> {
            Exception failure = null;
            try { work.run(); } catch (Exception exception) { failure = exception; }
            Exception result = failure;
            main.post(() -> {
                busy = false;
                if (epoch != visibleEpoch) return;
                refresh(host);
                if (result == null) success.run();
                else showMessage(host, DuckEmailTasks.error(context, result));
            });
        });
    }
    private String text(int id) { return GboardSettingsText.get(context, id); }
}
