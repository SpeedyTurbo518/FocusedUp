package com.focusedup.shared.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusedup.shared.model.TaskItem
import com.focusedup.shared.ui.FColors
import com.focusedup.shared.ui.GlassCard
import com.focusedup.shared.ui.Hairline
import com.focusedup.shared.ui.LocalEngine
import com.focusedup.shared.ui.fieldColors

/** Today's to-do list: type or paste a list (one task per line) and check items off. */
@Composable
fun TasksCard(onToast: (String) -> Unit) {
    val engine = LocalEngine.current
    val store = engine.tasks
    val all by store.tasks.collectAsState()
    val today = store.today()
    val todays = all.filter { it.date == today && !it.cleared }.sortedWith(compareBy({ it.done }, { it.id }))
    val doneCount = todays.count { it.done }
    // A half-typed task survives closing the app.
    var input by remember { mutableStateOf(engine.settings.ui("tasks_draft").orEmpty()) }
    androidx.compose.runtime.LaunchedEffect(input) { engine.settings.setUi("tasks_draft", input) }

    fun addInput() {
        val added = store.add(input)
        if (added > 0) {
            input = ""
            if (added > 1) onToast("Added $added tasks")
        }
    }

    GlassCard(Modifier.fillMaxWidth(), padding = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Today's tasks", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        todays.isEmpty() -> "What needs to get done today?"
                        doneCount == todays.size -> "All $doneCount done. Well done!"
                        else -> "$doneCount of ${todays.size} completed"
                    },
                    color = FColors.TextSecondary, fontSize = 13.sp,
                )
            }
            if (doneCount > 0) {
                TextButton(onClick = { store.clearDone() }) {
                    Text("Clear done", color = FColors.BlueBright, fontSize = 14.sp)
                }
            }
        }

        if (todays.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val progress by animateFloatAsState(doneCount.toFloat() / todays.size, label = "taskProgress")
            Box(
                Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.08f))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).fillMaxHeight().clip(RoundedCornerShape(3.dp))
                        .background(FColors.BlueBright)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Add a task, or paste a list (one per line)", fontSize = 14.sp) },
                maxLines = 5,
                colors = fieldColors(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .weight(1f)
                    .onPreviewKeyEvent { e ->
                        // On a computer keyboard, Enter adds the task and Shift+Enter starts a new line.
                        if (e.key == Key.Enter && !e.isShiftPressed) {
                            if (e.type == KeyEventType.KeyDown) addInput()
                            true
                        } else false
                    },
            )
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank()) FColors.Blue else Color.White.copy(alpha = 0.08f))
                    .clickable(enabled = input.isNotBlank()) { addInput() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add task", tint = Color.White)
            }
        }

        if (todays.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            todays.forEachIndexed { i, task ->
                if (i > 0) Hairline(startPadding = 40.dp)
                TaskRow(task, onToggle = { store.toggle(task.id) }, onDelete = { store.remove(task.id) })
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RoundCheckbox(task.done)
        Text(
            task.title,
            color = if (task.done) FColors.TextTertiary else Color.White,
            fontSize = 16.sp,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Rounded.Close,
            contentDescription = "Delete task",
            tint = FColors.TextTertiary,
            modifier = Modifier.size(28.dp).clip(CircleShape).clickable(onClick = onDelete).padding(5.dp),
        )
    }
}

@Composable
fun RoundCheckbox(checked: Boolean) {
    val fill by animateColorAsState(if (checked) FColors.Blue else Color.Transparent, label = "checkFill")
    Box(
        Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(fill)
            .border(1.5.dp, if (checked) FColors.Blue else Color.White.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(16.dp))
    }
}
