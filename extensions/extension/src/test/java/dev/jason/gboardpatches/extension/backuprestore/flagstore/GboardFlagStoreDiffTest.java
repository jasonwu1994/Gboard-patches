package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

public final class GboardFlagStoreDiffTest {
    @Test
    public void xmlDiffReportsChangedAddedAndRemovedTypedValues() {
        byte[] current = xml("<boolean name='a' value='true'/>"
                + "<int name='removed' value='7'/>");
        byte[] replacement = xml("<boolean name='a' value='false'/>"
                + "<string name='added'>hello</string>");

        GboardFlagStoreDiff.Result result = GboardFlagStoreDiff.compare(
                GboardFlagStoreFiles.LEGACY_OVERRIDE_FILE, current, replacement);

        assertEquals(1, result.count(GboardFlagStoreDiff.Change.CHANGED));
        assertEquals(1, result.count(GboardFlagStoreDiff.Change.ADDED));
        assertEquals(1, result.count(GboardFlagStoreDiff.Change.REMOVED));
        GboardFlagStoreDiff.Difference changed = result.getDifferences().get(0);
        assertEquals("a", changed.getKey());
        assertEquals("true", changed.getOldValue());
        assertEquals("false", changed.getNewValue());
    }

    @Test
    public void xmlNumericValuesAreComparedByStoredType() {
        byte[] current = xml("<int name='int' value='01'/>"
                + "<long name='long' value='+2'/>"
                + "<float name='float' value='1.00'/>");
        byte[] replacement = xml("<int name='int' value='1'/>"
                + "<long name='long' value='2'/>"
                + "<float name='float' value='1.0'/>");

        GboardFlagStoreDiff.Result result = GboardFlagStoreDiff.compare(
                GboardFlagStoreFiles.LEGACY_OVERRIDE_FILE, current, replacement);

        assertTrue(result.getDifferences().isEmpty());
    }

    @Test
    public void pbDiffDecodesLayerAndBooleanTransition() {
        GboardFlagStoreDiff.Result result = GboardFlagStoreDiff.compare(
                GboardFlagStoreFiles.PROTO_FILE,
                protoBoolean("enable_example", true, false),
                protoBoolean("enable_example", false, false));

        assertEquals(1, result.getDifferences().size());
        GboardFlagStoreDiff.Difference difference = result.getDifferences().get(0);
        assertEquals("enable_example [PHENOTYPE]", difference.getKey());
        assertEquals("true", difference.getOldValue());
        assertEquals("false", difference.getNewValue());
    }

    @Test
    public void malformedPbIsRejectedInsteadOfShowingAPartialDiff() {
        try {
            GboardFlagStoreDiff.compare(GboardFlagStoreFiles.PROTO_FILE,
                    protoBoolean("flag", true, false), new byte[] {0x0a, 0x7f});
            fail("Expected malformed replacement to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("truncated"));
        }
    }

    private static byte[] xml(String entries) {
        return ("<?xml version='1.0' encoding='utf-8'?><map>" + entries + "</map>")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] protoBoolean(String name, boolean value, boolean override) {
        byte[] typed = fieldVarint(1, value ? 1 : 0);
        byte[] layers = fieldBytes(override ? 2 : 1, typed);
        byte[] entry = concat(fieldBytes(1, name.getBytes(StandardCharsets.UTF_8)),
                fieldBytes(2, layers));
        return fieldBytes(1, entry);
    }

    private static byte[] fieldVarint(int field, long value) {
        return concat(varint(field << 3), varint(value));
    }

    private static byte[] fieldBytes(int field, byte[] value) {
        return concat(varint((field << 3) | 2), varint(value.length), value);
    }

    private static byte[] varint(long value) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        do {
            int next = (int) (value & 0x7f);
            value >>>= 7;
            output.write(value == 0 ? next : next | 0x80);
        } while (value != 0);
        return output.toByteArray();
    }

    private static byte[] concat(byte[]... values) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (byte[] value : values) {
            output.write(value, 0, value.length);
        }
        return output.toByteArray();
    }
}
