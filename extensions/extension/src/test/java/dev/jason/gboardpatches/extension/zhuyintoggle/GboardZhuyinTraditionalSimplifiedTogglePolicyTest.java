package dev.jason.gboardpatches.extension.zhuyintoggle;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class GboardZhuyinTraditionalSimplifiedTogglePolicyTest {
    @Test
    public void exactZhuyinEngSlideUpIsTheOnlyConsumedGesture() {
        assertEquals(0x7f0b28c5,
                GboardZhuyinTraditionalSimplifiedTogglePolicy.TOGGLE_KEY_ID);
        assertTrue(GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldHandleKey(
                0x7f0b28c5));
        assertTrue(GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldConsume(
                0x7f0b28c5, "SLIDE_UP"));
        assertFalse(GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldConsume(
                0x7f0b28c5, "SLIDE_DOWN"));
        assertFalse(GboardZhuyinTraditionalSimplifiedTogglePolicy.shouldConsume(
                0x7f0b2969, "SLIDE_UP"));
    }

    @Test
    public void labelNamesTheNextOfficialConversionState() {
        assertEquals("簡",
                GboardZhuyinTraditionalSimplifiedTogglePolicy.nextLabel(false));
        assertEquals("繁",
                GboardZhuyinTraditionalSimplifiedTogglePolicy.nextLabel(true));
    }
}
