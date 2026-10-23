package io.github.ocpabdurrahim.skilltrack.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.ocpabdurrahim.skilltrack.domain.Dates
import io.github.ocpabdurrahim.skilltrack.domain.Money
import io.github.ocpabdurrahim.skilltrack.ui.LocalMoney
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun FieldLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(top = 12.dp, bottom = 6.dp),
    )
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLength: Int = 200,
    supporting: String? = null,
    isError: Boolean = false,
    tag: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onChange(it) },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        modifier = modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

@Composable
fun NameInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, tag: String? = null, isError: Boolean = false) =
    TextInput(value, onChange, label, modifier, capitalization = KeyboardCapitalization.Words, tag = tag, isError = isError)

@Composable
fun PhoneInput(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, tag: String? = null) =
    TextInput(value, { raw -> onChange(raw.filter { it.isDigit() || it in "+- " }) }, label, modifier, keyboardType = KeyboardType.Phone, maxLength = 20, tag = tag)

/** Amount input that only accepts what could become a valid amount. */
@Composable
fun AmountInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    tag: String? = null,
) {
    val money = LocalMoney.current
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> Money.sanitize(raw)?.let(onChange) },
        label = { Text(label) },
        prefix = { Text(money.symbol + " ") },
        singleLine = true,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

/** Whole or decimal numbers (marks, capacity); [decimals] = 0 for whole numbers only. */
@Composable
fun NumberInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimals: Int = 0,
    tag: String? = null,
    isError: Boolean = false,
) {
    val pattern = remember(decimals) { if (decimals == 0) Regex("^\\d{0,6}$") else Regex("^\\d{0,6}(\\.\\d{0,$decimals})?$") }
    OutlinedTextField(
        value = value,
        onValueChange = { if (pattern.matches(it)) onChange(it) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals == 0) KeyboardType.Number else KeyboardType.Decimal),
        modifier = modifier
            .fillMaxWidth()
            .then(if (tag != null) Modifier.testTag(tag) else Modifier),
    )
}

/** A "YYYY-MM-DD" date chosen from a calendar. Blank shows as [blankLabel]. */
@Composable
fun DateInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    clearable: Boolean = false,
    blankLabel: String = "",
    tag: String? = null,
) {
    var open by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = if (value.isBlank()) blankLabel else Dates.full(value),
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            Row {
                if (clearable && value.isNotBlank()) {
                    IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear date") }
                }
                IconButton(onClick = { open = true }, modifier = Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)) {
                    Icon(Icons.Filled.Event, contentDescription = "Pick a date")
                }
            }
        },
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
    if (open) {
        val initial = Dates.parse(value) ?: LocalDate.now()
        val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onChange(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = state)
        }
    }
}

/** Pick one of [options]; [noneLabel] adds a "nothing chosen" entry. */
@Composable
fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T?,
    onSelect: (T?) -> Unit,
    optionLabel: (T) -> String,
    modifier: Modifier = Modifier,
    noneLabel: String? = null,
    tag: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let(optionLabel) ?: noneLabel.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (noneLabel != null) {
                DropdownMenuItem(text = { Text(noneLabel) }, onClick = {
                    onSelect(null)
                    expanded = false
                })
            }
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = {
                    onSelect(option)
                    expanded = false
                })
            }
        }
    }
}

/** One choice out of a few, as chips. */
@Composable
fun <T> ChoiceChips(options: List<T>, selected: T?, onSelect: (T) -> Unit, label: (T) -> String, modifier: Modifier = Modifier, tagPrefix: String? = null) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                modifier = if (tagPrefix != null) Modifier.testTag("${tagPrefix}_${label(option)}") else Modifier,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Two fields side by side. */
@Composable
fun FieldPair(left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}
