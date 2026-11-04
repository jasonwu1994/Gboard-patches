package dev.jason.gboardpatches.extension.settings.search;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.content.Context;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;

public final class GboardSettingsSearchIndexTest {
    @Test
    public void repeatedClassesAmongSiblingsAreNumbered() {
        List<GboardPatchesSettingsContract.Feature> siblings = Arrays.asList(
                new Page(true), new OtherPage(), new Page(true));

        assertEquals(Page.class.getName(), GboardSettingsSearchIndex.pathElement(siblings, 0));
        assertEquals(OtherPage.class.getName(),
                GboardSettingsSearchIndex.pathElement(siblings, 1));
        assertEquals(Page.class.getName() + "@1",
                GboardSettingsSearchIndex.pathElement(siblings, 2));
    }

    @Test
    public void resolverFollowsNumberedPathsAndSkipsUnavailablePages() {
        Page second = new Page(true);
        Page hidden = new Page(false);
        Group group = new Group(Arrays.asList(new Page(true), second, hidden));
        GboardSettingsSearchIndex.PathResolver resolver =
                new GboardSettingsSearchIndex.PathResolver(null,
                        Collections.singletonList(group));

        List<GboardPatchesSettingsContract.Feature> path = resolver.resolve(new String[] {
                Group.class.getName(), Page.class.getName() + "@1"});
        assertEquals(2, path.size());
        assertSame(group, path.get(0));
        assertSame(second, path.get(1));
        assertNull(resolver.resolve(new String[] {
                Group.class.getName(), Page.class.getName() + "@2"}));
        assertNull(resolver.resolve(new String[] {
                Group.class.getName(), Page.class.getName() + "@3"}));
    }

    private static class Page implements GboardPatchesSettingsContract.Feature {
        private final boolean available;

        Page(boolean available) {
            this.available = available;
        }

        @Override public String getEntryTitle() { return "Page"; }
        @Override public String getEntrySummary() { return ""; }
        @Override public boolean isAvailable(Context context) { return available; }
        @Override public GboardPatchesSettingsContract.Screen buildScreen(
                GboardPatchesSettingsContract.FeatureHost host) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class OtherPage extends Page {
        OtherPage() {
            super(true);
        }
    }

    private static final class Group extends Page {
        private final List<GboardPatchesSettingsContract.Feature> children;

        Group(List<GboardPatchesSettingsContract.Feature> children) {
            super(true);
            this.children = children;
        }

        @Override public List<GboardPatchesSettingsContract.Feature> getNavigationChildren() {
            return children;
        }
    }
}
