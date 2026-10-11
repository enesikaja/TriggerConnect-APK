package com.triggerconnect.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.triggerconnect.app.core.TimeFormat
import com.triggerconnect.app.domain.model.AcknowledgmentStatus
import com.triggerconnect.app.domain.model.DestinationType
import com.triggerconnect.app.domain.model.EventDirection
import com.triggerconnect.app.domain.model.TriggerEvent
import com.triggerconnect.app.domain.model.TriggerPriority
import com.triggerconnect.app.ui.theme.SuccessGreen
import com.triggerconnect.app.ui.theme.WarnAmber
import com.triggerconnect.app.ui.theme.DangerRed

@Composable
fun TriggerEventRow(event: TriggerEvent, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (event.direction == EventDirection.SENT) "To ${event.destinationName}" else "From ${event.senderName}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                PriorityChip(event.priority)
            }
            Spacer(Modifier.size(6.dp))
            Text(event.content, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            Spacer(Modifier.size(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    TimeFormat.relative(event.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (event.direction == EventDirection.SENT && event.destinationType == DestinationType.GROUP) {
                    Text(
                        "Ack ${event.acknowledgedCount}/${event.recipientCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (event.direction == EventDirection.RECEIVED) {
                    Text(
                        if (event.myAcknowledgment == AcknowledgmentStatus.ACKNOWLEDGED) "Acknowledged" else "New",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (event.myAcknowledgment == AcknowledgmentStatus.ACKNOWLEDGED) SuccessGreen else MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
fun PriorityChip(priority: TriggerPriority) {
    val color: Color = when (priority) {
        TriggerPriority.URGENT -> DangerRed
        TriggerPriority.IMPORTANT -> WarnAmber
        TriggerPriority.NORMAL -> MaterialTheme.colorScheme.primary
    }
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(priority.name.lowercase().replaceFirstChar { it.uppercase() }) },
        colors = AssistChipDefaults.assistChipColors(
            disabledLabelColor = color,
            disabledContainerColor = color.copy(alpha = 0.12f),
        ),
    )
}
