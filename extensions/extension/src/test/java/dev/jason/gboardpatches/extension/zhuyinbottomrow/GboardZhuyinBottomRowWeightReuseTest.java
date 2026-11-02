package dev.jason.gboardpatches.extension.zhuyinbottomrow;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Resources;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.ConscryptMode;

import java.lang.reflect.Field;
import java.util.IdentityHashMap;
import java.util.Map;

import dev.jason.gboardpatches.extension.zhuyinslide.GboardZhuyinSlideTargetFixture;


@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, manifest = Config.NONE)
@ConscryptMode(ConscryptMode.Mode.OFF)
public final class GboardZhuyinBottomRowWeightReuseTest {
    private static final String[] NAMES = {"key_pos_switch_to_symbol", "A02",
            "key_pos_switch_to_next_language", "key_pos_space", "A06",
            "key_pos_del", "key_pos_ime_action"};
    private static final float[] STOCK = {150, 100, 500, 100, 400, 300, 100, 100, 150};
    private static final float[] CUSTOM = {110, 120, 450, 130, 320, 140, 180, 160, 170};

    @Test
    public void sameViewsRestoreAllSevenKeysAndBothClustersWhenDisabled() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.assertWeights(CUSTOM);
        f.bind(false);
        f.assertWeights(STOCK);
        f.bind(false);
        f.assertWeights(STOCK);
    }

    @Test
    public void repeatedEnabledBindsDoNotCaptureModuleWeightsAsStock() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.bind(true);
        f.bind(false);
        f.assertWeights(STOCK);
    }

    @Test
    public void foreignWeightUpdatesBecomeTheNextBaselineWithoutLosingOtherFields()
            throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        LinearLayout.LayoutParams params = f.params(5);
        params.weight = 365;
        params.width = 77;
        params.leftMargin = 9;
        f.bind(true);
        f.bind(false);
        float[] expected = STOCK.clone();
        expected[5] = 365;
        f.assertWeights(expected);
        Assert.assertSame(params, f.views[5].getLayoutParams());
        Assert.assertEquals(77, params.width);
        Assert.assertEquals(9, params.leftMargin);
    }

    @Test
    public void replacementLayoutParamsAndChangedParentAreNeverRestoredFromOldBaseline()
            throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        LinearLayout.LayoutParams replacement = new LinearLayout.LayoutParams(88, 44, 140);
        f.views[5].setLayoutParams(replacement);
        LinearLayout oldParent = (LinearLayout) f.views[7].getParent();
        oldParent.removeView(f.views[7]);
        LinearLayout foreignParent = new LinearLayout(f.context);
        foreignParent.addView(f.views[7]);
        f.bind(false);
        Assert.assertSame(replacement, f.views[5].getLayoutParams());
        Assert.assertEquals(140, replacement.weight, 0);
        Assert.assertEquals(160, f.params(7).weight, 0);
    }

    @Test
    public void reusedViewsWithNonZhuyinMetadataReleaseOnlyModuleWeights() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.makeNonZhuyin();
        f.params(1).weight = 231;
        f.bind(true);
        float[] expected = STOCK.clone();
        expected[1] = 231;
        f.assertWeights(expected);
    }

    @Test
    public void unknownUnownedFooterIsUntouchedEvenWithEnabledSettings() throws Throwable {
        Fixture f = new Fixture();
        f.makeNonZhuyin();
        f.params(0).weight = 432;
        f.bind(true);
        float[] expected = STOCK.clone();
        expected[0] = 432;
        f.assertWeights(expected);
    }

    @Test
    public void releasedScopeCanPublishANewBaselineEqualToThePreviousModuleWeight()
            throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        GboardZhuyinBottomRowWeightRuntime.restoreOwnedWeights(f.keys[0]);
        f.assertWeights(STOCK);
        f.params(5).weight = CUSTOM[5];
        f.params(5).height = 81;
        f.bind(true);
        f.bind(false);
        float[] expected = STOCK.clone();
        expected[5] = CUSTOM[5];
        f.assertWeights(expected);
        Assert.assertEquals(81, f.params(5).height);
    }

    @Test
    public void disablingPreservesForeignWeightWithoutAnIntermediateEnabledApply()
            throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.params(5).weight = 347;
        f.bind(false);
        float[] expected = STOCK.clone();
        expected[5] = 347;
        f.assertWeights(expected);
    }

    @Test
    public void cleanupDoesNotChangeAnotherKeyboardInstance() throws Throwable {
        Fixture first = new Fixture();
        Fixture second = new Fixture();
        first.bind(true);
        second.bind(false);
        first.assertWeights(CUSTOM);
        first.bind(false);
        first.assertWeights(STOCK);
    }

    @Test
    public void composingControlBindsKeepAllWeightsAndLayoutParams() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        Object[] params = new Object[f.views.length];
        for (int i = 0; i < params.length; i++) params[i] = f.views[i].getLayoutParams();
        for (String label : new String[] {"ˉ", "", "注音"}) {
            f.metadata.put(f.keys[3], new Metadata(label, false));
            for (int key : new int[] {3, 5, 6}) {
                f.afterBind(f.keys[key]);
                f.assertWeights(CUSTOM);
                for (int i = 0; i < params.length; i++) {
                    Assert.assertSame(params[i], f.views[i].getLayoutParams());
                }
            }
        }
    }

    @Test
    public void firstBindDuringCompositionUsesSiblingZhuyinBody() throws Throwable {
        Fixture f = new Fixture();
        f.setSettings(true);
        f.metadata.put(f.keys[3], new Metadata("ˉ", false));
        f.afterBind(f.keys[6]);
        f.assertWeights(CUSTOM);
    }

    @Test
    public void unknownControlMetadataDoesNotRevokeOwnedFooter() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.metadata.remove(f.bodyKeys[0]);
        f.metadata.remove(f.bodyKeys[1]);
        for (View key : f.keys) f.metadata.put(key, new Metadata("", false));
        f.afterBind(f.keys[6]);
        f.assertWeights(CUSTOM);
        f.bind(false);
        f.assertWeights(STOCK);
    }

    @Test
    public void unknownUnownedComposingFooterIsNotClaimed() throws Throwable {
        Fixture f = new Fixture();
        f.metadata.remove(f.bodyKeys[0]);
        f.metadata.remove(f.bodyKeys[1]);
        f.metadata.put(f.keys[3], new Metadata("ˉ", false));
        f.setSettings(true);
        f.afterBind(f.keys[6]);
        f.assertWeights(STOCK);
    }

    @Test
    public void foreignBodyReleasesWeightsEvenBeforeFooterLabelsChange() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.metadata.put(f.bodyKeys[0], new Metadata("a", false));
        f.metadata.put(f.bodyKeys[1], new Metadata("b", false));
        f.afterBind(f.bodyKeys[1]);
        f.assertWeights(STOCK);
    }

    @Test
    public void partialBodyRebindKeepsWeightsWhileZhuyinKeysRemain() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.metadata.put(f.bodyKeys[0], new Metadata("a", false));
        f.metadata.put(f.keys[3], new Metadata("ˉ", false));
        f.afterBind(f.bodyKeys[0]);
        f.assertWeights(CUSTOM);
    }

    @Test
    public void changedFooterHierarchyReleasesOwnedWeights() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        ((LinearLayout) f.keys[4].getParent()).removeView(f.keys[4]);
        f.afterBind(f.bodyKeys[0]);
        float[] expected = STOCK.clone();
        // A moved view no longer belongs to the recorded parent.
        expected[6] = CUSTOM[6];
        f.assertWeights(expected);
    }

    @Test
    public void stockWeightUpdateDuringCompositionBecomesNewBaseline() throws Throwable {
        Fixture f = new Fixture();
        f.bind(true);
        f.params(5).weight = 365;
        f.metadata.put(f.keys[3], new Metadata("ˉ", false));
        f.afterBind(f.keys[3]);
        f.assertWeights(CUSTOM);
        f.bind(false);
        float[] expected = STOCK.clone();
        expected[5] = 365;
        f.assertWeights(expected);
    }

    private static final class Fixture {
        final Context context;
        final View[] keys = new View[7];
        final View[] bodyKeys = new View[2];
        final View[] views;
        final Map<View, Metadata> metadata = new IdentityHashMap<>();

        Fixture() throws Exception {
            Context app = RuntimeEnvironment.getApplication();
            Resources base = app.getResources();
            Resources resources = new Resources(base.getAssets(), base.getDisplayMetrics(),
                    base.getConfiguration()) {
                @Override public String getResourceEntryName(int id) {
                    if (id >= 1 && id <= NAMES.length) return NAMES[id - 1];
                    return super.getResourceEntryName(id);
                }
            };
            context = new ContextWrapper(app) {
                @Override public Resources getResources() { return resources; }
            };
            SoftKeyboardView keyboard = new SoftKeyboardView(context);
            LinearLayout body = new LinearLayout(context);
            keyboard.addView(body);
            for (int i = 0; i < bodyKeys.length; i++) {
                bodyKeys[i] = (View) GboardZhuyinSlideTargetFixture.newSoftKeyView(context);
                body.addView(bodyKeys[i]);
                metadata.put(bodyKeys[i], new Metadata(i == 0 ? "ㄅ" : "ㄉ", true));
            }
            LinearLayout footer = new LinearLayout(context);
            LinearLayout middle = new LinearLayout(context);
            LinearLayout space = new LinearLayout(context);
            keyboard.addView(footer);
            for (int i = 0; i < keys.length; i++) {
                keys[i] = (View) GboardZhuyinSlideTargetFixture.newSoftKeyView(context);
                GboardZhuyinSlideTargetFixture.setResourceEntryName(keys[i], i + 1, NAMES[i]);
                metadata.put(keys[i], new Metadata(i == 1 ? "，" : i == 3 ? "注音"
                        : i == 4 ? "ㄦ" : "", false));
            }
            footer.addView(keys[0]); footer.addView(keys[1]); footer.addView(middle);
            footer.addView(keys[5]); footer.addView(keys[6]);
            middle.addView(keys[2]); middle.addView(space);
            space.addView(keys[3]); space.addView(keys[4]);
            views = new View[] {keys[0], keys[1], middle, keys[2], space, keys[3], keys[4],
                    keys[5], keys[6]};
            for (int i = 0; i < views.length; i++) {
                views[i].setLayoutParams(new LinearLayout.LayoutParams(0, 40, STOCK[i]));
            }
        }

        void bind(boolean enabled) throws Throwable {
            setSettings(enabled);
            afterBind(keys[0]);
        }

        void setSettings(boolean enabled) throws Throwable {
            Field cached = GboardZhuyinBottomRowWeightRuntime.class
                    .getDeclaredField("cachedSettingsSnapshot");
            cached.setAccessible(true);
            cached.set(null, new GboardZhuyinBottomRowWeightRuntime.SettingsSnapshot(
                    SystemClock.elapsedRealtime(), enabled, 110, 120, 130, 140, 180, 160,
                    170));
        }

        void afterBind(View key) throws Throwable {
            for (View view : keys) publishMetadata(view);
            for (View view : bodyKeys) publishMetadata(view);
            GboardZhuyinBottomRowWeightRuntime.afterSoftKeyBound(key);
        }

        void makeNonZhuyin() {
            for (View key : keys) metadata.put(key, new Metadata("english", false));
            for (View key : bodyKeys) metadata.put(key, new Metadata("a", false));
        }

        LinearLayout.LayoutParams params(int index) {
            return (LinearLayout.LayoutParams) views[index].getLayoutParams();
        }

        void assertWeights(float[] expected) {
            for (int i = 0; i < views.length; i++) {
                Assert.assertEquals("weight target " + i, expected[i], params(i).weight, 0);
            }
        }

        void publishMetadata(View view) throws Exception {
            Metadata value = metadata.get(view);
            Object actual = value == null ? null : GboardZhuyinSlideTargetFixture.metadata(
                    view.getId(), value.label, value.label, new String[] {value.label},
                    value.zhuyin ? "up" : null, null);
            view.getClass().getField("e").set(view, actual);
        }
    }

    private static final class SoftKeyboardView extends LinearLayout {
        SoftKeyboardView(Context context) { super(context); }
    }

    private static final class Metadata {
        final String label;
        final boolean zhuyin;
        Metadata(String label, boolean zhuyin) { this.label = label; this.zhuyin = zhuyin; }
    }
}
