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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.model.PalmRejectionMode
import com.example.inknote.data.model.ThemeMode
import com.example.inknote.data.preferences.ThemeStateHolder

@Composable
fun AppSettingsDialog(
    currentPalmMode: PalmRejectionMode,
    onSetPalmMode: (PalmRejectionMode) -> Unit,
    onResetToolbar: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currentThemeMode by ThemeStateHolder.themeMode.collectAsState()
    val isDarkPaper by ThemeStateHolder.isDarkPaperEnabled.collectAsState()
    val isInvertInk by ThemeStateHolder.isInvertInkInDarkMode.collectAsState()

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
                // 1. Theme Control (Light / Dark / Match System)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.DarkMode,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text("Theme", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            "Choose your appearance or match system settings.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 3-Option Theme Selector (Light, Dark, System)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val options = listOf(
                                Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                                Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode),
                                Triple(ThemeMode.SYSTEM, "System", Icons.Default.BrightnessAuto)
                            )
                            options.forEach { (mode, label, icon) ->
                                val isSelected = currentThemeMode == mode
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { ThemeStateHolder.setThemeMode(context, mode) },
                                    label = { Text(label, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    leadingIcon = {
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                        // Dark Paper Canvas Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Dark Paper Canvas", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Renders the writing page in comforting dark charcoal instead of bright white when dark mode is active.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isDarkPaper,
                                onCheckedChange = { ThemeStateHolder.setDarkPaperEnabled(context, it) }
                            )
                        }

                        // Invert Ink Legibility Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Invert Dark Ink on Dark Paper", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("Auto-adjusts black/dark ink to crisp off-white for high legibility on dark paper without modifying saved notes.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isInvertInk,
                                onCheckedChange = { ThemeStateHolder.setInvertInkInDarkMode(context, it) }
                            )
                        }
                    }
                }

                // 2. Palm Rejection Mode
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

                // 3. Floating Toolbar Controls
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
                            Icon(Icons.Default.DragIndicator, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text("Floating Toolbar", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            "If the floating toolbar was dragged off-screen, behind the top bar, or into an unreachable position, tap below to immediately bring it back to its default position and expand it.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        var resetDone by remember { mutableStateOf(false) }
                        OutlinedButton(
                            onClick = {
                                com.example.inknote.data.preferences.SettingsManager(context).resetToolbarPosition()
                                onResetToolbar()
                                resetDone = true
                            },
                            modifier = Modifier.fillMaxWidth().testTag("reset_toolbar_position_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                if (resetDone) Icons.Default.Check else Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = if (resetDone) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (resetDone) "Toolbar Position Reset!" else "Reset Toolbar Position",
                                fontWeight = FontWeight.SemiBold,
                                color = if (resetDone) Color(0xFF16A34A) else MaterialTheme.colorScheme.primary
                            )
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

                // 4. Fully Offline & Privacy Information
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Column {
                            Text("Offline-First & Private", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                            Text("All core note-taking works without an internet connection. Optional AI features require one. All notebooks stay on this device.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 15.sp)
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
