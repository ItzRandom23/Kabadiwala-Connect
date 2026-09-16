package com.irinteractivestudios.kabadiwalaconnect.ui.screens.settings

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.irinteractivestudios.kabadiwalaconnect.R
import com.irinteractivestudios.kabadiwalaconnect.ui.components.SectionCard

private data class SafetyTip(val title: Int, val doText: Int, val dontText: Int, val audio: Int)
private val tips = listOf(
    SafetyTip(R.string.safety_crt, R.string.safety_crt_do, R.string.safety_crt_dont, R.string.safety_crt_audio),
    SafetyTip(R.string.safety_battery, R.string.safety_battery_do, R.string.safety_battery_dont, R.string.safety_battery_audio),
    SafetyTip(R.string.safety_motor, R.string.safety_motor_do, R.string.safety_motor_dont, R.string.safety_motor_audio),
    SafetyTip(R.string.safety_transformer, R.string.safety_transformer_do, R.string.safety_transformer_dont, R.string.safety_transformer_audio),
    SafetyTip(R.string.safety_capacitor, R.string.safety_capacitor_do, R.string.safety_capacitor_dont, R.string.safety_capacitor_audio),
    SafetyTip(R.string.safety_other, R.string.safety_other_do, R.string.safety_other_dont, R.string.safety_other_audio)
)

@Composable
fun SafetyScreen(modifier: Modifier = Modifier) {
    var filter by remember { mutableStateOf<Int?>(null) }
    var bookmarked by remember { mutableStateOf(setOf<Int>()) }
    val context = LocalContext.current
    val tts = remember(context) { TextToSpeech(context, null) }
    DisposableEffect(tts) { onDispose { tts.shutdown() } }
    val visible = if (filter == null) tips else tips.filter { it.title == filter }
    LazyColumn(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.safety_title), style = MaterialTheme.typography.headlineLarge); Text(stringResource(R.string.safety_directory_detail), style = MaterialTheme.typography.bodyLarge) }
        item { Text(stringResource(R.string.safety_filter), style = MaterialTheme.typography.titleMedium); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { FilterChip(filter == null, { filter = null }, label = { Text(stringResource(R.string.safety_all)) }); tips.forEach { tip -> FilterChip(filter == tip.title, { filter = tip.title }, label = { Text(stringResource(tip.title)) }) } } }
        item { SectionCard(title = stringResource(R.string.safety_tip_day)) { Text(stringResource(R.string.safety_tip_day_text), style = MaterialTheme.typography.bodyLarge) } }
        items(visible.size) { index -> val tip = visible[index]; SafetyCard(tip, tip.title in bookmarked, { bookmarked = if (tip.title in bookmarked) bookmarked - tip.title else bookmarked + tip.title }, tts) }
    }
}

@Composable
private fun SafetyCard(tip: SafetyTip, saved: Boolean, bookmark: () -> Unit, tts: TextToSpeech) {
    val audioText = stringResource(tip.audio)
    SectionCard(title = stringResource(tip.title)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = bookmark) { Text(if (saved) stringResource(R.string.safety_saved) else stringResource(R.string.safety_bookmark)) } }
        Icon(Icons.Filled.Warning, stringResource(R.string.safety_visual), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(48.dp))
        Text(stringResource(R.string.safety_do), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(tip.doText), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.safety_do_not), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(tip.dontText), style = MaterialTheme.typography.bodyLarge)
        Text(stringResource(R.string.safety_localized), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { tts.speak(audioText, TextToSpeech.QUEUE_FLUSH, null, "safety") }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) { Icon(Icons.Filled.VolumeUp, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.safety_hear)) }
    } }
}

@Composable
fun HelpScreen(modifier: Modifier = Modifier) { Column(modifier.fillMaxSize().padding(16.dp)) { Text(stringResource(R.string.help_title), style = MaterialTheme.typography.headlineLarge); Spacer(Modifier.height(16.dp)); SectionCard(title = stringResource(R.string.help_title)) { Text(stringResource(R.string.help_message), style = MaterialTheme.typography.bodyLarge) } } }
