package com.example.inknote.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.model.PalmRejectionMode

@Composable
fun AppSettingsDialog(
    currentPalmMode: PalmRejectionMode,
    onSetPalmMode: (PalmRejectionMode) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("inknote_settings", Context.MODE_PRIVATE) }

    var selectedPalmMode by remember { mutableStateOf(currentPalmMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                "App Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Palm Rejection Mode
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PanTool, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Palm Rejection Mode", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            "Controls how finger touch is handled during note-taking.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Option 1: Stylus Only
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedPalmMode == PalmRejectionMode.STYLUS_ONLY,
                                    onClick = {
                                        selectedPalmMode = PalmRejectionMode.STYLUS_ONLY
                                        onSetPalmMode(PalmRejectionMode.STYLUS_ONLY)
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text("Stylus Only (Recommended)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Only stylus tip draws. Fingers strictly scroll & zoom.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Option 2: Auto (Stylus Detect)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedPalmMode == PalmRejectionMode.AUTO,
                                    onClick = {
                                        selectedPalmMode = PalmRejectionMode.AUTO
                                        onSetPalmMode(PalmRejectionMode.AUTO)
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text("Auto Detect", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Rejects touch while stylus is actively writing.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Option 3: Off
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = selectedPalmMode == PalmRejectionMode.OFF,
                                    onClick = {
                                        selectedPalmMode = PalmRejectionMode.OFF
                                        onSetPalmMode(PalmRejectionMode.OFF)
                                    }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text("Off", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("Both fingers and stylus can produce ink.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // 2. Gemini AI Services (Pre-configured)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                            Text("Gemini AI Tools", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Surface(
                                color = Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(11.dp))
                                    Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                }
                            }
                        }
                        Text(
                            "Smart AI tools (Handwriting-to-Text OCR, Math Solver, Flashcards, Quiz Generator, and Note Summarization) are pre-configured and ready to use without requiring any manual key entry.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // 3. Fully Offline & Privacy Information
                Surface(
                    color = Color(0xFFF0FDF4),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(24.dp))
                        Column {
                            Text("100% Local & Private", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF15803D))
                            Text("No sign-in, no accounts, and no cloud sync. All notebooks and notes stay strictly on this device.", fontSize = 11.sp, color = Color(0xFF166534), lineHeight = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}
