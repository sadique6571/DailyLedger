package com.sadique.dailyledger.ui

import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.*
import org.junit.Test

class CategoryChartColorsTest {
    @Test fun thousandsOfCategoriesNeverReuseAnRgbColor() {
        val labels = (0 until 10_000).map { "Custom item $it" }
        val colors = CategoryChartColors.colors(labels)
        assertEquals(labels.size, colors.values.map { it.toArgb() }.toSet().size)
        assertTrue(colors.values.all { it.alpha == 1f })
    }

    @Test fun sortingAndSpendingChangesDoNotChangeCategoryColors() {
        val labels = listOf("Peanuts", "Curtains", "Milk", "Fuel", "School fees", "Tea & coffee")
        assertEquals(CategoryChartColors.colors(labels), CategoryChartColors.colors(labels.reversed()))
    }

    @Test fun punctuationAndCaseVariantsRemainDistinctSlices() {
        val labels = listOf("A-B", "A B", "a b", "AB", "A&B")
        assertEquals(labels.size, CategoryChartColors.colors(labels).values.toSet().size)
    }
}
