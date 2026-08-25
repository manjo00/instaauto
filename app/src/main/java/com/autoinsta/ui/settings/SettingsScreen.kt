package com.autoinsta.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.autoinsta.AutoInstaApp
import com.autoinsta.domain.TokenLifecycle

/**
 * Where the Instagram account is connected, checked, and disconnected.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AutoInstaApp
    val viewModel: SettingsViewModel = viewModel(
        factory = remember {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SettingsViewModel(app.accountRepository) as T
            }
        },
    )

    val state by viewModel.uiState.collectAsState()
    var confirmDisconnect by remember { mutableStateOf(false) }

    // The login page takes over the whole screen while it's up.
    if (state.showLogin) {
        InstagramLoginScreen(
            onCodeReceived = viewModel::onCodeReceived,
            onCancelled = viewModel::cancelConnect,
            onError = viewModel::onLoginError,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Instagram account", style = MaterialTheme.typography.titleMedium)

            when {
                state.isConnecting -> ConnectingCard()
                state.account != null -> ConnectedCard(
                    username = state.account!!.username,
                    profilePictureUrl = state.account!!.profilePictureUrl,
                    daysRemaining = state.daysRemaining,
                    tokenState = state.tokenState,
                    onDisconnectClick = { confirmDisconnect = true },
                    onReconnectClick = viewModel::startConnect,
                )
                else -> NotConnectedCard(onConnectClick = viewModel::startConnect)
            }

            state.errorMessage?.let { message ->
                MessageCard(
                    text = message,
                    isError = true,
                    onDismiss = viewModel::consumeMessages,
                )
            }
            state.successMessage?.let { message ->
                MessageCard(
                    text = message,
                    isError = false,
                    onDismiss = viewModel::consumeMessages,
                )
            }
        }
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect Instagram?") },
            text = {
                Text(
                    "Scheduled posts will stay in your queue, but nothing can be published " +
                        "until you connect an account again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.disconnect()
                    confirmDisconnect = false
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NotConnectedCard(onConnectClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Not connected", style = MaterialTheme.typography.titleSmall)
            Text(
                text = "autoinsta needs permission to post to your account. You'll log in " +
                    "with Instagram and approve it once.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(onClick = onConnectClick) { Text("Connect Instagram") }
        }
    }
}

@Composable
private fun ConnectingCard() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("Connecting…", modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun ConnectedCard(
    username: String,
    profilePictureUrl: String?,
    daysRemaining: Int,
    tokenState: TokenLifecycle.State,
    onDisconnectClick: () -> Unit,
    onReconnectClick: () -> Unit,
) {
    val expired = tokenState is TokenLifecycle.State.Expired

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (expired) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (profilePictureUrl != null) {
                    AsyncImage(
                        model = profilePictureUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                }
                Column(modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp)) {
                    Text(
                        text = "@$username",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ConnectionStatusLine(expired, daysRemaining)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (expired) {
                    Button(onClick = onReconnectClick) { Text("Reconnect") }
                }
                OutlinedButton(onClick = onDisconnectClick) { Text("Disconnect") }
            }
        }
    }
}

@Composable
private fun ConnectionStatusLine(expired: Boolean, daysRemaining: Int) {
    val (icon, text, tint) = when {
        expired -> Triple(
            Icons.Default.ErrorOutline,
            "Login expired — reconnect to keep posting",
            MaterialTheme.colorScheme.error,
        )
        daysRemaining <= 10 -> Triple(
            Icons.Default.ErrorOutline,
            "Renews automatically · $daysRemaining days left",
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Triple(
            Icons.Default.CheckCircle,
            "Connected · $daysRemaining days left",
            MaterialTheme.colorScheme.primary,
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = tint)
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun MessageCard(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isError) {
                        MaterialTheme.colorScheme.onErrorContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                )
            }
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}
