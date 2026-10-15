package com.sadique.dailyledger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat

object LedgerColors {
    val Navy = Color(0xFF0A1744)
    val Muted = Color(0xFF567094)
    val Teal = Color(0xFF0097B5)
    val Blue = Color(0xFF176CFF)
    val Green = Color(0xFF00AC75)
    val Red = Color(0xFFFF3D52)
    val Amber = Color(0xFFFFA600)
    val Purple = Color(0xFF9651F4)
}

@Composable fun ledgerIsDark() = MaterialTheme.colorScheme.background.luminance() < 0.2f

@Composable
fun LedgerBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val dark = ledgerIsDark()
    Box(modifier.background(Brush.verticalGradient(if (dark) listOf(Color(0xFF102C43), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background)
        else listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f), MaterialTheme.colorScheme.background, MaterialTheme.colorScheme.background), endY = 1500f)), content = content)
}

@Composable
fun LedgerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val display = LocalDisplayPreferences.current!!
    Surface(modifier, shape = RoundedCornerShape(if (display.compact) 14.dp else 22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 3.dp, border = androidx.compose.foundation.BorderStroke(1.dp, if (ledgerIsDark()) Color.White.copy(alpha = 0.06f) else Color.White)) {
        Column(Modifier.padding(display.cardPadding.dp), content = content)
    }
}

@Composable
fun LedgerLogo(size: Dp = 39.dp) {
    Box(Modifier.size(size).shadow(4.dp, RoundedCornerShape(10.dp)).background(
        Brush.linearGradient(listOf(Color(0xFF65FFD4), Color(0xFF00B784), Color(0xFF008A61))), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
        Row(Modifier.height(size * 0.55f), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
            listOf(0.48f, 0.78f, 1f, 0.62f).forEach { height ->
                Box(Modifier.width(size * 0.095f).fillMaxHeight(height).background(Color.White, RoundedCornerShape(2.dp)))
            }
        }
    }
}

@Composable
fun LedgerBrandHeader(subtitle: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        LedgerLogo()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("Daily Ledger", fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun GlowIcon(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 42.dp, description: String? = null) {
    val dark = ledgerIsDark()
    val display = LocalDisplayPreferences.current!!
    val scaledSize = size * display.iconMultiplier
    Box(modifier.size(scaledSize).background(Brush.linearGradient(listOf(color.copy(alpha = if (dark) 0.28f else 0.22f),
        color.copy(alpha = if (dark) 0.10f else 0.06f))), CircleShape).border(1.dp, Color.White.copy(alpha = if (dark) 0.05f else 0.7f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, description, Modifier.size(scaledSize * 0.56f), tint = color)
    }
}

@Composable
fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
                   icon: ImageVector? = Icons.AutoMirrored.Rounded.ArrowForward) {
    val gradient = if (enabled) listOf(Color(0xFF0589BC), Color(0xFF007DA1), Color(0xFF00BE8F))
        else listOf(Color(0xFF809BAA), Color(0xFF809BAA))
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 50.dp).background(Brush.linearGradient(gradient), RoundedCornerShape(17.dp)),
        shape = RoundedCornerShape(17.dp), contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, disabledContainerColor = Color.Transparent,
            contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.8f))) {
        Text(text, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        icon?.let { Icon(it, null, Modifier.size(22.dp)) }
    }
}

data class CategoryVisual(val icon: ImageVector, val color: Color)

fun categoryVisual(category: String, type: String = "EXPENSE"): CategoryVisual {
    val name = category.lowercase(java.util.Locale.ROOT).replace("&", "and")
    val itemGroup = com.sadique.dailyledger.data.ItemCategories.entries.firstOrNull { it.label.equals(category, true) }?.group
    when (itemGroup) {
        "Lentils & grains" -> return CategoryVisual(Icons.Rounded.Grain, LedgerColors.Amber)
        "Vegetables", "Fruit" -> return CategoryVisual(Icons.Rounded.Spa, LedgerColors.Green)
        "Meat & fish", "Breakfast & dairy" -> return CategoryVisual(Icons.Rounded.Restaurant, LedgerColors.Amber)
        "Cleaning items" -> return CategoryVisual(Icons.Rounded.CleaningServices, LedgerColors.Teal)
        "School items" -> return CategoryVisual(Icons.Rounded.School, LedgerColors.Purple)
        "Personal items" -> return CategoryVisual(Icons.Rounded.ShoppingBag, LedgerColors.Purple)
        "Pets" -> return CategoryVisual(Icons.Rounded.Pets, LedgerColors.Amber)
    }
    return when {
        listOf("other income", "other expense", "other").any { name == it } ->
            CategoryVisual(Icons.Rounded.Category, Color(0xFF7C92AE))
        listOf("salary", "pension", "overtime", "commission", "income", "refund", "bonus", "gift received").any { it in name } ->
            CategoryVisual(Icons.Rounded.Payments, LedgerColors.Green)
        listOf("fuel", "petrol", "diesel", "gas cylinder").any { it in name } ->
            CategoryVisual(Icons.Rounded.LocalGasStation, LedgerColors.Red)
        listOf("transport", "vehicle", "car", "bus", "taxi", "bike", "travel", "parking", "toll", "hotel", "holiday").any { it in name } ->
            CategoryVisual(Icons.Rounded.DirectionsCar, LedgerColors.Blue)
        listOf("restaurant", "dining", "food", "snack", "tea", "coffee", "beverage", "meal", "bakery", "bread", "meat", "chicken", "fish", "egg", "kitchen", "utensil").any { it in name } ->
            CategoryVisual(Icons.Rounded.Restaurant, LedgerColors.Amber)
        listOf("grocery", "grocer", "vegetable", "fruit", "rice", "flour", "lentil", "daal", "dairy", "milk", "oil", "spice", "sugar", "water").any { it in name } ->
            CategoryVisual(Icons.Rounded.ShoppingCart, LedgerColors.Green)
        listOf("health", "medical", "medicine", "doctor", "hospital", "lab", "dental", "eye", "glasses", "insurance", "elder care").any { it in name } ->
            CategoryVisual(Icons.Rounded.Favorite, LedgerColors.Red)
        listOf("school", "education", "book", "tuition", "college", "university", "course", "stationery", "exam", "admission", "uniform", "project").any { it in name } ->
            CategoryVisual(Icons.Rounded.School, LedgerColors.Purple)
        listOf("shopping", "clothes", "clothing", "shoes", "gift", "cosmetic", "skincare", "haircut", "personal allowance", "spouse", "wife").any { it in name } ->
            CategoryVisual(Icons.Rounded.ShoppingBag, LedgerColors.Purple)
        listOf("cleaning", "dishwash", "household", "soap", "shampoo", "hygiene", "toiletries", "oral care", "laundry", "home supplies", "bedding").any { it in name } ->
            CategoryVisual(Icons.Rounded.CleaningServices, LedgerColors.Teal)
        listOf("baby", "diaper", "child", "children", "toy", "pocket money", "childcare", "family support").any { it in name } ->
            CategoryVisual(Icons.Rounded.ChildCare, LedgerColors.Purple)
        listOf("saving", "wallet", "bank", "tax", "document", "postage", "subscription", "internet", "mobile", "phone", "electricity", "bill", "rent").any { it in name } ->
            CategoryVisual(Icons.Rounded.AccountBalanceWallet, LedgerColors.Blue)
        listOf("home", "rent", "repair", "maintenance", "furniture", "appliance", "domestic help", "security", "house cleaning").any { it in name } ->
            CategoryVisual(Icons.Rounded.Home, LedgerColors.Teal)
        listOf("pet", "veterinary").any { it in name } ->
            CategoryVisual(Icons.Rounded.Pets, LedgerColors.Amber)
        listOf("zakat", "sadqa", "donation", "religious", "eid", "festival", "wedding", "event").any { it in name } ->
            CategoryVisual(Icons.Rounded.VolunteerActivism, LedgerColors.Green)
        listOf("business", "office", "freelance", "agriculture").any { it in name } ->
            CategoryVisual(Icons.Rounded.Work, LedgerColors.Blue)
        listOf("emergency", "insurance").any { it in name } ->
            CategoryVisual(Icons.Rounded.Warning, LedgerColors.Red)
        else -> CategoryVisual(Icons.Rounded.LocalOffer, Color(0xFF7C92AE))
    }
}

/** Compact PKR presentation without losing paisa when they are present. */
fun displayMoney(minor: Long): String = "Rs " + DecimalFormat(if (minor % 100L == 0L) "#,##0" else "#,##0.00", java.text.DecimalFormatSymbols(java.util.Locale.US)).format(java.math.BigDecimal.valueOf(minor, 2))

@Composable
fun WalletArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val factor = size.width / 120f
        withTransform({ scale(factor, factor, Offset.Zero) }) {
            drawOval(Color(0xFF001E3B).copy(alpha = 0.24f), Offset(21f, 90f), Size(83f, 18f))
            withTransform({ rotate(-18f, Offset(64f, 48f)) }) {
                drawRoundRect(Brush.linearGradient(listOf(Color(0xFF00EAB0), Color(0xFF008D71))), Offset(35f, 20f), Size(54f, 65f), CornerRadius(6f))
                drawRoundRect(Color(0xFFADFFCC), Offset(39f, 25f), Size(46f, 54f), CornerRadius(4f), style = Stroke(2f))
            }
            withTransform({ rotate(9f, Offset(68f, 55f)) }) {
                drawRoundRect(Color(0xFFFFCA4D), Offset(43f, 35f), Size(57f, 49f), CornerRadius(5f))
                drawRoundRect(Color(0xFFFFE9A2), Offset(47f, 41f), Size(49f, 4f), CornerRadius(2f))
            }
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFF43FBE7), Color(0xFF078BB4), Color(0xFF005284))), Offset(23f, 43f), Size(76f, 58f), CornerRadius(12f))
            drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(27f, 47f), Size(67f, 49f), CornerRadius(9f), style = Stroke(1.5f))
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFF1CCBC7), Color(0xFF00769E))), Offset(75f, 61f), Size(31f, 24f), CornerRadius(7f))
            drawCircle(Color(0xFF92FFE3), 4f, Offset(85f, 73f))
            val sparkle = Path().apply { moveTo(14f, 34f); quadraticTo(15f, 42f, 20f, 44f); quadraticTo(15f, 46f, 14f, 54f); quadraticTo(13f, 46f, 8f, 44f); quadraticTo(13f, 42f, 14f, 34f) }
            drawPath(sparkle, Color(0xFFCCFFA5))
        }
    }
}

/** Small, scalable landscape drawn locally; decorative and independent of weather data. */
@Composable
fun LandscapeArtwork(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF2A2), Color(0xFFFFD773)), center = Offset(w * .80f, h * .27f), radius = h * .23f), h * .21f, Offset(w * .80f, h * .27f))
        fun mountain(color: Color, points: List<Pair<Float, Float>>) {
            val p = Path().apply { moveTo(0f, h); points.forEach { (x,y) -> lineTo(w*x, h*y) }; lineTo(w,h); close() }
            drawPath(p,color)
        }
        mountain(Color(0xFF9EDCCB), listOf(0f to .83f,.22f to .55f,.40f to .76f,.62f to .31f,.83f to .61f,1f to .18f))
        mountain(Color(0xFF54BDB0), listOf(0f to .96f,.32f to .73f,.53f to .84f,.78f to .53f,.91f to .69f,1f to .43f))
        mountain(Color(0xFF239D7C), listOf(.38f to 1f,.67f to .83f,.78f to .88f,.91f to .69f,1f to .76f))
        val white = Color(0xFFF3FFFF)
        val base = h*.88f
        listOf(.42f to .32f,.68f to .25f).forEach { (x,height) ->
            drawRoundRect(white, Offset(w*x,h*height), Size(w*.021f,base-h*height),CornerRadius(2f))
            val spire = Path().apply { moveTo(w*(x-.008f),h*height); lineTo(w*(x+.011f),h*(height-.15f)); lineTo(w*(x+.029f),h*height); close() }; drawPath(spire,Color(0xFFF5E9BC))
            drawLine(Color(0xFFADD4D8),Offset(w*(x-.011f),h*(height+.07f)),Offset(w*(x+.033f),h*(height+.07f)),2f)
        }
        val roof = Path().apply { moveTo(w*.38f,base); lineTo(w*.55f,h*.47f); lineTo(w*.73f,base); close() }
        drawPath(roof,white)
        drawLine(Color(0xFFB8E8ED),Offset(w*.55f,h*.47f),Offset(w*.56f,base),3f)
        drawRect(Color(0xFFD9F0EA),Offset(w*.39f,base),Size(w*.34f,h*.075f))
    }
}

@Composable
fun EncryptionBanner(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF057BB2), Color(0xFF009693), Color(0xFF68C4B6)))).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(39.dp), tint = Color(0xFFDCFFF8))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("Your ledger is encrypted", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("Protected on this phone. Cloud recovery needs a successful backup.", color = Color.White.copy(alpha = .92f), fontSize = 11.sp, lineHeight = 15.sp)
        }
        Spacer(Modifier.width(10.dp))
        Surface(color = Color.White.copy(alpha = .84f), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(17.dp), tint = Color(0xFF007F73))
                Text("256-bit", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF005A61))
            }
        }
    }
}
