package com.sadique.dailyledger.ui

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext

/** Icons a user can assign to a category, keyed by a stable storage name. */
val CategoryOverrideIcons: Map<String, ImageVector> = linkedMapOf(
    "restaurant" to Icons.Rounded.Restaurant,
    "cart" to Icons.Rounded.ShoppingCart,
    "bag" to Icons.Rounded.ShoppingBag,
    "fuel" to Icons.Rounded.LocalGasStation,
    "car" to Icons.Rounded.DirectionsCar,
    "home" to Icons.Rounded.Home,
    "bolt" to Icons.Rounded.Bolt,
    "wifi" to Icons.Rounded.Wifi,
    "health" to Icons.Rounded.Medication,
    "hospital" to Icons.Rounded.LocalHospital,
    "school" to Icons.Rounded.School,
    "work" to Icons.Rounded.Work,
    "payments" to Icons.Rounded.Payments,
    "bank" to Icons.Rounded.AccountBalance,
    "heart" to Icons.Rounded.Favorite,
    "pets" to Icons.Rounded.Pets,
    "games" to Icons.Rounded.SportsEsports,
    "other" to Icons.Rounded.Category,
)

/** Colors a user can assign to a category. */
val CategoryOverrideColors: List<Color> = listOf(
    Color(0xFF0EA5A4), Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFDB2777), Color(0xFFEF4444),
    Color(0xFFF59E0B), Color(0xFF84CC16), Color(0xFF15803D), Color(0xFF0EA5E9), Color(0xFF64748B),
)

/**
 * User-chosen icon/color per category label, kept in local preferences
 * (presentation only — never part of ledger data or backups).
 */
object CategoryVisualOverrides {
    private const val STORE = "category_visual_overrides_v1"

    /** Bumped on every change so composables reading [rememberCategoryVisual] redraw. */
    var version by mutableStateOf(0)
        private set

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    private fun key(category: String) =
        "visual_" + category.trim().lowercase(java.util.Locale.ROOT)

    /** Returns (iconName, color) for a category, or null when no override is set. */
    fun get(context: Context, category: String): Pair<String, Color>? {
        val raw = prefs(context).getString(key(category), null) ?: return null
        val parts = raw.split("|")
        if (parts.size != 2) return null
        if (parts[0] !in CategoryOverrideIcons) return null
        val argb = parts[1].toULongOrNull() ?: return null
        return parts[0] to Color(argb)
    }

    fun set(context: Context, category: String, iconName: String, color: Color) {
        prefs(context).edit().putString(key(category), "$iconName|${color.value}").apply()
        version++
    }

    fun clear(context: Context, category: String) {
        prefs(context).edit().remove(key(category)).apply()
        version++
    }
}

/** The visual for a category, honoring the user's override when one is set. */
@Composable
fun rememberCategoryVisual(category: String, type: String = "EXPENSE"): CategoryVisual {
    val context = LocalContext.current
    val currentVersion = CategoryVisualOverrides.version
    val override = remember(category, currentVersion) { CategoryVisualOverrides.get(context, category) }
    val base = categoryVisual(category, type)
    return if (override != null) {
        CategoryVisual(CategoryOverrideIcons[override.first] ?: base.icon, override.second)
    } else base
}
