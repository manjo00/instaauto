package com.autoinsta.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shown when Android's battery rules will stop the queue firing on time.
 *
 * ## Why this is worth a banner rather than a settings toggle
 * On 2026-09-09 a post silently did not publish. Nothing was broken: the app was in the
 * **RARE** standby bucket, where even `setExactAndAllowWhileIdle` is throttled to about
 * one firing a day. The alarm sat there for thirteen hours until the app was opened.
 *
 * There was no error, no notification and nothing on screen — the queue looked perfectly
 * healthy while being unable to do the one thing it exists for. An app that quietly fails
 * to keep a schedule is worse than one that says it might, which is the same argument as
 * [ExactAlarmBanner], and this cause is both more likely and better hidden.
 *
 * Being idle is this app's *normal* state — it posts once a week without being opened —
 * so it drifts into a throttled bucket by design unless exempted.
 */
@Composable
fun BackgroundHealthBanner(
    onFixClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.BatteryAlert,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp)
            ) {
                Text(
                    text = "Android may not wake this app",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text(
                    text = "Battery saving is switched on for autoinsta. Because this app " +
                        "sits idle between posts, Android can hold its alarms back for " +
                        "hours — a post then waits until you next open the app. " +
                        "This has already happened once.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(top = 2.dp),
                )
                TextButton(
                    onClick = onFixClick,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text("Allow it to run in the background")
                }
            }
        }
    }
}
