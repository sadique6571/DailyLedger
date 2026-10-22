package com.sadique.dailyledger.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.ai.AiModelRequest
import com.sadique.dailyledger.ai.AiPrompts
import kotlinx.coroutines.launch

private data class ChatMessage(val fromUser: Boolean, val text: String)

/**
 * AI Assistant: free chat over the user's own totals, plus one-tap monthly report,
 * budget advice, savings coaching and debt payoff plan. Online only (Gemini), nothing is saved.
 */
@Composable
fun AiChatScreen(
    contextJson: String,
    advise: suspend (AiModelRequest) -> String,
    onBack: () -> Unit,
    embedded: Boolean = false,
    onBusyChanged: (Boolean) -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var messages by remember { mutableStateOf(listOf(ChatMessage(false, "Assalam-o-Alaikum! Apne hisaab ke bare mein kuch bhi poochein — ya neeche wale button dabayein."))) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    fun ask(request: AiModelRequest, echo: String?) {
        if (busy) return
        busy = true
        onBusyChanged(true)
        if (echo != null) messages = messages + ChatMessage(true, echo)
        scope.launch {
            try {
                val answer = advise(request)
                messages = messages + ChatMessage(false, answer)
            } catch (e: Exception) {
                messages = messages + ChatMessage(false, e.message ?: "AI se rabta nahi ho saka. Internet aur Settings mein API key check karein.")
            } finally {
                busy = false
                onBusyChanged(false)
            }
        }
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(enabled = !busy, onClick = { ask(AiPrompts.monthlyReport(contextJson), "Is mahine ki report banayein") }, label = { Text("Mahine ki report") })
            AssistChip(enabled = !busy, onClick = { ask(AiPrompts.budgetAdvice(contextJson), "Budget ka mashwara dein") }, label = { Text("Budget mashwara") })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(enabled = !busy, onClick = { ask(AiPrompts.savingsCoach(contextJson), "Bachat ka plan banayein") }, label = { Text("Bachat plan") })
            AssistChip(enabled = !busy, onClick = { ask(AiPrompts.debtPlan(contextJson), "Qarz adaigi ki tarteeb banayein") }, label = { Text("Qarz tarteeb") })
        }
        LazyColumn(Modifier.weight(1f).testTag("ai-chat-list"), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(messages) { message ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(10.dp)) {
                        Text(if (message.fromUser) "Aap" else "AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(message.text, style = MaterialTheme.typography.bodyMedium)
                        if (!message.fromUser) {
                            IconButton(onClick = {
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, message.text)
                                context.startActivity(Intent.createChooser(send, "Share"))
                            }) { Icon(Icons.Rounded.Share, "Share") }
                        }
                    }
                }
            }
        }
        if (busy) Text("AI soch raha hai...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(input, { input = it }, label = { Text("Sawal likhein...") }, modifier = Modifier.weight(1f).testTag("ai-chat-input"), enabled = !busy)
            Button(enabled = !busy && input.isNotBlank(), modifier = Modifier.testTag("ai-chat-send"), onClick = {
                val question = input.trim()
                input = ""
                ask(AiPrompts.chat(question, contextJson), question)
            }) { Text("Bhejein") }
        }
    }
}
