package com.orgista.openstream.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.orgista.openstream.domain.ContentPolicy
import com.orgista.openstream.domain.KidsCap
import com.orgista.openstream.domain.createKidsPolicy
import com.orgista.openstream.ui.icons.AppIcons

private enum class Step { MODE, RATING, BLOCKLIST, CONFIRM }

/**
 * First-run setup. The kids path ends in an explicit irreversible
 * confirmation: once enabled there is no PIN, setting, or hidden exit —
 * uninstalling the app (clearing its data) is the only reset.
 */
@Composable
fun OnboardingScreen(
    onStandard: () -> Unit,
    onKids: (ContentPolicy.Kids) -> Unit,
) {
    var step by remember { mutableStateOf(Step.MODE) }
    var cap by remember { mutableStateOf(KidsCap.TEENS) }
    val blockedTerms = remember { mutableStateListOf<String>() }
    var termDraft by remember { mutableStateOf("") }
    var acknowledged by remember { mutableStateOf(false) }

    BackHandler(enabled = step != Step.MODE) {
        acknowledged = false
        step = when (step) {
            Step.CONFIRM -> Step.BLOCKLIST
            Step.BLOCKLIST -> Step.RATING
            else -> Step.MODE
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(OpenStreamColors.CanvasDeep),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (step) {
                Step.MODE -> {
                    Title("Who watches on this device?")
                    Subtitle("This choice sets up the whole library. Kids Mode is permanent for this installation.")
                    ChoiceCard(
                        title = "Everyone",
                        body = "The full library with every connected source and add-on.",
                        onClick = onStandard,
                    )
                    ChoiceCard(
                        title = "Kids Mode",
                        body = "Only age-appropriate titles. Restricted and unrated content never appears or plays.",
                        warning = "Cannot be reverted once enabled",
                        onClick = { step = Step.RATING },
                    )
                }

                Step.RATING -> {
                    Title("What is the highest rating allowed?")
                    Subtitle("Titles above this certification are hidden and refuse to play. Unrated content is always blocked in Kids Mode.")
                    KidsCap.entries.forEach { entry ->
                        ChoiceCard(
                            title = entry.label,
                            body = entry.description,
                            detail = entry.examples,
                            selected = entry == cap,
                            onClick = {
                                cap = entry
                                step = Step.BLOCKLIST
                            },
                        )
                    }
                }

                Step.BLOCKLIST -> {
                    Title("Anything else to block?")
                    Subtitle("Add titles, franchises, or words. Any match in a title or description is hidden even when its rating passes.")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedTextField(
                            value = termDraft,
                            onValueChange = { termDraft = it.take(80) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("Type a title or word") },
                        )
                        Button(
                            onClick = {
                                val term = termDraft.trim().lowercase()
                                if (term.isNotEmpty() && term !in blockedTerms) blockedTerms.add(term)
                                termDraft = ""
                            },
                            enabled = termDraft.isNotBlank(),
                        ) { Text("Add") }
                    }
                    if (blockedTerms.isEmpty()) {
                        Subtitle("No extra blocks yet. The ${cap.label} rating cap still applies.")
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        ) {
                            blockedTerms.take(6).forEach { term ->
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = OpenStreamColors.Coral.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, OpenStreamColors.Coral.copy(alpha = 0.4f)),
                                    onClick = { blockedTerms.remove(term) },
                                ) {
                                    Text(
                                        "$term  ✕",
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        color = OpenStreamColors.Coral,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                        if (blockedTerms.size > 6) Subtitle("+ ${blockedTerms.size - 6} more")
                    }
                    Button(onClick = { step = Step.CONFIRM }) { Text("Continue") }
                }

                Step.CONFIRM -> {
                    Icon(AppIcons.Warning, contentDescription = null, tint = OpenStreamColors.Amber, modifier = Modifier.size(48.dp))
                    Title("Turn on Kids Mode?")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SummaryCell(cap.label, cap.examples)
                        SummaryCell("${blockedTerms.size}", if (blockedTerms.size == 1) "blocked term" else "blocked terms")
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = OpenStreamColors.Amber.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, OpenStreamColors.Amber.copy(alpha = 0.45f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(AppIcons.Lock, contentDescription = null, tint = OpenStreamColors.Amber, modifier = Modifier.size(22.dp))
                            Text(
                                "Kids Mode cannot be reverted. There is no PIN, setting, or hidden exit. " +
                                    "To turn it off, OpenStream must be uninstalled — removing its app data — and set up again from the beginning.",
                                color = OpenStreamColors.Amber.copy(alpha = 0.95f),
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Checkbox(
                            checked = acknowledged,
                            onCheckedChange = { acknowledged = it },
                            colors = CheckboxDefaults.colors(checkedColor = OpenStreamColors.Amber),
                        )
                        Text("I understand Kids Mode is permanent for this installation.", color = OpenStreamColors.TextSoft)
                    }
                    Button(
                        onClick = { onKids(createKidsPolicy(cap, blockedTerms.toList())) },
                        enabled = acknowledged,
                        colors = ButtonDefaults.buttonColors(containerColor = OpenStreamColors.Amber, contentColor = OpenStreamColors.CanvasDeep),
                    ) {
                        Icon(AppIcons.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("  Enable Kids Mode permanently", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (step != Step.MODE) {
                TextButton(onClick = {
                    acknowledged = false
                    step = when (step) {
                        Step.CONFIRM -> Step.BLOCKLIST
                        Step.BLOCKLIST -> Step.RATING
                        else -> Step.MODE
                    }
                }) { Text("Back", color = OpenStreamColors.Muted) }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    Text(text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text, textAlign = TextAlign.Center)
}

@Composable
private fun Subtitle(text: String) {
    Text(text, fontSize = 16.sp, color = OpenStreamColors.TextSoft, textAlign = TextAlign.Center, lineHeight = 23.sp)
}

@Composable
private fun ChoiceCard(
    title: String,
    body: String,
    detail: String? = null,
    warning: String? = null,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = OpenStreamColors.Surface,
        border = BorderStroke(1.dp, if (selected) OpenStreamColors.Cyan.copy(alpha = 0.6f) else OpenStreamColors.Border),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(title, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
            Text(body, fontSize = 15.sp, color = OpenStreamColors.TextSoft, lineHeight = 21.sp)
            if (detail != null) {
                Text(detail.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = OpenStreamColors.Muted)
            }
            if (warning != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(AppIcons.Lock, contentDescription = null, tint = OpenStreamColors.Amber, modifier = Modifier.size(14.dp))
                    Text(warning, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = OpenStreamColors.Amber)
                }
            }
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = OpenStreamColors.Surface, border = BorderStroke(1.dp, OpenStreamColors.Border)) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 12.dp).width(160.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = OpenStreamColors.Text)
            Text(label, fontSize = 13.sp, color = OpenStreamColors.Muted)
        }
    }
}
