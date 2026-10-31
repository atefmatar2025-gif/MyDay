package com.dayflow.app.ui.screens.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.dayflow.app.R
import com.dayflow.app.domain.AssistantService
import com.dayflow.app.domain.TaskInteractor
import com.dayflow.app.ui.components.DfTopBar
import com.dayflow.app.ui.components.rememberSpeechInput
import com.dayflow.app.ui.components.speechSupport
import com.dayflow.app.ui.navigation.Routes
import com.dayflow.app.ui.navigation.dfViewModel
import com.dayflow.app.ui.theme.Spacing
import com.dayflow.core.intelligence.AssistantAction
import com.dayflow.core.intelligence.AssistantReply
import com.dayflow.core.intelligence.RuleBasedAssistantEngine
import androidx.compose.runtime.mutableStateListOf
import kotlinx.coroutines.launch

sealed interface ChatItem {
    data class User(val text: String) : ChatItem
    data class Bot(val reply: AssistantReply) : ChatItem
}

/** Chat history lives only in memory: answers are recomputed from current data each time. */
class AssistantViewModel(private val service: AssistantService, private val interactor: TaskInteractor) : ViewModel() {
    val messages = mutableStateListOf<ChatItem>()
    val engineName = service.engine.displayName
    val isLanguageModel = service.engine.isLanguageModel

    fun ask(q: String) {
        val text = q.trim()
        if (text.isEmpty()) return
        messages += ChatItem.User(text)
        viewModelScope.launch { messages += ChatItem.Bot(service.ask(text)) }
    }

    fun startTask(id: Long) = viewModelScope.launch { interactor.start(id) }
}

@Composable
fun AssistantScreen(nav: NavController) {
    val vm = dfViewModel { AssistantViewModel(it.assistantService, it.taskInteractor) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val support = remember { speechSupport(context) }
    val listen = rememberSpeechInput(onText = { vm.ask(it) }, onUnavailable = {})
    LaunchedEffect(vm.messages.size) { if (vm.messages.isNotEmpty()) listState.animateScrollToItem(vm.messages.size) }

    Column(Modifier.fillMaxSize().imePadding()) {
        DfTopBar(stringResource(R.string.ask_dayflow), onBack = { nav.popBackStack() })
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(Spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        stringResource(R.string.assistant_disclaimer, vm.engineName),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            itemsIndexed(vm.messages) { _, m ->
                when (m) {
                    is ChatItem.User -> Bubble(m.text, mine = true)
                    is ChatItem.Bot -> BotBubble(m.reply) { action ->
                        when (action) {
                            is AssistantAction.StartTask -> { vm.startTask(action.taskId); nav.navigate(Routes.focus(action.taskId)) }
                            is AssistantAction.OpenTask -> nav.navigate(Routes.task(action.taskId))
                            is AssistantAction.ReviewPlan -> nav.navigate(Routes.saveMyDay(action.date.toEpochDay()))
                        }
                    }
                }
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            items(RuleBasedAssistantEngine.SUGGESTED_QUESTIONS) { q -> AssistChip(onClick = { vm.ask(q) }, label = { Text(q) }) }
        }
        Row(Modifier.fillMaxWidth().padding(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input, onValueChange = { input = it }, modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.ask_placeholder)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.ask(input); input = "" }),
                shape = RoundedCornerShape(24.dp), maxLines = 3,
            )
            if (support.available) {
                IconButton(onClick = listen) { Icon(Icons.Filled.Mic, stringResource(R.string.voice_input)) }
            }
            FilledIconButton(onClick = { vm.ask(input); input = "" }) {
                Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.send))
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterStart else Alignment.CenterEnd) {
        Text(
            text,
            modifier = Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                .background(if (mine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            color = if (mine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun BotBubble(reply: AssistantReply, onAction: (AssistantAction) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Column(
            Modifier.widthIn(max = 340.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surface)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(reply.text, style = MaterialTheme.typography.bodyLarge)
            reply.bullets.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            reply.actions.forEach { a ->
                OutlinedButton(onClick = { onAction(a) }, modifier = Modifier.padding(top = 2.dp)) { Text(a.label, maxLines = 1) }
            }
        }
    }
}
