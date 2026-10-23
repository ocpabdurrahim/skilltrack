package io.github.ocpabdurrahim.skilltrack.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.ocpabdurrahim.skilltrack.AppContainer
import io.github.ocpabdurrahim.skilltrack.data.Center
import io.github.ocpabdurrahim.skilltrack.data.CenterData
import io.github.ocpabdurrahim.skilltrack.domain.Grouping
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.print.Printouts

/** The center's currency and digit grouping, available to every screen. */
@Immutable
data class MoneyFormat(val symbol: String = "৳", val grouping: Grouping = Grouping.SOUTH_ASIAN) {
    fun format(minor: Long, showPlus: Boolean = false): String = Money.format(minor, symbol, grouping, showPlus)
    fun compact(minor: Long): String = Money.compact(minor, symbol, grouping)
}

val LocalApp = staticCompositionLocalOf<AppContainer> { error("No AppContainer") }
val LocalMoney = staticCompositionLocalOf { MoneyFormat() }

/** Shows a short message at the bottom of the screen. */
val LocalNotify = staticCompositionLocalOf<(String) -> Unit> { {} }

/** The open center, live. */
@Composable
fun centerData(): CenterData {
    val data by LocalApp.current.store.data.collectAsStateWithLifecycle()
    return data
}

fun MoneyFormat.printouts(center: Center?): Printouts = Printouts(center ?: Center(id = "")) { format(it) }
