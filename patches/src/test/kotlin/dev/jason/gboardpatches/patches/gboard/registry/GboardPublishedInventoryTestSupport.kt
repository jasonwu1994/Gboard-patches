package dev.jason.gboardpatches.patches.gboard.registry

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals

internal fun generatedPublishedInventory(version: String = "test-version"): JsonObject =
    JsonParser.parseString(GboardPublishedPatchCatalog.publishedInventory(version)).asJsonObject

internal fun generatedPublishedPatches(): List<JsonObject> =
    generatedPublishedInventory().getAsJsonArray("patches").map { element -> element.asJsonObject }

internal fun assertMatchesPublishedRegistrationCount(actualCount: Int) {
    assertEquals(GboardPublishedPatchCatalog.morpheRegistrations.size, actualCount)
}
