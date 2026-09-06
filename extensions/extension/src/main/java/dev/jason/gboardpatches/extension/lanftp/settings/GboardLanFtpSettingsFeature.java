package dev.jason.gboardpatches.extension.lanftp.settings;

import android.content.Context;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.lanftp.android.LanFtpServerController;
import dev.jason.gboardpatches.extension.lanftp.android.LanFtpServerConfigSnapshot;
import dev.jason.gboardpatches.extension.lanftp.android.LanFtpServerState;
import dev.jason.gboardpatches.extension.lanftp.android.LanFtpRuntimeObserver;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpPortPolicy;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpCredentialPolicy;
import dev.jason.gboardpatches.extension.lanftp.runtime.LanFtpSessionInfo;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/** Root-level management UI for the module-owned LAN FTP service. */
public final class GboardLanFtpSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardLanFtp";
    private static final String ROOT_PICKER_VALUE = "choose-folder";

    private final Context textContext;
    private final Object runtimeObserverLock = new Object();
    private LanFtpRuntimeObserver runtimeObserver;

    public GboardLanFtpSettingsFeature(Context context) {
        textContext = context;
    }

    public static GboardLanFtpSettingsFeature forTests() {
        return new GboardLanFtpSettingsFeature(null);
    }

    @Override
    public String getEntryTitle() {
        return text(R.string.gboard_patches_lan_ftp_title, "FTP Server");
    }

    @Override
    public String getEntrySummary() {
        return text(R.string.gboard_patches_lan_ftp_summary,
                "Transfer files with an FTP client on the local network");
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_LAN_FTP);
    }

    @Override
    public void onVisible(GboardPatchesSettingsContract.FeatureHost host) {
        synchronized (runtimeObserverLock) {
            closeRuntimeObserverLocked();
            if (host == null || host.getContext() == null) {
                return;
            }
            runtimeObserver = new LanFtpRuntimeObserver(
                    host.getContext(),
                    () -> GboardPatchesSettingsContract.refresh(host));
            runtimeObserver.start();
        }
    }

    @Override
    public void onHidden(GboardPatchesSettingsContract.FeatureHost host) {
        synchronized (runtimeObserverLock) {
            closeRuntimeObserverLocked();
        }
    }

    private void closeRuntimeObserverLocked() {
        if (runtimeObserver == null) {
            return;
        }
        runtimeObserver.close();
        runtimeObserver = null;
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            if (host == null || host.getContext() == null) {
                return errorScreen();
            }
            Context context = host.getContext();
            LanFtpServerController controller = LanFtpServerController.from(context);
            LanFtpServerState state = controller.requestState();
            LanFtpServerConfigSnapshot settings = state.config();
            return buildScreenForState(host, settings, state, new Callbacks() {
                @Override public boolean setEnabled(boolean value) {
                    return controller.applyConfig(settings.withEnabled(value));
                }
                @Override public boolean setControlPort(int value) {
                    return controller.applyConfig(settings.withControlPort(value));
                }
                @Override public boolean setPassivePortRange(int start, int end) {
                    return controller.applyConfig(settings.withPassivePortRange(start, end));
                }
                @Override public boolean setRootTreeUri(String value) {
                    return controller.applyConfig(settings.withRootTreeUri(value));
                }
                @Override public boolean setReadOnly(boolean value) {
                    return controller.applyConfig(settings.withReadOnly(value));
                }
                @Override public boolean setAllowAnonymous(boolean value) {
                    return controller.applyConfig(settings.withAllowAnonymous(value));
                }
                @Override public boolean setPassword(String value) {
                    return controller.setPassword(value);
                }
                @Override public String regeneratePassword() {
                    return controller.regeneratePassword();
                }
                @Override public boolean kickSession(String sessionId) {
                    return controller.kick(sessionId);
                }
            });
        } catch (Throwable throwable) {
            logWarning("Failed to render LAN FTP settings", throwable);
            return errorScreen();
        }
    }

    GboardPatchesSettingsContract.Screen buildScreenForState(
            GboardPatchesSettingsContract.FeatureHost host,
            LanFtpServerConfigSnapshot settings,
            LanFtpServerState runtime,
            Callbacks callbacks) {
        if (host == null || settings == null || runtime == null || callbacks == null) {
            return errorScreen();
        }
        boolean sharedStorageRoot = settings.isSharedStorageRoot();
        boolean controlsEnabled = settings.enabled();
        String rootDisplay = settings.rootTreeUri().isEmpty()
                ? text(R.string.gboard_patches_lan_ftp_root_unselected, "Not selected")
                : sharedStorageRoot ? "/sdcard" : friendlyRootName(settings.rootTreeUri());
        String endpoint = runtime.endpoint().isEmpty()
                ? "FTP :" + settings.controlPort()
                : runtime.endpoint();
        String credentials = settings.allowAnonymous()
                ? "\nusername: " + LanFtpCredentialPolicy.ANONYMOUS_USERNAME
                        + "\npassword: not required"
                : "\nusername: " + settings.username()
                        + "\npassword: " + settings.password();
        String connectionDetails = endpoint
                + credentials
                + "\npassive ports: " + settings.passivePortStart()
                + "-" + settings.passivePortEnd();

        GboardPatchesSettingsContract.SelectorRow rootFolderRow =
                new GboardPatchesSettingsContract.SelectorRow(
                        text(R.string.gboard_patches_lan_ftp_root_title,
                                "Root folder"),
                        text(R.string.gboard_patches_lan_ftp_root_summary,
                                "Only this folder and its contents are shared."),
                        rootDisplay,
                        controlsEnabled,
                        () -> safelyRun(() -> showRootChoice(
                                host, callbacks, settings.rootTreeUri())));
        GboardPatchesSettingsContract.Section serverSection =
                new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_lan_ftp_section_server, "Server"),
                        Arrays.asList(
                                new GboardPatchesSettingsContract.ToggleRow(
                                        text(R.string.gboard_patches_lan_ftp_enabled_title,
                                                "Enabled"),
                                        settings.rootTreeUri().isEmpty()
                                                ? text(R.string.gboard_patches_lan_ftp_enabled_summary,
                                                        "Select a root folder before starting the server.")
                                                : "",
                                        !settings.rootTreeUri().isEmpty(),
                                        settings.enabled(),
                                        value -> applyEnabledWithPermission(
                                                host, callbacks, value, sharedStorageRoot,
                                                runtime.localNetworkAccess(),
                                                runtime.allFilesAccess())),
                                new GboardPatchesSettingsContract.CommandRow(
                                        text(R.string.gboard_patches_lan_ftp_screen_off_title,
                                                "Keep connection when screen is off"),
                                        text(R.string.gboard_patches_lan_ftp_screen_off_summary,
                                                "If FTP disconnects after the screen turns off, set this app's battery policy to No restrictions."),
                                        controlsEnabled,
                                        () -> safelyRun(() ->
                                                GboardPatchesSettingsContract
                                                        .openBatteryOptimizationSettings(
                                                                host,
                                                                text(R.string.gboard_patches_lan_ftp_battery_settings_unavailable,
                                                                        "Battery optimization settings are unavailable.")))),
                                new GboardPatchesSettingsContract.SelectorRow(
                                        text(R.string.gboard_patches_lan_ftp_port_title,
                                                "Control port"),
                                        text(R.string.gboard_patches_lan_ftp_port_summary,
                                                "FTP clients connect to this port."),
                                        Integer.toString(settings.controlPort()),
                                        true,
                                        () -> safelyRun(() ->
                                                GboardPatchesSettingsContract
                                                        .showPositiveIntegerDialog(host,
                                                text(R.string.gboard_patches_lan_ftp_port_title,
                                                        "Control port"),
                                                "1024–65535",
                                                settings.controlPort(),
                                                value -> safelyRefresh(host,
                                                        () -> callbacks.setControlPort(value))))),
                                new GboardPatchesSettingsContract.SelectorRow(
                                        text(R.string.gboard_patches_lan_ftp_passive_range_title,
                                                "Passive port range"),
                                        text(R.string.gboard_patches_lan_ftp_passive_summary,
                                                "Each directory listing or file transfer uses one available data port. The default 10 ports already exceed this server's concurrency limits; a larger range does not increase transfer speed."),
                                        settings.passivePortStart() + "-" + settings.passivePortEnd(),
                                        controlsEnabled,
                                        () -> safelyRun(() ->
                                                GboardPatchesSettingsContract.showTextInputDialog(
                                                        host,
                                                text(R.string.gboard_patches_lan_ftp_passive_range_title,
                                                        "Passive port range"),
                                                "50000-50009 (maximum 32; larger ranges do not improve speed)",
                                                settings.passivePortStart() + "-"
                                                        + settings.passivePortEnd(),
                                                value -> applyPassiveRange(
                                                        host,
                                                        callbacks,
                                                        settings.controlPort(),
                                                        value)))),
                                rootFolderRow,
                                new GboardPatchesSettingsContract.ToggleRow(
                                        text(R.string.gboard_patches_lan_ftp_read_only_title,
                                                "Read-only access"),
                                        text(R.string.gboard_patches_lan_ftp_read_only_summary,
                                                "Prevent FTP clients from changing files."),
                                        controlsEnabled,
                                        settings.readOnly(),
                                        value -> safelyRefresh(host,
                                                () -> callbacks.setReadOnly(value)))));

        if (sharedStorageRoot) {
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>(
                    serverSection.getItems());
            boolean allFilesGranted = runtime.allFilesAccess();
            rows.add(rows.indexOf(rootFolderRow) + 1,
                    new GboardPatchesSettingsContract.CommandRow(
                            text(R.string.gboard_patches_lan_ftp_all_files_title,
                                    "All files access"),
                            allFilesGranted
                                    ? text(R.string.gboard_patches_lan_ftp_all_files_granted,
                                            "Granted for /sdcard")
                                    : text(R.string.gboard_patches_lan_ftp_all_files_required,
                                            "Required before the /sdcard server can start"),
                            controlsEnabled,
                            () -> safelyRun(() -> GboardPatchesSettingsContract
                                    .openAllFilesAccessSettings(
                                            host,
                                            text(R.string.gboard_patches_lan_ftp_all_files_settings_unavailable,
                                                    "All files access settings are unavailable.")))));
            serverSection = new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_lan_ftp_section_server, "Server"), rows);
        }

        GboardPatchesSettingsContract.Section accountSection =
                new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_lan_ftp_section_account, "Account"),
                        Arrays.asList(
                                new GboardPatchesSettingsContract.ToggleRow(
                                        text(R.string.gboard_patches_lan_ftp_anonymous_title,
                                                "Allow anonymous login"),
                                        text(R.string.gboard_patches_lan_ftp_anonymous_summary,
                                                "Clients can sign in as anonymous without a password."),
                                        controlsEnabled,
                                        settings.allowAnonymous(),
                                        value -> safelyRefresh(host,
                                                () -> callbacks.setAllowAnonymous(value))),
                                new GboardPatchesSettingsContract.DetailRow(
                                        text(R.string.gboard_patches_lan_ftp_username_title,
                                                "Username"),
                                        settings.username(),
                                        controlsEnabled,
                                        true),
                                new GboardPatchesSettingsContract.SelectorRow(
                                        text(R.string.gboard_patches_lan_ftp_password_title,
                                                "Password"),
                                        text(R.string.gboard_patches_lan_ftp_password_summary,
                                                "Tap to set exactly six lowercase letters."),
                                        settings.password(),
                                        controlsEnabled,
                                        () -> safelyRun(() ->
                                                GboardPatchesSettingsContract.showTextInputDialog(
                                                        host,
                                                text(R.string.gboard_patches_lan_ftp_password_title,
                                                        "Password"),
                                                text(R.string.gboard_patches_lan_ftp_password_hint,
                                                        "Six lowercase letters"),
                                                settings.password(),
                                                value -> applyPassword(
                                                        host, callbacks, value)))),
                                new GboardPatchesSettingsContract.CommandRow(
                                        text(R.string.gboard_patches_lan_ftp_regenerate_title,
                                                "Regenerate password"),
                                        text(R.string.gboard_patches_lan_ftp_regenerate_summary,
                                                "Disconnects existing sessions when the server reloads."),
                                        controlsEnabled,
                                        () -> safelyRefresh(host,
                                                callbacks::regeneratePassword))));

        List<GboardPatchesSettingsContract.Row> statusRows = new ArrayList<>(Arrays.asList(
                new GboardPatchesSettingsContract.DetailRow(
                        text(R.string.gboard_patches_lan_ftp_runtime_title,
                                "Runtime status"),
                        runtime.status(),
                        controlsEnabled),
                new GboardPatchesSettingsContract.DetailRow(
                        text(R.string.gboard_patches_lan_ftp_endpoint_title,
                                "Endpoint"),
                        endpoint,
                        controlsEnabled,
                        true),
                new GboardPatchesSettingsContract.CommandRow(
                        text(R.string.gboard_patches_lan_ftp_copy_connection_title,
                                "Copy connection details"),
                        connectionDetails,
                        controlsEnabled,
                        () -> copyConnectionDetails(host.getContext(), connectionDetails)),
                new GboardPatchesSettingsContract.DetailRow(
                        text(R.string.gboard_patches_lan_ftp_clients_title,
                                "Connected clients"),
                        Integer.toString(runtime.clientCount()),
                        controlsEnabled)));
        runtime.clients().forEach(client -> statusRows.add(
                new GboardPatchesSettingsContract.CommandRow(
                        text(R.string.gboard_patches_lan_ftp_disconnect_client_title,
                                "Disconnect client") + " " + client.peer(),
                        clientSummary(client),
                        controlsEnabled,
                        () -> safelyRefresh(host,
                                () -> callbacks.kickSession(client.id())))));

        GboardPatchesSettingsContract.Section statusSection =
                new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_lan_ftp_section_status, "Status"),
                        statusRows);

        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(),
                text(R.string.gboard_patches_header_badge, "Gboard"),
                getEntryTitle(),
                getEntrySummary(),
                Collections.emptyList(),
                Arrays.asList(statusSection, serverSection, accountSection),
                GboardPatchesSettingsContract.RefreshPolicy.none(),
                GboardPatchesSettingsContract.PanelStyle.FLAT);
    }

    private String clientSummary(LanFtpSessionInfo client) {
        StringBuilder summary = new StringBuilder(client.id())
                .append('\n')
                .append(text(R.string.gboard_patches_lan_ftp_client_login_time_label,
                        "Login time"))
                .append(": ")
                .append(LanFtpSettingsDisplayFormatter.formatTimestamp(
                        client.loginTimeMillis()))
                .append('\n')
                .append(text(R.string.gboard_patches_lan_ftp_client_last_activity_label,
                        "Last activity"))
                .append(": ")
                .append(LanFtpSettingsDisplayFormatter.formatTimestamp(
                        client.lastActivityMillis()))
                .append('\n')
                .append(transferStatusLabel(client));
        if (!client.path().isEmpty()) {
            summary.append(' ').append(client.fileName());
        }
        summary.append(" • ").append(
                LanFtpSettingsDisplayFormatter.formatBytes(client.bytesTransferred()));
        if (client.totalBytes() >= 0L) {
            summary.append(" / ")
                    .append(LanFtpSettingsDisplayFormatter.formatBytes(client.totalBytes()))
                    .append(" (")
                    .append(client.progressPercent())
                    .append("%)");
        }
        if (client.isTransferActive()) {
            summary.append(" • ").append(
                    LanFtpSettingsDisplayFormatter.formatRate(client.bytesPerSecond()));
            if (client.totalBytes() < 0L) {
                summary.append(" • ").append(text(
                        R.string.gboard_patches_lan_ftp_transfer_total_unknown,
                        "Total size unknown"));
            }
        }
        return summary.toString();
    }

    private String transferStatusLabel(LanFtpSessionInfo client) {
        if ("upload".equals(client.direction())) {
            return text(R.string.gboard_patches_lan_ftp_transfer_upload_label,
                    "Uploading to phone");
        }
        if ("download".equals(client.direction())) {
            return text(R.string.gboard_patches_lan_ftp_transfer_download_label,
                    "Downloading from phone");
        }
        return text(R.string.gboard_patches_lan_ftp_transfer_idle_label, "Idle");
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return new GboardPatchesSettingsContract.Screen(
                getEntryTitle(),
                text(R.string.gboard_patches_header_badge, "Gboard"),
                getEntryTitle(),
                getEntrySummary(),
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_lan_ftp_error_title,
                                "FTP settings unavailable"),
                        text(R.string.gboard_patches_lan_ftp_error_summary,
                                "The server remains stopped because its settings could not be loaded safely."),
                        GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private String text(int resourceId, String fallback) {
        try {
            return GboardSettingsText.get(textContext, resourceId);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String text(Context context, int resourceId, String fallback) {
        try {
            return GboardSettingsText.get(context, resourceId);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static void safelyRefresh(GboardPatchesSettingsContract.FeatureHost host,
            UnsafeAction action) {
        try {
            if (action != null) {
                action.run();
            }
        } catch (Throwable throwable) {
            logWarning("LAN FTP settings action failed", throwable);
        }
        try {
            if (host != null) {
                GboardPatchesSettingsContract.refresh(host);
            }
        } catch (Throwable throwable) {
            logWarning("LAN FTP settings refresh failed", throwable);
        }
    }

    private static void safelyRun(UnsafeAction action) {
        try {
            if (action != null) {
                action.run();
            }
        } catch (Throwable throwable) {
            logWarning("LAN FTP settings callback failed", throwable);
        }
    }

    private static void logWarning(String message, Throwable throwable) {
        try {
            Log.w(TAG, message, throwable);
        } catch (Throwable ignored) {
            // Logging must not cross the host callback boundary either.
        }
    }

    private static String friendlyRootName(String treeUri) {
        try {
            String documentId = DocumentsContract.getTreeDocumentId(Uri.parse(treeUri));
            int separator = Math.max(documentId.lastIndexOf(':'), documentId.lastIndexOf('/'));
            String name = separator >= 0 ? documentId.substring(separator + 1) : documentId;
            return name.isBlank() ? "Selected folder" : name;
        } catch (Throwable ignored) {
            return "Selected folder";
        }
    }

    private static void copyConnectionDetails(Context context, String details) {
        if (context == null || details == null) {
            return;
        }
        try {
            ClipboardManager clipboard = context.getSystemService(ClipboardManager.class);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("LAN FTP connection", details));
            }
        } catch (Throwable throwable) {
            logWarning("Could not copy LAN FTP connection details", throwable);
        }
    }

    private static int[] parsePassiveRange(String value) {
        if (value == null || !value.trim().matches("\\d{4,5}\\s*-\\s*\\d{4,5}")) {
            throw new IllegalArgumentException("Invalid passive port range");
        }
        String[] parts = value.trim().split("\\s*-\\s*", 2);
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    private static void applyPassiveRange(GboardPatchesSettingsContract.FeatureHost host,
            Callbacks callbacks, int controlPort, String value) {
        try {
            int[] range = parsePassiveRange(value);
            LanFtpPortPolicy.requireValid(controlPort, range[0], range[1]);
            if (!callbacks.setPassivePortRange(range[0], range[1])) {
                throw new IllegalArgumentException("Passive port range could not be saved");
            }
        } catch (Throwable throwable) {
            logWarning("Invalid LAN FTP passive port range", throwable);
            GboardPatchesSettingsContract.showMessage(host,
                    throwable.getMessage() == null
                    ? "Invalid passive port range"
                    : throwable.getMessage());
        } finally {
            safelyRefresh(host, null);
        }
    }

    private static void applyPassword(GboardPatchesSettingsContract.FeatureHost host,
            Callbacks callbacks, String value) {
        if (!callbacks.setPassword(value)) {
            GboardPatchesSettingsContract.showMessage(host, text(
                    host.getContext(), R.string.gboard_patches_lan_ftp_password_invalid,
                    "Password must contain exactly six lowercase letters."));
        }
        safelyRefresh(host, null);
    }

    private static void applyEnabledWithPermission(
            GboardPatchesSettingsContract.FeatureHost host, Callbacks callbacks, boolean enabled,
            boolean sharedStorageRoot, boolean localNetworkAccess, boolean allFilesAccess) {
        if (enabled && sharedStorageRoot
                && !allFilesAccess) {
            GboardPatchesSettingsContract.showMessage(host, text(
                    host.getContext(),
                    R.string.gboard_patches_lan_ftp_all_files_required_message,
                    "All files access is required to share /sdcard."));
            safelyRun(() -> GboardPatchesSettingsContract.openAllFilesAccessSettings(
                    host,
                    text(
                            host.getContext(),
                            R.string.gboard_patches_lan_ftp_all_files_settings_unavailable,
                            "All files access settings are unavailable.")));
            safelyRefresh(host, null);
            return;
        }
        if (!enabled || localNetworkAccess) {
            safelyRefresh(host, () -> callbacks.setEnabled(enabled));
            return;
        }
        GboardPatchesSettingsContract.requestRuntimePermission(host,
                "android.permission.ACCESS_LOCAL_NETWORK",
                granted -> {
                    if (granted) {
                        safelyRefresh(host, () -> callbacks.setEnabled(true));
                    } else {
                        GboardPatchesSettingsContract.showMessage(
                                host,
                                "Local network permission is required to start LAN FTP");
                        safelyRefresh(host, null);
                    }
                });
    }

    private void showRootChoice(GboardPatchesSettingsContract.FeatureHost host,
            Callbacks callbacks, String currentRoot) {
        String current = LanFtpServerConfigSnapshot.SHARED_STORAGE_ROOT_URI.equals(currentRoot)
                ? LanFtpServerConfigSnapshot.SHARED_STORAGE_ROOT_URI
                : ROOT_PICKER_VALUE;
        GboardPatchesSettingsContract.showChoiceDialog(host,
                text(R.string.gboard_patches_lan_ftp_root_title, "Root folder"),
                new String[]{
                        text(R.string.gboard_patches_lan_ftp_root_shared,
                                "Shared storage (/sdcard)"),
                        text(R.string.gboard_patches_lan_ftp_root_choose,
                                "Choose another folder…")},
                new String[]{LanFtpServerConfigSnapshot.SHARED_STORAGE_ROOT_URI,
                        ROOT_PICKER_VALUE},
                current,
                null,
                null,
                value -> {
                    if (ROOT_PICKER_VALUE.equals(value)) {
                        safelyRun(() -> GboardPatchesSettingsContract.openDocumentTree(host,
                                currentRoot,
                                selected -> safelyRefresh(host,
                                        () -> callbacks.setRootTreeUri(selected))));
                    } else {
                        safelyRefresh(host, () -> callbacks.setRootTreeUri(value));
                    }
                });
    }

    @FunctionalInterface
    private interface UnsafeAction {
        void run() throws Throwable;
    }

    interface Callbacks {
        boolean setEnabled(boolean value);
        boolean setControlPort(int value);
        boolean setPassivePortRange(int start, int end);
        boolean setRootTreeUri(String value);
        boolean setReadOnly(boolean value);
        boolean setAllowAnonymous(boolean value);
        boolean setPassword(String value);
        String regeneratePassword();
        boolean kickSession(String sessionId);

        static Callbacks noop() {
            return new Callbacks() {
                @Override public boolean setEnabled(boolean value) { return true; }
                @Override public boolean setControlPort(int value) { return true; }
                @Override public boolean setPassivePortRange(int start, int end) { return true; }
                @Override public boolean setRootTreeUri(String value) { return true; }
                @Override public boolean setReadOnly(boolean value) { return true; }
                @Override public boolean setAllowAnonymous(boolean value) { return true; }
                @Override public boolean setPassword(String value) { return true; }
                @Override public String regeneratePassword() { return ""; }
                @Override public boolean kickSession(String sessionId) { return true; }
            };
        }
    }
}
