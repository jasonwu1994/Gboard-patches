package dev.jason.gboardpatches.extension.clipboard;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;
import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncService;
import dev.jason.gboardpatches.extension.webclipboard.ClipboardSyncWebPortal;
import dev.jason.gboardpatches.extension.webclipboard.WebClipboardEndpointResolver;
import dev.jason.gboardpatches.extension.webclipboard.WebClipboardPreferences;
import dev.jason.gboardpatches.extension.webclipboard.WebClipboardTileController;

final class GboardClipboardSyncSettingsSection {
    private final TextLookup textLookup;
    private final String headerBadge;
    private final String entryTitle;
    private final String entrySummary;
    private final String sectionGeneral;
    private final String sectionSecurity;
    private final String sectionNetwork;
    private final String sectionConnectedClients;
    private final String titleEnable;
    private final String titleToast;
    private final String toastSummary;
    private final String titlePort;
    private final String titlePairing;
    private final String titlePairingCode;
    private final String titleUrls;
    private final String titleClients;
    private final String errorPairingCode;
    private final String clientsFeatureSummary;
    private final String noClientsSummary;
    private final String clientsSummary;
    private final String connectedClientsSummary;
    private final String regenerateCodeTitle;
    private final String regenerateCodeSummary;
    private final GboardPatchesSettingsContract.Feature connectedClientsFeature =
            new GboardPatchesSettingsContract.Feature() {
                @Override
                public String getEntryTitle() {
                    return titleClients;
                }

                @Override
                public String getEntrySummary() {
                    return clientsFeatureSummary;
                }

                @Override
                public GboardPatchesSettingsContract.Screen buildScreen(
                        GboardPatchesSettingsContract.FeatureHost host) {
                    return buildConnectedClientsScreen(host);
                }
            };

    GboardClipboardSyncSettingsSection(Context context) {
        this(new ContextTextLookup(context));
    }

    static GboardClipboardSyncSettingsSection forTests() {
        return new GboardClipboardSyncSettingsSection(TestTextLookup.INSTANCE);
    }

    private GboardClipboardSyncSettingsSection(TextLookup textLookup) {
        this.textLookup = textLookup;
        headerBadge = textLookup.headerBadge();
        entryTitle = textLookup.entryTitle();
        entrySummary = textLookup.entrySummary();
        sectionGeneral = textLookup.sectionGeneral();
        sectionSecurity = textLookup.sectionSecurity();
        sectionNetwork = textLookup.sectionNetwork();
        sectionConnectedClients = textLookup.sectionConnectedClients();
        titleEnable = textLookup.titleEnable();
        titleToast = textLookup.titleToast();
        toastSummary = textLookup.toastSummary();
        titlePort = textLookup.titlePort();
        titlePairing = textLookup.titlePairing();
        titlePairingCode = textLookup.titlePairingCode();
        titleUrls = textLookup.titleUrls();
        titleClients = textLookup.titleClients();
        errorPairingCode = textLookup.errorPairingCode();
        clientsFeatureSummary = textLookup.clientsFeatureSummary();
        noClientsSummary = textLookup.noClientsSummary();
        clientsSummary = textLookup.clientsSummary();
        connectedClientsSummary = textLookup.connectedClientsSummary();
        regenerateCodeTitle = textLookup.regenerateCodeTitle();
        regenerateCodeSummary = textLookup.regenerateCodeSummary();
    }

    String getEntryTitle() {
        return entryTitle;
    }

    String getEntrySummary() {
        return entrySummary;
    }

    String getEntrySummary(Context context, SharedPreferences preferences) {
        int port = WebClipboardPreferences.getPort(preferences);
        return textLookup.entryHostedSummary(firstUrl(port));
    }

    GboardPatchesSettingsContract.Screen buildHomeScreen(
            GboardPatchesSettingsContract.FeatureHost host,
            SharedPreferences preferences) {
        Context context = host.getContext();
        WebClipboardPreferences.ensureDefaults(preferences);
        int port = WebClipboardPreferences.getPort(preferences);
        boolean enabled = WebClipboardPreferences.isEnabled(preferences);
        boolean toastEnabled = WebClipboardPreferences.isToastEnabled(preferences);
        boolean pairingRequired = WebClipboardPreferences.isPairingRequired(preferences);
        String pairingCode = WebClipboardPreferences.getPairingCode(preferences);
        List<ClipboardSyncWebPortal.ConnectedClientSnapshot> clients =
                ClipboardSyncService.getConnectedClientSnapshots();

        List<GboardPatchesSettingsContract.Row> generalRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        generalRows.add(new GboardPatchesSettingsContract.ToggleRow(
                titleEnable,
                "",
                true,
                enabled,
                value -> WebClipboardTileController.applyEnabled(context, value)));
        generalRows.add(new GboardPatchesSettingsContract.ToggleRow(
                titleToast,
                toastSummary,
                enabled,
                toastEnabled,
                value -> WebClipboardTileController.applyToastEnabled(context, value)));

        List<GboardPatchesSettingsContract.Row> securityRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        securityRows.add(new GboardPatchesSettingsContract.ToggleRow(
                titlePairing,
                textLookup.pairingSummary(),
                enabled,
                pairingRequired,
                value -> {
                    WebClipboardTileController.applyPairingRequired(context, value);
                    GboardPatchesSettingsContract.refresh(host);
                }));
        securityRows.add(new GboardPatchesSettingsContract.SelectorRow(
                titlePairingCode,
                pairingRequired
                        ? textLookup.pairingCodeSummaryOn(pairingCode)
                        : textLookup.pairingCodeSummaryOff(),
                pairingCode,
                enabled,
                () -> GboardPatchesSettingsContract.showTextInputDialog(host,
                        titlePairingCode,
                        pairingCode,
                        pairingCode,
                        value -> {
                            String normalizedCode = normalizePairingCodeInput(value);
                            WebClipboardTileController.applyPairingCode(context, normalizedCode);
                            GboardPatchesSettingsContract.refresh(host);
                        })));
        securityRows.add(new GboardPatchesSettingsContract.CommandRow(
                regenerateCodeTitle,
                regenerateCodeSummary,
                enabled,
                () -> {
                    WebClipboardTileController.regeneratePairingCode(context);
                    GboardPatchesSettingsContract.refresh(host);
                }));

        List<GboardPatchesSettingsContract.Row> networkRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        networkRows.add(new GboardPatchesSettingsContract.SelectorRow(
                titlePort,
                textLookup.portSummary(),
                Integer.toString(port),
                true,
                () -> GboardPatchesSettingsContract.showPositiveIntegerDialog(host,
                        titlePort,
                        textLookup.portHint(WebClipboardPreferences.DEFAULT_PORT),
                        port,
                        value -> WebClipboardTileController.applyPort(context, value))));
        networkRows.add(new GboardPatchesSettingsContract.DetailRow(
                titleUrls,
                String.join("\n", portalUrls(port)),
                enabled,
                true));

        List<GboardPatchesSettingsContract.Row> clientsRows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        clientsRows.add(new GboardPatchesSettingsContract.NavigationRow(
                titleClients,
                clientsHomeSummary(clients),
                Integer.toString(clients.size()),
                enabled,
                () -> GboardPatchesSettingsContract.openFeature(host, connectedClientsFeature)));

        return new GboardPatchesSettingsContract.Screen(
                entryTitle,
                headerBadge,
                entryTitle,
                entrySummary,
                Collections.emptyList(),
                java.util.Arrays.asList(
                        new GboardPatchesSettingsContract.Section(sectionGeneral, generalRows),
                        new GboardPatchesSettingsContract.Section(sectionSecurity, securityRows),
                        new GboardPatchesSettingsContract.Section(sectionNetwork, networkRows),
                        new GboardPatchesSettingsContract.Section(
                                sectionConnectedClients,
                                clientsRows)),
                GboardPatchesSettingsContract.RefreshPolicy.liveStatusOnly(1_000L));
    }

    private GboardPatchesSettingsContract.Screen buildConnectedClientsScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        List<ClipboardSyncWebPortal.ConnectedClientSnapshot> clients =
                ClipboardSyncService.getConnectedClientSnapshots();
        List<GboardPatchesSettingsContract.Row> rows =
                new ArrayList<GboardPatchesSettingsContract.Row>();
        List<GboardPatchesSettingsContract.StatusBlock> statusBlocks =
                new ArrayList<GboardPatchesSettingsContract.StatusBlock>();
        if (clients.isEmpty()) {
            statusBlocks.add(new GboardPatchesSettingsContract.StatusBlock(
                    titleClients + ": 0",
                    noClientsSummary,
                    GboardPatchesSettingsContract.StatusTone.INFO));
        }
        for (ClipboardSyncWebPortal.ConnectedClientSnapshot client : clients) {
            String clientTitle = clientTitle(client);
            rows.add(new GboardPatchesSettingsContract.DangerRow(
                    textLookup.kickTitle(clientTitle),
                    clientDetail(client),
                    true,
                    () -> {
                        ClipboardSyncService.kickConnectedClient(client.id);
                        GboardPatchesSettingsContract.refresh(host);
                    },
                    textLookup.kickConfirmTitle(),
                    textLookup.kickConfirmMessage(clientTitle)));
        }
        return new GboardPatchesSettingsContract.Screen(
                titleClients,
                headerBadge,
                titleClients,
                connectedClientsSummary,
                statusBlocks,
                rows.isEmpty()
                        ? Collections.emptyList()
                        : Collections.singletonList(new GboardPatchesSettingsContract.Section(
                                titleClients,
                                rows)),
                GboardPatchesSettingsContract.RefreshPolicy.liveStatusOnly(1_000L));
    }

    private String firstUrl(int port) {
        List<String> urls = WebClipboardEndpointResolver.resolveUrls(port);
        return urls.isEmpty()
                ? "http://<phone LAN IP>:" + port
                : trimTrailingSlash(urls.get(0));
    }

    private List<String> portalUrls(int port) {
        List<String> urls = new ArrayList<String>();
        for (String url : WebClipboardEndpointResolver.resolveUrls(port)) {
            urls.add(trimTrailingSlash(url));
        }
        return urls;
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.isBlank() || !url.endsWith("/")) {
            return url;
        }
        return url.substring(0, url.length() - 1);
    }

    private String clientsHomeSummary(List<ClipboardSyncWebPortal.ConnectedClientSnapshot> clients) {
        return clients.isEmpty() ? noClientsSummary : clientsSummary;
    }

    private String clientTitle(ClipboardSyncWebPortal.ConnectedClientSnapshot client) {
        String browser = client.browser == null || client.browser.isBlank()
                ? textLookup.clientBrowserFallback()
                : client.browser;
        return textLookup.clientTitle(browser, client.label);
    }

    private String clientDetail(ClipboardSyncWebPortal.ConnectedClientSnapshot client) {
        String agent = client.agent == null || client.agent.isBlank()
                ? textLookup.clientAgentFallback()
                : client.agent;
        return textLookup.clientDetail(client.address, agent);
    }

    private String normalizePairingCodeInput(String value) {
        String code = value == null ? "" : value.trim();
        if (!code.matches("\\d{4}")) {
            throw new IllegalArgumentException(errorPairingCode);
        }
        return code;
    }

    private interface TextLookup {
        String headerBadge();
        String entryTitle();
        String entrySummary();
        String sectionGeneral();
        String sectionSecurity();
        String sectionNetwork();
        String sectionConnectedClients();
        String titleEnable();
        String titleToast();
        String toastSummary();
        String titlePort();
        String titlePairing();
        String titlePairingCode();
        String titleUrls();
        String titleClients();
        String errorPairingCode();
        String clientsFeatureSummary();
        String noClientsSummary();
        String clientsSummary();
        String connectedClientsSummary();
        String regenerateCodeTitle();
        String regenerateCodeSummary();
        String pairingSummary();
        String pairingCodeSummaryOn(String pairingCode);
        String pairingCodeSummaryOff();
        String portSummary();
        String portHint(int defaultPort);
        String entryHostedSummary(String url);
        String kickTitle(String clientTitle);
        String kickConfirmTitle();
        String kickConfirmMessage(String clientTitle);
        String clientTitle(String browser, String label);
        String clientDetail(String address, String agent);
        String clientBrowserFallback();
        String clientAgentFallback();
    }

    private static final class ContextTextLookup implements TextLookup {
        private final Context context;

        private ContextTextLookup(Context context) {
            this.context = context;
        }

        @Override
        public String headerBadge() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_header_badge);
        }

        @Override
        public String entryTitle() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_title);
        }

        @Override
        public String entrySummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_summary);
        }

        @Override
        public String sectionGeneral() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_clipboard_section_general);
        }

        @Override
        public String sectionSecurity() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_section_security);
        }

        @Override
        public String sectionNetwork() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_section_network);
        }

        @Override
        public String sectionConnectedClients() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_section_connected_clients);
        }

        @Override
        public String titleEnable() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_enable_title);
        }

        @Override
        public String titleToast() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_toast_title);
        }

        @Override
        public String toastSummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_toast_summary);
        }

        @Override
        public String titlePort() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_port_title);
        }

        @Override
        public String titlePairing() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_pairing_title);
        }

        @Override
        public String titlePairingCode() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_pairing_code_title);
        }

        @Override
        public String titleUrls() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_urls_title);
        }

        @Override
        public String titleClients() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_clients_title);
        }

        @Override
        public String errorPairingCode() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_pairing_error);
        }

        @Override
        public String clientsFeatureSummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_clients_feature_summary);
        }

        @Override
        public String noClientsSummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_no_clients);
        }

        @Override
        public String clientsSummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_clients_summary);
        }

        @Override
        public String connectedClientsSummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_connected_summary);
        }

        @Override
        public String regenerateCodeTitle() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_regenerate_code_title);
        }

        @Override
        public String regenerateCodeSummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_regenerate_code_summary);
        }

        @Override
        public String pairingSummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_pairing_summary);
        }

        @Override
        public String pairingCodeSummaryOn(String pairingCode) {
            return GboardSettingsText.format(
                    context,
                    R.string.gboard_patches_web_clipboard_pairing_code_summary_on,
                    pairingCode);
        }

        @Override
        public String pairingCodeSummaryOff() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_pairing_code_summary_off);
        }

        @Override
        public String portSummary() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_port_summary);
        }

        @Override
        public String portHint(int defaultPort) {
            return GboardSettingsText.format(
                    context,
                    R.string.gboard_patches_web_clipboard_port_hint_default,
                    defaultPort);
        }

        @Override
        public String entryHostedSummary(String url) {
            return GboardSettingsText.format(
                    context,
                    R.string.gboard_patches_web_clipboard_entry_summary,
                    url);
        }

        @Override
        public String kickTitle(String clientTitle) {
            return GboardSettingsText.format(
                    context,
                    R.string.gboard_patches_web_clipboard_kick_title,
                    clientTitle);
        }

        @Override
        public String kickConfirmTitle() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_kick_confirm_title);
        }

        @Override
        public String kickConfirmMessage(String clientTitle) {
            return GboardSettingsText.format(
                    context,
                    R.string.gboard_patches_web_clipboard_kick_confirm_message,
                    clientTitle);
        }

        @Override
        public String clientTitle(String browser, String label) {
            return GboardSettingsText.format(
                    context,
                    R.string.gboard_patches_web_clipboard_client_title,
                    browser,
                    label);
        }

        @Override
        public String clientDetail(String address, String agent) {
            return GboardSettingsText.format(
                    context,
                    R.string.gboard_patches_web_clipboard_client_detail,
                    address,
                    agent);
        }

        @Override
        public String clientBrowserFallback() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_client_browser_fallback);
        }

        @Override
        public String clientAgentFallback() {
            return GboardSettingsText.get(
                    context,
                    R.string.gboard_patches_web_clipboard_client_agent_fallback);
        }
    }

    private static final class TestTextLookup implements TextLookup {
        private static final TestTextLookup INSTANCE = new TestTextLookup();

        @Override
        public String headerBadge() {
            return "Gboard";
        }

        @Override
        public String entryTitle() {
            return "Web Clipboard";
        }

        @Override
        public String entrySummary() {
            return "Zero-install clipboard sharing hosted by this phone. Open the same LAN page"
                    + " on multiple devices to keep your phone and browsers in sync."
                    + " Recommended: turn on Quick Settings Tile.";
        }

        @Override
        public String sectionGeneral() {
            return "General";
        }

        @Override
        public String sectionSecurity() {
            return "Security";
        }

        @Override
        public String sectionNetwork() {
            return "Network";
        }

        @Override
        public String sectionConnectedClients() {
            return "Connected clients";
        }

        @Override
        public String titleEnable() {
            return "Enable Web Clipboard";
        }

        @Override
        public String titleToast() {
            return "Show toast notification";
        }

        @Override
        public String toastSummary() {
            return "Show a brief notification when the clipboard is updated from a web client.";
        }

        @Override
        public String titlePort() {
            return "Web server port";
        }

        @Override
        public String titlePairing() {
            return "Require pairing code";
        }

        @Override
        public String titlePairingCode() {
            return "Pairing code";
        }

        @Override
        public String titleUrls() {
            return "Current URLs";
        }

        @Override
        public String titleClients() {
            return "Connected clients";
        }

        @Override
        public String errorPairingCode() {
            return "Enter a 4-digit code.";
        }

        @Override
        public String clientsFeatureSummary() {
            return "View connected Web Clipboard clients and kick browser sessions.";
        }

        @Override
        public String noClientsSummary() {
            return "No browser clients are connected.";
        }

        @Override
        public String clientsSummary() {
            return "Tap to view and kick browser clients.";
        }

        @Override
        public String connectedClientsSummary() {
            return "Browser clients currently connected to the Web Clipboard portal.";
        }

        @Override
        public String regenerateCodeTitle() {
            return "Regenerate pairing code";
        }

        @Override
        public String regenerateCodeSummary() {
            return "Create a new 4-digit code and restart the portal if it is running.";
        }

        @Override
        public String pairingSummary() {
            return "Default is on. Desktop browsers must enter a simple 4-digit code before"
                    + " they can sync unless you turn this off.";
        }

        @Override
        public String pairingCodeSummaryOn(String pairingCode) {
            return String.format(
                    Locale.US,
                    "Desktop users enter this 4-digit code or open a URL that includes"
                            + " ?code=%1$s.",
                    pairingCode);
        }

        @Override
        public String pairingCodeSummaryOff() {
            return "Pairing is off; this code is only used after you enable the switch.";
        }

        @Override
        public String portSummary() {
            return "Choose the port used by the phone-hosted web UI.";
        }

        @Override
        public String portHint(int defaultPort) {
            return Integer.toString(defaultPort);
        }

        @Override
        public String entryHostedSummary(String url) {
            return String.format(
                    Locale.US,
                    "Hosted on this phone at %1$s when enabled.",
                    url);
        }

        @Override
        public String kickTitle(String clientTitle) {
            return String.format(Locale.US, "Kick %1$s", clientTitle);
        }

        @Override
        public String kickConfirmTitle() {
            return "Kick connected client";
        }

        @Override
        public String kickConfirmMessage(String clientTitle) {
            return String.format(
                    Locale.US,
                    "Disconnect %1$s from Web Clipboard?",
                    clientTitle);
        }

        @Override
        public String clientTitle(String browser, String label) {
            return String.format(Locale.US, "%1$s %2$s", browser, label);
        }

        @Override
        public String clientDetail(String address, String agent) {
            return String.format(Locale.US, "IP: %1$s\nClient agent: %2$s", address, agent);
        }

        @Override
        public String clientBrowserFallback() {
            return "Browser";
        }

        @Override
        public String clientAgentFallback() {
            return "Unknown agent";
        }
    }
}
