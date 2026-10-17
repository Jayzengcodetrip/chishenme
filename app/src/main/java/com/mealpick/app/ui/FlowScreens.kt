package com.mealpick.app.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar
import kotlin.random.Random

internal enum class Way(val key: String) { Takeout("takeout"), Dining("dining"), Cooking("cooking"), Canteen("canteen"), Family("family") }

private fun Meal.art(): ImageVector = when (this) {
    Meal.Breakfast -> Art.breakfast
    Meal.Lunch -> Art.lunch
    Meal.Dinner -> Art.dinner
    Meal.AfternoonTea -> Art.afternoonTea
    Meal.LateNight -> Art.lateNight
}

private fun Meal.tint(): Color = when (this) {
    Meal.Breakfast -> Tones.Takeout.tint
    Meal.Lunch -> Tones.Cooking.tint
    Meal.Dinner -> Tones.Dining.tint
    Meal.AfternoonTea -> Tones.Snacks.tint
    Meal.LateNight -> Color(0xFF2F3A66)
}

@Composable
private fun Chevron(tint: Color = Mp.IconTint) {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

/** Rounded-square icon tile used by the meal list and the ways list. */
@Composable
private fun ArtTile(art: ImageVector, tint: Color) {
    Box(Modifier.size(44.dp).background(tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        Image(art, contentDescription = null, modifier = Modifier.size(34.dp))
    }
}

/**
 * Home, Things style: a quiet title, the five meals as one list, and the low-key utilities
 * (statistics bottom-left, language bottom-right, both in secondary grey) under a hairline.
 */
@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun MealHomeScreen(
    strings: UiStrings,
    onLanguage: (String) -> Unit,
    onMeal: (Meal) -> Unit,
    onStatistics: () -> Unit,
) {
    val current = remember { Meal.at(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }
    var languageExpanded by remember { mutableStateOf(false) }
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 44.dp, bottom = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.RamenDining, contentDescription = null, tint = Mp.Jade, modifier = Modifier.size(30.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        strings.appTitle,
                        modifier = Modifier.weight(1f).semantics { heading() }.testTag("home_title"),
                        color = Mp.Ink,
                        fontSize = 28.sp,
                        lineHeight = 36.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Meal.entries.forEach { meal ->
                        val isNow = meal == current
                        val interaction = remember { MutableInteractionSource() }
                        Surface(
                            onClick = { onMeal(meal) },
                            interactionSource = interaction,
                            shape = RoundedCornerShape(14.dp),
                            color = if (isNow) Mp.JadeTint else Color.Transparent,
                            contentColor = Mp.Ink,
                            modifier = Modifier.fillMaxWidth().pressScale(interaction).testTag("meal_${meal.key}"),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // On the highlighted row a white tile keeps the art readable against the accent tint.
                                ArtTile(meal.art(), if (isNow && meal != Meal.LateNight) Color.White else meal.tint())
                                Spacer(Modifier.width(16.dp))
                                FlowRow(
                                    modifier = Modifier.weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                    itemVerticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(strings.mealName(meal), fontSize = 19.sp, lineHeight = 25.sp,
                                        fontWeight = if (isNow) FontWeight.Bold else FontWeight.Medium, color = Mp.Ink)
                                    if (isNow) {
                                        Text(strings.now, color = Mp.JadeDeep, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(Modifier.width(8.dp))
                                Chevron(if (isNow) Mp.Jade else Mp.IconTint)
                            }
                        }
                    }
                }
            }

            Column(Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp)) {
                HorizontalDivider(color = Mp.Line)
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onStatistics,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("home_stats"),
                        colors = ButtonDefaults.textButtonColors(contentColor = Mp.Ink2),
                    ) {
                        Icon(Icons.Outlined.BarChart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(strings.statistics, fontSize = 15.sp)
                    }
                    Box {
                        TextButton(
                            onClick = { languageExpanded = true },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("language_menu"),
                            colors = ButtonDefaults.textButtonColors(contentColor = Mp.Ink2),
                        ) {
                            Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.languageLabel, fontSize = 15.sp)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(expanded = languageExpanded, onDismissRequest = { languageExpanded = false }) {
                            listOf("zh" to "中文", "en" to "English").forEach { (language, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    modifier = Modifier.testTag("language_$language"),
                                    onClick = { onLanguage(language); languageExpanded = false },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private class WayOption(val way: Way, val title: String, val art: ImageVector, val tint: Color)

/**
 * Second step: how to eat this meal. All five ways share one row style in a single column,
 * matching the home list. Late-night takeout leads to the snack lists.
 */
@Composable
internal fun WaysScreen(strings: UiStrings, onWay: (Way) -> Unit) {
    val options = listOf(
        WayOption(Way.Takeout, strings.takeout, Art.takeout, Tones.Takeout.tint),
        WayOption(Way.Dining, strings.dining, Art.dining, Tones.Dining.tint),
        WayOption(Way.Cooking, strings.cooking, Art.cooking, Tones.Cooking.tint),
        WayOption(Way.Canteen, strings.canteen, Art.canteen, Color(0xFFE9ECF6)),
        WayOption(Way.Family, strings.family, Art.family, Tones.Snacks.tint),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        options.forEach { option -> WayRow(option.title, option.art, option.tint, "way_${option.way.key}") { onWay(option.way) } }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WayRow(title: String, art: ImageVector, tint: Color, tag: String, onClick: () -> Unit) {
    MealCard(Modifier.fillMaxWidth().testTag(tag), radius = 16.dp, onClick = onClick) {
        Row(Modifier.heightIn(min = 76.dp).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ArtTile(art, tint)
            Spacer(Modifier.width(14.dp))
            Text(title, Modifier.weight(1f), fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold, color = Mp.Ink)
            Spacer(Modifier.width(8.dp))
            Chevron()
        }
    }
}

/** Remembers the last quote per page for this app session, so re-entering always shows a new one. */
internal object QuoteMemory {
    var canteen: Int? = null
    var family: Int? = null
}

/** "食堂有啥吃啥" draws a fortune slip; "家里有啥吃啥" pins a note on the fridge. */
@Composable
internal fun QuoteScreen(strings: UiStrings, canteen: Boolean) {
    val quotes = if (canteen) Quotes.canteen(strings.english) else Quotes.family(strings.english)
    var index by rememberSaveable(canteen) {
        val previous = if (canteen) QuoteMemory.canteen else QuoteMemory.family
        val next = Quotes.nextIndex(quotes.size, previous) { Random.nextInt(it) }
        if (canteen) QuoteMemory.canteen = next else QuoteMemory.family = next
        mutableStateOf(next)
    }
    val another = {
        val next = Quotes.nextIndex(quotes.size, index) { Random.nextInt(it) }
        if (canteen) QuoteMemory.canteen = next else QuoteMemory.family = next
        index = next
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
            label = "quote",
            modifier = Modifier.fillMaxWidth().testTag("quote_card"),
        ) { shown ->
            if (canteen) FortuneSlip(strings, shown, quotes[shown]) else FridgeNote(strings, quotes[shown])
        }
        Spacer(Modifier.height(32.dp))
        PillButton(onClick = another, modifier = Modifier.fillMaxWidth().testTag("quote_next")) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (canteen) strings.drawAnotherSign else strings.anotherLine, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun FortuneSlip(strings: UiStrings, index: Int, quote: String) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(Modifier.matchParentSize().padding(top = 8.dp).rotate(3f).background(Color(0xFFF1EFEC), RoundedCornerShape(26.dp)))
        MealCard(Modifier.fillMaxWidth(), radius = 26.dp, elevation = 3.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(130.dp)) {
                    TileWall(Modifier.fillMaxWidth().height(124.dp), tile = 22.dp)
                    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(6.dp).background(Mp.Jade))
                }
                Box(
                    Modifier.offset(y = (-32).dp).rotate(-6f).size(64.dp)
                        .background(Mp.Red, CircleShape).border(3.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("签", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Serif)
                }
                Column(Modifier.offset(y = (-16).dp).padding(horizontal = 26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${strings.canteenSign} · ${strings.signNumber(index + 1)}", color = Mp.Ink2, fontSize = 13.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(24.dp))
                    Text(quote, color = Mp.Ink, fontSize = 24.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.testTag("quote_text"))
                    Spacer(Modifier.height(24.dp))
                    Box(Modifier.size(28.dp, 3.dp).background(Mp.Red, CircleShape))
                    Spacer(Modifier.height(20.dp))
                    Box(Modifier.size(64.dp).background(Mp.Disabled, CircleShape), contentAlignment = Alignment.Center) {
                        Image(Art.canteen, contentDescription = null, modifier = Modifier.size(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun FridgeNote(strings: UiStrings, quote: String) {
    Box(Modifier.fillMaxWidth().padding(top = 20.dp), contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier.fillMaxWidth().rotate(-2f),
            shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp, bottomEnd = 26.dp, bottomStart = 10.dp),
            color = Color(0xFFFFF9EC),
            contentColor = Mp.Ink,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEADBC6)),
            shadowElevation = 4.dp,
        ) {
            Column(Modifier.padding(horizontal = 28.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(strings.familyNote, color = Tones.Snacks.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(30.dp))
                Text(quote, color = Mp.Ink, fontSize = 24.sp, lineHeight = 40.sp, fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Serif, textAlign = TextAlign.Center, modifier = Modifier.testTag("quote_text"))
                Spacer(Modifier.height(34.dp))
                Box(Modifier.size(84.dp).background(Tones.Snacks.tint, CircleShape), contentAlignment = Alignment.Center) {
                    Image(Art.family, contentDescription = null, modifier = Modifier.size(62.dp))
                }
            }
        }
        Box(Modifier.offset(y = (-14).dp).size(32.dp).background(Mp.Red, CircleShape).border(4.dp, Color(0xFFA32A1F).copy(alpha = .35f), CircleShape))
    }
}
