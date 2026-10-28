package com.sadique.dailyledger.ai

import com.sadique.dailyledger.data.ExpandedItemCategories
import com.sadique.dailyledger.data.ItemArtwork
import com.sadique.dailyledger.data.ItemCategories
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

class ExpandedItemsTest {
    @Test fun regionalNamesChooseTheSpecificItemAndMatchingArtwork() {
        val examples = mapOf(
            "moongphali" to "Peanuts", "مونگ پھلی" to "Peanuts", "parday" to "Curtains", "پردے" to "Curtains",
            "badam" to "Almonds", "akhrot" to "Walnuts", "kaju" to "Cashews", "pista" to "Pistachios",
            "toothbrush" to "Toothbrush", "ٹوتھ پیسٹ" to "Toothpaste", "pankha" to "Ceiling fan",
            "fridge" to "Refrigerator", "gadda" to "Mattress", "balti" to "Bucket", "chabi" to "Keys",
            "shakarkandi" to "Sweet potatoes", "gachak" to "Peanut brittle", "pressure cooker" to "Pressure cooker")
        examples.forEach { (alias, label) ->
            assertEquals(alias, label, CategoryRules.infer("EXPENSE", "$alias 250"))
            assertEquals(alias, ItemArtwork.key(label), ItemArtwork.key(alias))
        }
        assertEquals("1f95c", ItemArtwork.key("Peanuts"))
        assertEquals("curtains", ItemArtwork.key("Parday"))
        assertNotEquals(ItemArtwork.key("Peanuts"), ItemArtwork.key("Moong dal"))
    }

    @Test fun everyNewItemNameIsRecognizedByBothCategoryValidationPaths() {
        ExpandedItemCategories.entries.forEach { item ->
            assertEquals(item.label, item.label, CategoryRules.infer("EXPENSE", item.label))
            assertEquals(item.label, item.label, CategoryRules.normalize("EXPENSE", item.label, item.label))
        }
    }

    @Test fun specificItemsReplaceBroadOnlineBucketsWithoutChangingMoneyOrDate() {
        listOf(Triple("Parday", "Furniture", "Curtains"), Triple("Badam", "Dry fruits", "Almonds"),
            Triple("Toothbrush", "Oral care", "Toothbrush")).forEach { (note, broad, exact) ->
            val draft = com.sadique.dailyledger.data.TransactionDraft("EXPENSE", 12345, broad, note, "2026-10-28")
            val result = CategoryRules.normalizeDraft(draft)
            assertEquals(exact, result.category)
            assertEquals(draft.amountMinor, result.amountMinor)
            assertEquals(draft.date, result.date)
            assertEquals(draft.note, result.note)
        }
    }

    @Test fun offlineEntryParserUsesTheExpandedVocabulary() {
        val result = OfflineMiniAi.drafts("parday 3500")!!
        assertEquals("Curtains", result.entries.single().category)
        assertEquals(350000L, result.entries.single().amountMinor)
        assertEquals("Other income", CategoryRules.infer("INCOME", "toothbrush"))
    }

    @Test fun everyCatalogItemHasRealDecodableOfflineArtwork() {
        assertTrue(ExpandedItemCategories.entries.size >= 200)
        assertEquals(ItemCategories.entries.size, ItemCategories.entries.map { it.type to it.label }.toSet().size)
        val labels = ItemCategories.entries.map { it.label } + Regex("\"label\"\\s*:\\s*\"([^\"]+)\"")
            .findAll(File("src/main/assets/categories.json").readText()).map { it.groupValues[1] }.toList()
        labels.distinct().forEach { label ->
            val path = ItemArtwork.asset(label)
            assertNotNull("No art assigned to $label", path)
            val image = ImageIO.read(File("src/main/assets/$path"))
            assertNotNull("Invalid PNG for $label", image)
            assertEquals(label, 72, image.width)
            assertEquals(label, 72, image.height)
        }
    }
}
