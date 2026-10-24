package dev.jason.gboardpatches.extension.zhuyindictionary;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import com.google.android.apps.inputmethod.libs.zhuyin.ZhuyinHmmDecodeProcessor;
import java.io.File;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, sdk = 35)
public class GboardZhuyinShortcutCandidatesTest {
    @Test
    public void dictionaryShortcutAppearsBeforeStockAndCanBeCommitted() throws Exception {
        Object listener = listener();
        GboardZhuyinShortcutCandidates.beginImport(listener);
        GboardZhuyinShortcutCandidates.recordEntry(1, null, "ㄉ", "訂飲料");
        GboardZhuyinShortcutCandidates.finishImport(listener);
        ZhuyinHmmDecodeProcessor processor =
                new com.google.android.apps.inputmethod.latin.hmm.LatinTaiwanZhuyinDecodeProcessor();
        Iterator<?> candidates = GboardZhuyinShortcutCandidates.candidates(processor);
        assertNotNull(org.robolectric.shadows.ShadowLog.getLogsForTag("GboardZhuyinShortcuts").toString(), candidates);
        Object custom = candidates.next();
        assertEquals("訂飲料", custom.getClass().getField("a").get(custom));
        assertEquals("stock candidate", candidates.next());
        assertFalse(candidates.hasNext());
        assertTrue(GboardZhuyinShortcutCandidates.commit(processor, custom));
        assertEquals("訂飲料", processor.committed);
        assertEquals(1, processor.cursorPosition);
        assertTrue(processor.reset);
        assertEquals("", processor.m.composing);
        assertFalse(GboardZhuyinShortcutCandidates.commit(processor, new Object()));
    }

    @Test
    public void staleCandidateIsConsumedWithoutCommittingOrNativeSelection() throws Exception {
        Object listener = listener();
        GboardZhuyinShortcutCandidates.beginImport(listener);
        GboardZhuyinShortcutCandidates.recordEntry(1, "zh_TW", "ㄉ", "訂飲料");
        GboardZhuyinShortcutCandidates.finishImport(listener);
        ZhuyinHmmDecodeProcessor processor = new ZhuyinHmmDecodeProcessor();
        Object candidate = GboardZhuyinShortcutCandidates.candidates(processor).next();
        processor.m.composing = "ㄅ";
        assertTrue(GboardZhuyinShortcutCandidates.commit(processor, candidate));
        assertNull(processor.committed);
        assertNull(GboardZhuyinShortcutCandidates.candidates(processor));
    }

    @Test
    public void deletingEntriesPublishesAnEmptyDictionary() throws Exception {
        Object listener = listener();
        GboardZhuyinShortcutCandidates.beginImport(listener);
        GboardZhuyinShortcutCandidates.recordEntry(1, "zh-TW", "ㄉ", "訂飲料");
        GboardZhuyinShortcutCandidates.finishImport(listener);
        GboardZhuyinShortcutCandidates.beginImport(listener);
        GboardZhuyinShortcutCandidates.finishImport(listener);
        assertNull(GboardZhuyinShortcutCandidates.candidates(new ZhuyinHmmDecodeProcessor()));
    }

    @Test
    public void storePublishesOnlyCompletedImportsAndFiltersUnrelatedEntries() {
        ZhuyinShortcutStore store = new ZhuyinShortcutStore();
        store.begin();
        store.record(1, null, "ㄉ", "訂飲料");
        store.record(1, "zh_TW", "ㄉ", "第二筆");
        store.record(1, "zh_TW", "ㄉ", "第二筆");
        store.record(0, null, "ㄉ", "英文引擎");
        store.record(1, "ja_JP", "ㄉ", "其他語言");
        store.record(1, null, "d", "英文捷徑");
        assertTrue(store.find("ㄉ").isEmpty());
        store.finish();
        assertEquals(Arrays.asList("訂飲料", "第二筆"), store.find("ㄉ"));
        assertTrue(store.find("d").isEmpty());
    }

    @Test
    public void candidateIteratorDoesNotConsumeOrReplaceStockEntries() {
        Iterator<?> iterator = GboardZhuyinShortcutCandidates.prepend(
                Collections.singletonList("custom"), Arrays.asList("one", "two").iterator());
        assertEquals("custom", iterator.next());
        assertEquals("one", iterator.next());
        assertEquals("two", iterator.next());
        assertFalse(iterator.hasNext());
    }

    @Test
    public void completionAcceptsEngineAfterStockMethodReusesReceiverRegister() throws Exception {
        Object engine = Class.forName("kkb").getDeclaredConstructor().newInstance();
        GboardZhuyinShortcutCandidates.beginImport(new ImportListener(engine));
        GboardZhuyinShortcutCandidates.recordEntry(1, null, "ㄉ", "訂飲料");
        GboardZhuyinShortcutCandidates.finishImport(engine);
        Iterator<?> result = GboardZhuyinShortcutCandidates.candidates(
                new com.google.android.apps.inputmethod.latin.hmm.LatinTaiwanZhuyinDecodeProcessor());
        assertNotNull(result);
        Object candidate = result.next();
        assertEquals("訂飲料", candidate.getClass().getField("a").get(candidate));
        assertEquals(Class.forName("ojs").getField("d").get(null), candidate.getClass().getField("e").get(candidate));
    }

    @Test
    public void loadsShortcutsDirectlyFromPersonalDictionaryDatabase() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        File dbFile = context.getDatabasePath("PersonalDictionary.db");
        if (dbFile.exists()) dbFile.delete();
        dbFile.getParentFile().mkdirs();
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        db.execSQL("CREATE TABLE entry(_id INTEGER PRIMARY KEY, word TEXT, shortcut TEXT, locale TEXT)");
        db.execSQL("INSERT INTO entry(word, shortcut, locale) VALUES('訂飲料', 'ㄉ', 'zh_TW')");
        db.execSQL("INSERT INTO entry(word, shortcut, locale) VALUES('第二筆', 'ㄉ', 'zh_TW')");
        db.execSQL("INSERT INTO entry(word, shortcut, locale) VALUES('english', 'e', 'en_US')");
        db.close();

        // Clear in-memory store so it must load from db
        Object listener = listener();
        GboardZhuyinShortcutCandidates.beginImport(listener);
        GboardZhuyinShortcutCandidates.finishImport(listener);

        ZhuyinHmmDecodeProcessor processor = new ZhuyinHmmDecodeProcessor();
        processor.u = context;
        processor.m.composing = "ㄉ";

        Iterator<?> candidates = GboardZhuyinShortcutCandidates.candidates(processor);
        assertNotNull(candidates);
        Object custom1 = candidates.next();
        assertEquals("訂飲料", custom1.getClass().getField("a").get(custom1));
        Object custom2 = candidates.next();
        assertEquals("第二筆", custom2.getClass().getField("a").get(custom2));
        assertEquals("stock candidate", candidates.next());
        assertFalse(candidates.hasNext());
    }
    private static Object listener() throws Exception {
        return new ImportListener(Class.forName("kkb").getDeclaredConstructor().newInstance());
    }

    private static final class ImportListener {
        private final Object a;
        ImportListener(Object owner) { a = owner; }
    }
}
