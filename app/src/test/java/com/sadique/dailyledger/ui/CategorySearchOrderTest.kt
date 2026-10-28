package com.sadique.dailyledger.ui

import com.sadique.dailyledger.data.ItemCategories
import com.sadique.dailyledger.ui.screens.CategoryOptions
import org.junit.Assert.*
import org.junit.Test

class CategorySearchOrderTest {
    private val options = CategoryOptions.build(ItemCategories.entries, emptyList(), { it }).getValue("EXPENSE")

    @Test fun exactMilkStaysFirstEvenWithTheExpandedDairyCatalog() {
        val matches = options.search("Milk").map { it.label }
        assertEquals("Milk", matches.first())
        assertTrue(matches.contains("Milk powder"))
        assertTrue(matches.contains("Condensed milk"))
    }

    @Test fun exactRomanUrduAliasBeatsRelatedItems() {
        assertEquals("Onions", options.search("piyaz").first().label)
        assertEquals("Packaged milk", options.search("milk pack").first().label)
        assertEquals("Curtains", options.search("parday").first().label)
    }

    @Test fun searchingKeepsAllMatchesAndBlankSearchKeepsCatalogOrder() {
        assertEquals(options.indexed.map { it.first }, options.search(""))
        val matches = options.search("milk")
        val originalMatches = options.indexed.filter { "doodh" in it.second }.map { it.first }
        assertEquals(originalMatches.toSet(), matches.toSet())
        assertEquals(matches.size, matches.toSet().size)
    }
}
