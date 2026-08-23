package com.example.inknote.ui.canvas.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.inknote.data.ai.NoteSummaryResult
import com.example.inknote.data.ai.QuizQuestion
import com.example.inknote.data.db.entities.FlashcardEntity
import com.example.inknote.data.db.entities.PageSnapshotEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FlashcardStudyModal(
    notebookTitle: String,
    flashcards: List<FlashcardEntity>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onGenerateWithAi: () -> Unit,
    onMarkMastered: (FlashcardEntity) -> Unit,
    onMarkReview: (FlashcardEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var isFlipped by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "cardFlip"
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "Flashcards & Study Mode",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            notebookTitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Generating AI Flashcards...", fontSize = 14.sp)
                        }
                    }
                } else if (flashcards.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text("No flashcards for this notebook yet.", fontSize = 14.sp)
                            Button(onClick = onGenerateWithAi) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Generate with AI")
                            }
                        }
                    }
                } else {
                    val validIndex = currentIndex.coerceIn(0, flashcards.size - 1)
                    val card = flashcards[validIndex]

                    // Progress indicator
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Card ${validIndex + 1} of ${flashcards.size}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text(card.category, fontSize = 11.sp) }
                        )
                    }

                    // Flip Card Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .graphicsLayer {
                                rotationY = rotation
                                cameraDistance = 12f * density
                            }
                            .clickable { isFlipped = !isFlipped }
                            .background(
                                color = if (isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (rotation <= 90f) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "QUESTION / PROMPT",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    card.frontText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "👆 Tap to flip for answer",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.graphicsLayer { rotationY = 180f }
                            ) {
                                Text(
                                    "ANSWER / EXPLANATION",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    card.backText,
                                    fontSize = 15.sp,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    // Navigation and Assessment
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        OutlinedButton(
                            onClick = {
                                onMarkReview(card)
                                if (validIndex < flashcards.size - 1) {
                                    currentIndex++
                                    isFlipped = false
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE11D48))
                        ) {
                            Icon(Icons.Default.Repeat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Need Review")
                        }

                        Button(
                            onClick = {
                                onMarkMastered(card)
                                if (validIndex < flashcards.size - 1) {
                                    currentIndex++
                                    isFlipped = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Mastered")
                        }
                    }

                    // Footer actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                if (validIndex > 0) {
                                    currentIndex--
                                    isFlipped = false
                                }
                            },
                            enabled = validIndex > 0
                        ) {
                            Text("Previous")
                        }

                        IconButton(onClick = onGenerateWithAi) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "Regenerate with AI", tint = MaterialTheme.colorScheme.primary)
                        }

                        TextButton(
                            onClick = {
                                if (validIndex < flashcards.size - 1) {
                                    currentIndex++
                                    isFlipped = false
                                }
                            },
                            enabled = validIndex < flashcards.size - 1
                        ) {
                            Text("Next")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InteractiveQuizModal(
    notebookTitle: String,
    quizQuestions: List<QuizQuestion>,
    isLoading: Boolean,
    errorMessage: String? = null,
    onGenerateQuiz: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    var selectedAnswerIndex by remember { mutableStateOf<Int?>(null) }
    var score by remember { mutableIntStateOf(0) }
    var isQuizCompleted by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("AI Knowledge Quiz", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(notebookTitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp),
                            fontSize = 12.sp
                        )
                    }
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text("Generating Quiz from notes...", fontSize = 14.sp)
                        }
                    }
                } else if (quizQuestions.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.Default.Quiz, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                            Text("Take an active recall quiz on this note", fontSize = 14.sp)
                            Button(onClick = onGenerateQuiz) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Start Quiz")
                            }
                        }
                    }
                } else if (isQuizCompleted) {
                    // Result Screen
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Default.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = Color(0xFFF59E0B)
                        )
                        Text(
                            "Quiz Completed!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "You scored $score out of ${quizQuestions.size}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = {
                                currentIndex = 0
                                selectedAnswerIndex = null
                                score = 0
                                isQuizCompleted = false
                            }
                        ) {
                            Icon(Icons.Default.Replay, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Retake Quiz")
                        }
                    }
                } else {
                    val question = quizQuestions[currentIndex]

                    Text(
                        "Question ${currentIndex + 1} of ${quizQuestions.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        question.question,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        question.options.forEachIndexed { optIdx, optionText ->
                            val isSelected = selectedAnswerIndex == optIdx
                            val isCorrect = optIdx == question.correctIndex
                            val showFeedback = selectedAnswerIndex != null

                            val cardColor = when {
                                !showFeedback -> if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                isCorrect -> Color(0xFFDCFCE7)
                                isSelected -> Color(0xFFFEE2E2)
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }

                            val borderCol = when {
                                !showFeedback -> if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                isCorrect -> Color(0xFF16A34A)
                                isSelected -> Color(0xFFDC2626)
                                else -> Color.Transparent
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = selectedAnswerIndex == null) {
                                        selectedAnswerIndex = optIdx
                                        if (isCorrect) score++
                                    },
                                shape = RoundedCornerShape(10.dp),
                                color = cardColor,
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, borderCol)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        "${('A'.code + optIdx).toChar()}.",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(optionText, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                    if (showFeedback && isCorrect) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A))
                                    } else if (showFeedback && isSelected) {
                                        Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }

                    if (selectedAnswerIndex != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        ) {
                            Text(
                                "💡 ${question.explanation}",
                                fontSize = 12.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }

                        Button(
                            onClick = {
                                if (currentIndex < quizQuestions.size - 1) {
                                    currentIndex++
                                    selectedAnswerIndex = null
                                } else {
                                    isQuizCompleted = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (currentIndex < quizQuestions.size - 1) "Next Question" else "See Results")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AiSummaryModal(
    summaryResult: NoteSummaryResult?,
    isLoading: Boolean,
    errorMessage: String? = null,
    onInsertAsText: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("AI Note Summary & Insights", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (!errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp),
                        fontSize = 12.sp
                    )
                }
            } else if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("Synthesizing notebook notes...", fontSize = 14.sp)
                    }
                }
            } else if (summaryResult != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Executive Summary",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(summaryResult.summary, fontSize = 13.sp)
                            }
                        }
                    }

                    item {
                        Text("Key Concepts & Takeaways:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        summaryResult.keyPoints.forEach { pt ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("•", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(pt, fontSize = 13.sp)
                            }
                        }
                    }

                    item {
                        Text("Action Items:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        summaryResult.actionItems.forEach { act ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircleOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(act, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (summaryResult != null) {
                Button(onClick = {
                    val fullText = buildString {
                        append("📝 Summary: ${summaryResult.title}\n")
                        append("${summaryResult.summary}\n\n")
                        append("Key Points:\n")
                        summaryResult.keyPoints.forEach { append("• $it\n") }
                        append("\nAction Items:\n")
                        summaryResult.actionItems.forEach { append("☑ $it\n") }
                    }
                    onInsertAsText(fullText)
                    onDismiss()
                }) {
                    Text("Insert on Page")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun PageSnapshotsModal(
    snapshots: List<PageSnapshotEntity>,
    onSaveCurrentSnapshot: (String) -> Unit,
    onRestoreSnapshot: (PageSnapshotEntity) -> Unit,
    onDeleteSnapshot: (PageSnapshotEntity) -> Unit,
    onDismiss: () -> Unit
) {
    var snapshotTitleInput by remember { mutableStateOf("") }
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Version History & Snapshots", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = snapshotTitleInput,
                        onValueChange = { snapshotTitleInput = it },
                        placeholder = { Text("Snapshot name...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val title = snapshotTitleInput.ifBlank { "Snapshot ${System.currentTimeMillis() % 1000}" }
                            onSaveCurrentSnapshot(title)
                            snapshotTitleInput = ""
                        }
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Save")
                    }
                }

                HorizontalDivider()

                if (snapshots.isEmpty()) {
                    Text(
                        "No saved snapshots for this page yet. Save versions to easily rollback changes.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(snapshots) { snap ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(snap.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(dateFormat.format(Date(snap.timestamp)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Row {
                                        IconButton(onClick = { onRestoreSnapshot(snap); onDismiss() }) {
                                            Icon(Icons.Default.Restore, contentDescription = "Restore", tint = MaterialTheme.colorScheme.primary)
                                        }
                                        IconButton(onClick = { onDeleteSnapshot(snap) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun SecurityPinDialog(
    currentPin: String?,
    isUnlocking: Boolean = false,
    onVerifyOrSetPin: (String) -> Boolean, // returns true if success
    onRemovePin: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var pinInput by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                if (isUnlocking) "Enter Notebook PIN" else if (currentPin != null) "Change Notebook PIN" else "Set Notebook PIN",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    if (isUnlocking) "Enter the 4-digit PIN to access this notebook." else "Protect your private notes with a secure passcode.",
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) pinInput = it },
                    label = { Text("Passcode (4-6 digits)") },
                    singleLine = true,
                    isError = errorMessage != null
                )

                if (errorMessage != null) {
                    Text(errorMessage ?: "", color = Color(0xFFDC2626), fontSize = 12.sp)
                }

                if (!isUnlocking && currentPin != null) {
                    TextButton(
                        onClick = {
                            onRemovePin()
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                    ) {
                        Text("Remove Password Protection")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pinInput.length < 4) {
                        errorMessage = "PIN must be at least 4 digits"
                    } else {
                        val success = onVerifyOrSetPin(pinInput)
                        if (success) {
                            onDismiss()
                        } else {
                            errorMessage = "Incorrect PIN code"
                        }
                    }
                }
            ) {
                Text(if (isUnlocking) "Unlock" else "Set PIN")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
