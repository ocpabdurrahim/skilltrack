package io.github.ocpabdurrahim.skilltrack.ui.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.data.CenterData
import io.github.ocpabdurrahim.skilltrack.data.EnquiryStatus
import io.github.ocpabdurrahim.skilltrack.data.PayMethod
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import io.github.ocpabdurrahim.skilltrack.ui.LocalNav
import io.github.ocpabdurrahim.skilltrack.ui.centerData
import io.github.ocpabdurrahim.skilltrack.ui.components.KeyValue
import io.github.ocpabdurrahim.skilltrack.ui.components.LegendDot
import io.github.ocpabdurrahim.skilltrack.ui.components.ListScreen
import io.github.ocpabdurrahim.skilltrack.ui.components.MonthSelector
import io.github.ocpabdurrahim.skilltrack.ui.components.ProgressBar
import io.github.ocpabdurrahim.skilltrack.ui.components.SectionHeader
import io.github.ocpabdurrahim.skilltrack.ui.components.SkillCard
import io.github.ocpabdurrahim.skilltrack.ui.components.StatTile
import io.github.ocpabdurrahim.skilltrack.ui.components.TileRow
import io.github.ocpabdurrahim.skilltrack.ui.theme.SkillTheme
import java.time.YearMonth

private class MonthFigures(val month: YearMonth, val income: Long, val spending: Long) {
    val profit: Long get() = income - spending
}

private fun figures(data: CenterData, month: YearMonth) =
    MonthFigures(month, data.income(month), data.expenseTotal(month) + data.instructorPayTotal(month))

@Composable
fun ReportsScreen() {
    val data = centerData()
    val nav = LocalNav.current
    val money = LocalMoney.current
    val colors = SkillTheme.colors
    var month by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val ym = YearMonth.parse(month)
    val year = remember(data) { (11 downTo 0).map { figures(data, YearMonth.now().minusMonths(it.toLong())) } }
    val current = remember(data, month) { figures(data, ym) }

    ListScreen(title = "Reports", onBack = nav::back) {
        item { SectionHeader("Last 12 months") }
        item {
            SkillCard(modifier = Modifier.testTag("year_chart")) {
                MonthBars(year, selected = ym) { month = it.toString() }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendDot(colors.success, "Income")
                    LegendDot(colors.danger, "Spending")
                }
                Spacer(Modifier.height(8.dp))
                KeyValue("Income", money.format(year.sumOf { it.income }), colors.success)
                KeyValue("Spending", money.format(year.sumOf { it.spending }), colors.danger)
                val profit = year.sumOf { it.profit }
                KeyValue("Profit", money.format(profit), if (profit >= 0) colors.success else colors.danger)
            }
        }

        item { SectionHeader("Month") }
        item { MonthSelector(ym, { month = it.toString() }) }
        item {
            TileRow {
                StatTile("Income", money.format(current.income), colors.success, Modifier.weight(1f), tag = "report_income")
                StatTile("Spending", money.format(current.spending), colors.danger, Modifier.weight(1f))
            }
        }
        item {
            TileRow {
                StatTile("Profit", money.format(current.profit), if (current.profit >= 0) colors.success else colors.danger, Modifier.weight(1f), tag = "report_profit")
                StatTile("Admissions", data.enrollments.count { Dates.inMonth(it.date, ym) }.toString(), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            }
        }
        item {
            val byCourse = data.payments.filter { Dates.inMonth(it.date, ym) }
                .groupBy { data.enrollmentById[it.enrollmentId]?.courseId }
                .mapValues { (_, l) -> l.sumOf { it.amount } }.entries.sortedByDescending { it.value }
            BreakdownCard("Income by course", byCourse.map { (courseId, v) -> data.courseName(courseId) to v }, colors.success)
        }
        item {
            val byHead = data.expenses.filter { Dates.inMonth(it.date, ym) }
                .groupBy { it.headId }.mapValues { (_, l) -> l.sumOf { it.amount } }
                .map { (h, v) -> (data.headById[h]?.let { "${it.emoji} ${it.name}" } ?: "Other") to v }
            val instructorPay = data.instructorPayTotal(ym)
            val rows = (byHead + if (instructorPay > 0) listOf("🧑‍🏫 Instructor pay" to instructorPay) else emptyList()).sortedByDescending { it.second }
            BreakdownCard("Spending by head", rows, colors.danger)
        }
        item {
            val byMethod = PayMethod.entries.map { m -> m.label to data.payments.filter { Dates.inMonth(it.date, ym) && it.method == m }.sumOf { it.amount } }.filter { it.second > 0 }
            BreakdownCard("Collection by method", byMethod, colors.info)
        }

        item { SectionHeader("Dues by course") }
        item {
            val rows = data.countedEnrollments.groupBy { it.courseId }
                .mapValues { (_, l) -> l.sumOf { data.fee(it).due.coerceAtLeast(0) } }
                .filter { it.value > 0 }.entries.sortedByDescending { it.value }
                .map { (c, v) -> data.courseName(c) to v }
            BreakdownCard("Still to collect", rows, colors.warning, total = data.totalDue)
        }

        item { SectionHeader("Enquiries") }
        item {
            val total = data.enquiries.size
            val converted = data.enquiries.count { it.status == EnquiryStatus.CONVERTED }
            val lost = data.enquiries.count { it.status == EnquiryStatus.LOST }
            SkillCard {
                KeyValue("All enquiries", total.toString())
                KeyValue("Converted to admissions", if (total > 0) "$converted (${converted * 100 / total}%)" else "0")
                KeyValue("Lost", lost.toString())
                KeyValue("This month", data.enquiries.count { Dates.inMonth(it.date, ym) }.toString())
                data.enquiries.groupBy { it.source }.entries.sortedByDescending { it.value.size }.forEach { (source, list) ->
                    KeyValue("From ${source.label}", "${list.size} (${list.count { it.status == EnquiryStatus.CONVERTED }} admitted)")
                }
            }
        }
    }
}

@Composable
private fun BreakdownCard(title: String, rows: List<Pair<String, Long>>, color: Color, total: Long? = null) {
    val money = LocalMoney.current
    SkillCard {
        Row {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(money.format(total ?: rows.sumOf { it.second }), style = MaterialTheme.typography.titleMedium, color = color)
        }
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) Text("Nothing in this period.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
        rows.forEach { (label, value) ->
            Row {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(money.format(value), style = MaterialTheme.typography.bodyMedium)
            }
            ProgressBar(value.toFloat() / max, color, height = 6.dp)
            Spacer(Modifier.height(6.dp))
        }
    }
}

/** Income and spending side by side for each month; tap a month to see it below. */
@Composable
private fun MonthBars(months: List<MonthFigures>, selected: YearMonth, onPick: (YearMonth) -> Unit) {
    val colors = SkillTheme.colors
    val max = months.maxOfOrNull { maxOf(it.income, it.spending) }?.coerceAtLeast(1L) ?: 1L
    Row(
        Modifier
            .fillMaxWidth()
            .height(170.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { m ->
            val isSelected = m.month == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { onPick(m.month) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Bar(m.income.toFloat() / max, colors.success)
                    Spacer(Modifier.width(1.dp))
                    Bar(m.spending.toFloat() / max, colors.danger)
                }
                Text(
                    Dates.shortMonth(m.month).take(3),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: Color) {
    Box(
        Modifier
            .width(7.dp)
            .fillMaxHeight(fraction.coerceIn(0.01f, 1f))
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
            .background(color),
    )
}
