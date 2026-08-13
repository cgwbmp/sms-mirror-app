package com.example.smsmirror.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.smsmirror.filter.Rule
import com.example.smsmirror.filter.RuleType
import com.example.smsmirror.ui.theme.DataStyle

@Composable
fun RulesScreen(
    forwardAll: Boolean,
    rules: List<Rule>,
    onSetForwardAll: (Boolean) -> Unit,
    onToggleRule: (Rule, Boolean) -> Unit,
    onDeleteRule: (Rule) -> Unit,
    onAddRule: (RuleType, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Forward everything",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (forwardAll) {
                                "Every message is forwarded. Block rules still apply."
                            } else {
                                "Only messages matching a rule below are forwarded."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = forwardAll, onCheckedChange = onSetForwardAll)
                }
            }

            SectionCard(title = "Rules") {
                if (rules.isEmpty()) {
                    EmptyState("No rules yet. Add one to forward only what matters.")
                } else {
                    rules.forEachIndexed { index, rule ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        RuleRow(
                            rule = rule,
                            onToggle = { onToggleRule(rule, it) },
                            onDelete = { onDeleteRule(rule) },
                        )
                    }
                }
            }

            // Clearance so the FAB never covers the last rule.
            Box(Modifier.size(72.dp))
        }

        ExtendedFloatingActionButton(
            onClick = { sheetOpen = true },
            icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
            text = { Text("Add rule") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        )
    }

    if (sheetOpen) {
        AddRuleSheet(
            onDismiss = { sheetOpen = false },
            onAdd = { type, pattern ->
                onAddRule(type, pattern)
                sheetOpen = false
            },
        )
    }
}

@Composable
private fun RuleRow(rule: Rule, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.type.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!rule.type.isPatternless) {
                Text(
                    text = rule.pattern,
                    style = DataStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!rule.isValid) {
                Text(
                    text = "Invalid pattern — this rule never matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Switch(checked = rule.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = "Delete rule",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddRuleSheet(onDismiss: () -> Unit, onAdd: (RuleType, String) -> Unit) {
    var type by remember { mutableStateOf(RuleType.SENDER_ALLOW) }
    var pattern by remember { mutableStateOf("") }

    val candidate = Rule(type = type, pattern = pattern)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Add rule",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            // Chips rather than a dropdown: five options fit, and seeing them all makes
            // the choice obvious without a tap to discover it. FlowRow packs them by
            // actual width, so labels of different lengths don't leave ragged gaps.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RuleType.entries.forEach { option ->
                    FilterChip(
                        selected = type == option,
                        onClick = { type = option },
                        label = { Text(option.displayName) },
                    )
                }
            }

            Text(
                text = type.hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!type.isPatternless) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text(type.fieldLabel) },
                    singleLine = true,
                    isError = pattern.isNotBlank() && !candidate.isValid,
                    supportingText = if (type == RuleType.REGEX && pattern.isNotBlank() && !candidate.isValid) {
                        { Text("Not a valid regular expression") }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (type == RuleType.SENDER_ALLOW || type == RuleType.SENDER_BLOCK) {
                            KeyboardType.Phone
                        } else {
                            KeyboardType.Text
                        },
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = { onAdd(type, pattern.trim()) },
                enabled = candidate.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Add rule")
            }
        }
    }
}

private val RuleType.displayName: String
    get() = when (this) {
        RuleType.SENDER_ALLOW -> "From sender"
        RuleType.SENDER_BLOCK -> "Block sender"
        RuleType.KEYWORD -> "Contains text"
        RuleType.REGEX -> "Matches regex"
        RuleType.OTP -> "Passcodes only"
    }

private val RuleType.fieldLabel: String
    get() = when (this) {
        RuleType.SENDER_ALLOW, RuleType.SENDER_BLOCK -> "Phone number or sender name"
        RuleType.KEYWORD -> "Text to look for"
        RuleType.REGEX -> "Regular expression"
        RuleType.OTP -> ""
    }

private val RuleType.hint: String
    get() = when (this) {
        RuleType.SENDER_ALLOW ->
            "Forward everything from this number. National and international forms of " +
                "the same number both match."
        RuleType.SENDER_BLOCK ->
            "Never forward from this number, even with \"Forward everything\" on."
        RuleType.KEYWORD -> "Forward when the message contains this text. Case-insensitive."
        RuleType.REGEX -> "Forward when the message matches this pattern."
        RuleType.OTP ->
            "Forward messages that look like one-time passcodes: a 4–8 digit code plus a " +
                "word like \"code\" or \"verification\"."
    }
