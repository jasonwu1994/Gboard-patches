package dev.jason.gboardpatches.patches.gboard.features.calculator

import app.morphe.patcher.patch.resourcePatch
import dev.jason.gboardpatches.patches.shared.Constants.COMPATIBILITY_GBOARD
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Finds Gboard's copy icon for the calculator's long-press bubble. Resource names are obfuscated
 * and ids change between versions, so the icon is found by its Material "content_copy" artwork.
 */
internal val gboardCalculatorCopyIconPatch = resourcePatch(
    description = "找出 Gboard 內建的複製圖示，供 Simple Calculator 長按選單使用。",
) {
    compatibleWith(COMPATIBILITY_GBOARD)

    execute {
        val drawableIds = publicIds(this["res/values/public.xml"], "drawable")
        val tints = mutableMapOf<Int, String>()
        val copyIcons = mutableListOf<Int>()
        this["res/drawable"].listFiles().orEmpty().filter { it.extension == "xml" }.forEach { file ->
            val id = drawableIds[file.nameWithoutExtension] ?: return@forEach
            val text = file.readText()
            TINT.find(text)?.let { tints[id] = it.groupValues[1] }
            if (COPY_ICON_PATH in text) copyIcons += id
        }
        calculatorDrawables = CalculatorDrawables(copyIcons.sorted(), tints)
    }
}

internal class CalculatorDrawables(
    private val copyIcons: List<Int>,
    private val tints: Map<Int, String>,
) {
    /** The copy icon themed like [removeIcon], or null if Gboard has none. */
    fun copyIconLike(removeIcon: Int): Int? {
        val tint = tints[removeIcon] ?: return null
        return copyIcons.firstOrNull { tints[it] == tint }
    }
}

internal var calculatorDrawables: CalculatorDrawables? = null

private fun publicIds(publicXml: java.io.File, type: String): Map<String, Int> {
    val entries = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(publicXml)
        .getElementsByTagName("public")
    return (0 until entries.length).map { entries.item(it) as Element }
        .filter { it.getAttribute("type") == type }
        .associate { it.getAttribute("name") to Integer.decode(it.getAttribute("id")) }
}

private val TINT = Regex("""android:tint="([^"]+)"""")

// Start of Material Symbols "content_copy".
private const val COPY_ICON_PATH = "M360,720q-33,0-56.5-23.5T280,640V160"
