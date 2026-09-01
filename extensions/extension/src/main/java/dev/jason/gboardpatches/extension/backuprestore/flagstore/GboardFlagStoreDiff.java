package dev.jason.gboardpatches.extension.backuprestore.flagstore;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

/** Complete typed semantic diff for raw Gboard PB and SharedPreferences XML stores. */
public final class GboardFlagStoreDiff {
    private GboardFlagStoreDiff() {
    }

    public static Result compare(String canonicalFileName, byte[] current, byte[] replacement) {
        try {
            Map<String, TypedValue> before = decode(canonicalFileName, current);
            Map<String, TypedValue> after = decode(canonicalFileName, replacement);
            List<Difference> differences = new ArrayList<>();
            TreeMap<String, Boolean> keys = new TreeMap<>();
            before.keySet().forEach(key -> keys.put(key, Boolean.TRUE));
            after.keySet().forEach(key -> keys.put(key, Boolean.TRUE));
            for (String key : keys.keySet()) {
                TypedValue oldValue = before.get(key);
                TypedValue newValue = after.get(key);
                if (oldValue == null) {
                    differences.add(new Difference(key, Change.ADDED, null,
                            newValue.display));
                } else if (newValue == null) {
                    differences.add(new Difference(key, Change.REMOVED, oldValue.display,
                            null));
                } else if (!oldValue.equalsValue(newValue)) {
                    differences.add(new Difference(key, Change.CHANGED, oldValue.display,
                            newValue.display));
                }
            }
            return new Result(before.size(), after.size(), differences);
        } catch (Throwable failure) {
            if (failure instanceof IllegalArgumentException invalid) {
                throw invalid;
            }
            throw new IllegalArgumentException("Unable to decode complete flag store", failure);
        }
    }

    private static Map<String, TypedValue> decode(String fileName, byte[] bytes) throws Exception {
        byte[] safe = bytes == null ? new byte[0] : bytes;
        if (safe.length == 0) {
            return Collections.emptyMap();
        }
        if (GboardFlagStoreFiles.PROTO_FILE.equals(fileName)) {
            return decodeProto(safe);
        }
        return decodeXml(safe);
    }

    private static Map<String, TypedValue> decodeXml(byte[] bytes) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(false);
        Document document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        Element root = document.getDocumentElement();
        if (root == null || !"map".equals(root.getTagName())) {
            throw new IllegalArgumentException("SharedPreferences XML root must be <map>");
        }
        Map<String, TypedValue> values = new TreeMap<>();
        NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (!(node instanceof Element element)) {
                continue;
            }
            String name = element.getAttribute("name");
            if (name.isEmpty() || values.containsKey(name)) {
                throw new IllegalArgumentException("Invalid or duplicate XML preference key");
            }
            values.put(name, decodeXmlValue(element));
        }
        return values;
    }

    private static TypedValue decodeXmlValue(Element element) {
        String tag = element.getTagName();
        return switch (tag) {
            case "boolean" -> typed("boolean", canonicalBoolean(
                    requiredAttribute(element, "value")));
            case "int" -> typed("int", Integer.toString(Integer.parseInt(
                    requiredAttribute(element, "value"))));
            case "long" -> typed("long", Long.toString(Long.parseLong(
                    requiredAttribute(element, "value"))));
            case "float" -> typed("float", Float.toString(Float.parseFloat(
                    requiredAttribute(element, "value"))));
            case "string" -> typed("string", element.getTextContent());
            case "set" -> {
                TreeSet<String> items = new TreeSet<>();
                NodeList children = element.getChildNodes();
                for (int index = 0; index < children.getLength(); index++) {
                    Node node = children.item(index);
                    if (node instanceof Element child) {
                        if (!"string".equals(child.getTagName())) {
                            throw new IllegalArgumentException("XML set contains a non-string");
                        }
                        items.add(child.getTextContent());
                    }
                }
                List<String> canonicalItems = new ArrayList<>(items);
                yield typed("string_set", canonicalItems, canonicalItems.toString());
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported SharedPreferences XML value: " + tag);
        };
    }

    private static String canonicalBoolean(String value) {
        if (!"true".equals(value) && !"false".equals(value)) {
            throw new IllegalArgumentException("Invalid XML boolean value");
        }
        return value;
    }

    private static String requiredAttribute(Element element, String name) {
        if (!element.hasAttribute(name)) {
            throw new IllegalArgumentException("Missing XML attribute: " + name);
        }
        return element.getAttribute(name);
    }

    private static Map<String, TypedValue> decodeProto(byte[] bytes) {
        Map<String, TypedValue> values = new TreeMap<>();
        Cursor outer = new Cursor(bytes);
        boolean entrySeen = false;
        while (outer.hasRemaining()) {
            long tag = outer.varint();
            int field = field(tag);
            int wire = wire(tag);
            if (field == 1 && wire == 2) {
                entrySeen = true;
                decodeMapEntry(outer.lengthDelimited(), values);
            } else {
                outer.skip(wire);
            }
        }
        if (!entrySeen) {
            throw new IllegalArgumentException("PB does not contain a Gboard flag map entry");
        }
        return values;
    }

    private static void decodeMapEntry(byte[] bytes, Map<String, TypedValue> values) {
        Cursor entry = new Cursor(bytes);
        String name = null;
        byte[] layers = null;
        while (entry.hasRemaining()) {
            long tag = entry.varint();
            int field = field(tag);
            int wire = wire(tag);
            if (field == 1 && wire == 2) {
                name = new String(entry.lengthDelimited(), StandardCharsets.UTF_8);
            } else if (field == 2 && wire == 2) {
                layers = entry.lengthDelimited();
            } else {
                entry.skip(wire);
            }
        }
        if (name == null || name.isEmpty() || layers == null) {
            throw new IllegalArgumentException("PB flag map entry is incomplete");
        }
        decodeLayers(name, layers, values);
    }

    private static void decodeLayers(String name, byte[] bytes,
            Map<String, TypedValue> values) {
        Cursor layers = new Cursor(bytes);
        while (layers.hasRemaining()) {
            long tag = layers.varint();
            int field = field(tag);
            int wire = wire(tag);
            if ((field == 1 || field == 2) && wire == 2) {
                String layer = field == 1 ? "PHENOTYPE" : "OVERRIDE";
                String key = name + " [" + layer + "]";
                TypedValue previous = values.put(key,
                        decodeTypedValue(layers.lengthDelimited()));
                if (previous != null) {
                    throw new IllegalArgumentException("Duplicate PB flag layer: " + key);
                }
            } else {
                layers.skip(wire);
            }
        }
    }

    private static TypedValue decodeTypedValue(byte[] bytes) {
        Cursor value = new Cursor(bytes);
        TypedValue decoded = null;
        while (value.hasRemaining()) {
            long tag = value.varint();
            int field = field(tag);
            int wire = wire(tag);
            if (field == 1 && wire == 0) {
                decoded = typed("boolean", value.varint() == 0 ? "false" : "true");
            } else if (field == 2 && wire == 1) {
                decoded = typed("long", Long.toString(value.fixed64()));
            } else if (field == 3 && wire == 1) {
                decoded = typed("double",
                        Double.toString(Double.longBitsToDouble(value.fixed64())));
            } else if (field == 4 && wire == 2) {
                decoded = typed("string",
                        new String(value.lengthDelimited(), StandardCharsets.UTF_8));
            } else if (field == 5 && wire == 2) {
                decoded = typed("bytes",
                        Base64.getEncoder().encodeToString(value.lengthDelimited()));
            } else {
                value.skip(wire);
            }
        }
        if (decoded == null) {
            throw new IllegalArgumentException("PB typed value has no supported oneof value");
        }
        return decoded;
    }

    private static TypedValue typed(String type, String display) {
        return typed(type, display, display);
    }

    private static TypedValue typed(String type, Object value, String display) {
        return new TypedValue(type, value, display);
    }

    private static int field(long tag) {
        int field = (int) (tag >>> 3);
        if (field <= 0) {
            throw new IllegalArgumentException("PB contains an invalid field number");
        }
        return field;
    }

    private static int wire(long tag) {
        return (int) (tag & 7L);
    }

    public enum Change { ADDED, REMOVED, CHANGED }

    public static final class Difference {
        private final String key;
        private final Change change;
        private final String oldValue;
        private final String newValue;

        Difference(String key, Change change, String oldValue, String newValue) {
            this.key = key;
            this.change = change;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public String getKey() { return key; }
        public Change getChange() { return change; }
        public String getOldValue() { return oldValue; }
        public String getNewValue() { return newValue; }
    }

    public static final class Result {
        private final int currentValueCount;
        private final int replacementValueCount;
        private final List<Difference> differences;

        Result(int currentValueCount, int replacementValueCount,
                List<Difference> differences) {
            this.currentValueCount = currentValueCount;
            this.replacementValueCount = replacementValueCount;
            this.differences = Collections.unmodifiableList(new ArrayList<>(differences));
        }

        public int getCurrentValueCount() { return currentValueCount; }
        public int getReplacementValueCount() { return replacementValueCount; }
        public List<Difference> getDifferences() { return differences; }

        public int count(Change change) {
            int count = 0;
            for (Difference difference : differences) {
                if (difference.change == change) {
                    count++;
                }
            }
            return count;
        }
    }

    private static final class TypedValue {
        final String type;
        final Object value;
        final String display;

        TypedValue(String type, Object value, String display) {
            this.type = type;
            this.value = value;
            this.display = display;
        }

        boolean equalsValue(TypedValue other) {
            return other != null && type.equals(other.type) && value.equals(other.value);
        }
    }

    private static final class Cursor {
        final byte[] data;
        int offset;

        Cursor(byte[] data) {
            this.data = data;
        }

        boolean hasRemaining() { return offset < data.length; }

        long varint() {
            long result = 0;
            for (int shift = 0; shift < 64; shift += 7) {
                require(1);
                int next = data[offset++] & 0xff;
                result |= (long) (next & 0x7f) << shift;
                if ((next & 0x80) == 0) {
                    return result;
                }
            }
            throw new IllegalArgumentException("PB varint is malformed");
        }

        long fixed64() {
            require(8);
            long result = ByteBuffer.wrap(data, offset, 8)
                    .order(ByteOrder.LITTLE_ENDIAN).getLong();
            offset += 8;
            return result;
        }

        byte[] lengthDelimited() {
            long length = varint();
            if (length < 0 || length > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("PB length is invalid");
            }
            require((int) length);
            byte[] result = java.util.Arrays.copyOfRange(data, offset, offset + (int) length);
            offset += (int) length;
            return result;
        }

        void skip(int wire) {
            switch (wire) {
                case 0 -> varint();
                case 1 -> advance(8);
                case 2 -> lengthDelimited();
                case 5 -> advance(4);
                default -> throw new IllegalArgumentException(
                        "Unsupported PB wire type: " + wire);
            }
        }

        void advance(int count) {
            require(count);
            offset += count;
        }

        void require(int count) {
            if (count < 0 || (long) offset + count > data.length) {
                throw new IllegalArgumentException("PB is truncated");
            }
        }
    }
}
