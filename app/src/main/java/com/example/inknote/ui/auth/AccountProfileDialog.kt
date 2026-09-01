package com.example.inknote.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.cloud.CloudSyncState
import com.example.inknote.data.db.entities.UserAccountEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AccountProfileDialog(
    user: UserAccountEntity,
    syncStatus: CloudSyncState,
    isFirebaseActive: Boolean = false,
    lastSyncTime: Long = 0L,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onSyncNow: () -> Unit
) {
    val avatarColor = try {
        Color(android.graphics.Color.parseColor(user.avatarColorHex))
    } catch (e: Exception) {
        Color(0xFF4F46E5)
    }

    val initials = user.displayName
        .split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifEmpty { "U" }
        .uppercase()

    val formattedSyncTime = if (lastSyncTime > 0) {
        val sdf = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
        sdf.format(Date(lastSyncTime))
    } else {
        "Just now"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Account & Cloud Sync", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // User Avatar
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .background(avatarColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = user.displayName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text(
                    text = user.email,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Firebase Status Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isFirebaseActive) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isFirebaseActive) Icons.Default.CheckCircle else Icons.Default.CloudQueue,
                            contentDescription = null,
                            tint = if (isFirebaseActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isFirebaseActive) "Firebase Connected" else "Firebase Integration Ready",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFirebaseActive) Color(0xFF10B981) else MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = if (isFirebaseActive) "Realtime Auth & Firestore sync active" else "App ID: com.aistudio.inknote.app (google-services.json enabled)",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Cloud Sync Card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                when (syncStatus) {
                                    CloudSyncState.SYNCING -> {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Syncing...",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    CloudSyncState.SYNCED -> {
                                        Icon(
                                            Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Cloud Synced",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    CloudSyncState.OFFLINE -> {
                                        Icon(
                                            Icons.Default.CloudOff,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Offline Mode",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = Color(0xFFF59E0B),
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                    else -> {
                                        Icon(
                                            Icons.Default.CloudQueue,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            "Cloud Connected",
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }

                            FilledTonalButton(
                                onClick = onSyncNow,
                                enabled = syncStatus != CloudSyncState.SYNCING,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("button_sync_now")
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sync Now", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Last updated: $formattedSyncTime",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                        Text(
                            text = "Log in with ${user.email} on another device to restore all notes.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("Close")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onSignOut,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("button_sign_out")
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Sign Out")
            }
        }
    )
}
