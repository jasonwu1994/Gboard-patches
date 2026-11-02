package dev.jason.gboardpatches.extension.settings.search;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;

import org.robolectric.Shadows;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsTestHost;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsFeatureRegistry;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

/**
 * Walks the settings feature tree under Robolectric and records which string resources name each
 * page and setting row. {@link TokenText} makes every text lookup return the resource name, so the
 * index maps rows to resources exactly instead of matching rendered text back to resources.
 */
final class GboardSettingsSearchIndexCrawler {
    private static final String TOKEN_PREFIX = "\u2063R:";
    private static final String FORMATTED_TOKEN_PREFIX = "\u2063F:";
    private static final String TOKEN_SUFFIX = "\u2063";

    private static final Map<Integer, String> STRING_NAMES = stringNames();

    private GboardSettingsSearchIndexCrawler() {
    }

    static final class CrawledEntry {
        final List<String> path;
        final String titleName;
        final String summaryName;
        final boolean page;

        CrawledEntry(List<String> path, String titleName, String summaryName, boolean page) {
            this.path = path;
            this.titleName = titleName;
            this.summaryName = summaryName;
            this.page = page;
        }
    }

    static final class Result {
        final List<CrawledEntry> entries = new ArrayList<CrawledEntry>();
        final Map<String, String> failures = new LinkedHashMap<String, String>();
    }

    static Result crawl(Context context) {
        markEveryFeaturePatched(context);
        Result result = new Result();
        Set<String> seen = new LinkedHashSet<String>();
        crawlSiblings(context, GboardPatchesSettingsFeatureRegistry.allFeatures(context),
                new ArrayList<String>(), result, seen);
        return result;
    }

    private static void crawlSiblings(Context context,
            List<GboardPatchesSettingsContract.Feature> siblings,
            List<String> parentPath,
            Result result,
            Set<String> seen) {
        for (int index = 0; index < siblings.size(); index++) {
            GboardPatchesSettingsContract.Feature feature = siblings.get(index);
            if (feature != null) {
                crawlFeature(context, feature,
                        GboardSettingsSearchIndex.pathElement(siblings, index),
                        parentPath, result, seen);
            }
        }
    }

    private static void crawlFeature(Context context,
            GboardPatchesSettingsContract.Feature feature,
            String pathElement,
            List<String> parentPath,
            Result result,
            Set<String> seen) {
        List<String> path = new ArrayList<String>(parentPath);
        path.add(pathElement);
        String pathKey = String.join(">", path);
        try {
            // As on the settings page, availability runs first; some pages load their text there.
            feature.isAvailable(context);
        } catch (Throwable throwable) {
            result.failures.put(pathKey, throwable.toString());
        }
        add(result, seen, path, pureName(feature.getEntryTitle()),
                pureName(feature.getEntrySummary()), true);

        List<GboardPatchesSettingsContract.Feature> children = feature.getNavigationChildren();
        Set<String> childTitles = new LinkedHashSet<String>();
        for (GboardPatchesSettingsContract.Feature child : children) {
            try {
                child.isAvailable(context);
            } catch (Throwable ignored) {
                // Reported when the child itself is crawled.
            }
            String childTitle = pureName(child.getEntryTitle());
            if (childTitle != null) {
                childTitles.add(childTitle);
            }
        }

        try {
            GboardPatchesSettingsContract.Screen screen =
                    feature.buildScreen(new GboardPatchesSettingsTestHost(context));
            for (GboardPatchesSettingsContract.Section section : screen.getSections()) {
                for (GboardPatchesSettingsContract.Row row : section.getItems()) {
                    if (!isSearchableRow(row)) {
                        continue;
                    }
                    String titleName = pureName(row.getTitle());
                    if (titleName == null || childTitles.contains(titleName)) {
                        continue;
                    }
                    add(result, seen, path, titleName, pureName(row.getSummary()), false);
                }
            }
        } catch (Throwable throwable) {
            result.failures.put(pathKey, throwable.toString());
        }

        crawlSiblings(context, children, path, result, seen);
    }

    private static void add(Result result, Set<String> seen, List<String> path,
            String titleName, String summaryName, boolean page) {
        if (titleName == null) {
            return;
        }
        String key = String.join(">", path) + "#" + titleName;
        if (seen.add(key)) {
            result.entries.add(new CrawledEntry(path, titleName, summaryName, page));
        }
    }

    /** Rows a person turns or picks, by name; read-only details and custom views are left out. */
    private static boolean isSearchableRow(GboardPatchesSettingsContract.Row row) {
        if (row instanceof GboardPatchesSettingsContract.SearchRow) {
            return false;
        }
        return row instanceof GboardPatchesSettingsContract.ToggleRow
                || row instanceof GboardPatchesSettingsContract.SwitchRow
                || row instanceof GboardPatchesSettingsContract.SelectorRow
                || row instanceof GboardPatchesSettingsContract.NavigationRow
                || row instanceof GboardPatchesSettingsContract.CommandRow
                || row instanceof GboardPatchesSettingsContract.ActionRow;
    }

    /** The resource name when the text is exactly one unformatted string resource, else null. */
    static String pureName(CharSequence text) {
        if (text == null) {
            return null;
        }
        String value = text.toString();
        if (!value.startsWith(TOKEN_PREFIX) || !value.endsWith(TOKEN_SUFFIX)) {
            return null;
        }
        String name = value.substring(
                TOKEN_PREFIX.length(), value.length() - TOKEN_SUFFIX.length());
        return name.isEmpty() || name.contains(TOKEN_SUFFIX) ? null : name;
    }

    /** Declares every feature marker, so rows shown only for patched features are indexed too. */
    private static void markEveryFeaturePatched(Context context) {
        Bundle metaData = new Bundle();
        for (Field field : GboardPatchesFeatureAvailability.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == String.class
                    && field.getName().startsWith("FEATURE_")) {
                try {
                    metaData.putBoolean((String) field.get(null), true);
                } catch (IllegalAccessException ignored) {
                    // Public static fields are always readable.
                }
            }
        }
        PackageInfo packageInfo = Shadows.shadowOf(context.getPackageManager())
                .getInternalMutablePackageInfo(context.getPackageName());
        packageInfo.applicationInfo.metaData = metaData;
    }

    private static String token(int resId) {
        String name = STRING_NAMES.get(resId);
        return name == null ? "" : TOKEN_PREFIX + name + TOKEN_SUFFIX;
    }

    private static String formattedToken(int resId) {
        return FORMATTED_TOKEN_PREFIX + resId + TOKEN_SUFFIX;
    }

    private static Map<Integer, String> stringNames() {
        Map<Integer, String> names = new HashMap<Integer, String>();
        for (Field field : R.string.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || field.getType() != int.class) {
                continue;
            }
            try {
                names.put(field.getInt(null), field.getName());
            } catch (IllegalAccessException ignored) {
                // Public static fields are always readable.
            }
        }
        return names;
    }

    @Implements(value = GboardSettingsText.class, isInAndroidSdk = false)
    public static class TokenText {
        @Implementation
        public static String get(Context context, int resId) {
            return token(resId);
        }

        @Implementation
        public static String format(Context context, int resId, Object... args) {
            return formattedToken(resId);
        }

        @Implementation
        public static String quantity(Context context, int resId, int quantity) {
            return formattedToken(resId);
        }
    }
}
