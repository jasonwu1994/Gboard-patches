package dev.jason.gboardpatches.extension.emojisize;

import org.junit.Assert;
import org.junit.Test;

public final class GboardEmojiSizePolicyTest {
    @Test
    public void forcesOnlyExactFalseBooleanWhenEnabled() {
        Assert.assertEquals(Boolean.TRUE, GboardEmojiSizePolicy.maybeForce(
                "emoji_scale_supported", Boolean.FALSE, true));
        Assert.assertSame(Boolean.FALSE, GboardEmojiSizePolicy.maybeForce(
                "emoji_scale_supported", Boolean.FALSE, false));
        Assert.assertSame(Boolean.TRUE, GboardEmojiSizePolicy.maybeForce(
                "emoji_scale_supported", Boolean.TRUE, true));
        Assert.assertSame(Boolean.FALSE, GboardEmojiSizePolicy.maybeForce(
                "unrelated", Boolean.FALSE, true));
    }
}
