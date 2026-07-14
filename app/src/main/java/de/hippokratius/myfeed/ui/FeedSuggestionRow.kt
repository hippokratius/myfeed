package de.hippokratius.myfeed.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.hippokratius.myfeed.R

/** Vorschlagszeile mit Ein-Tipp-Hinzufügen, genutzt vom Katalog und der Feed-Suche. */
@Composable
fun FeedSuggestionRow(
    title: String,
    subtitle: String,
    added: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Titel dürfen umbrechen: Bei "Blog » Mobile Feed" u. Ä. sitzt genau
            // der unterscheidende Teil am Ende und darf nicht abgeschnitten werden.
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Gleiche Breite wie der IconButton (48 dp), damit + und Häkchen
        // über alle Zeilen hinweg exakt untereinander stehen.
        Box(
            modifier = Modifier.size(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (added) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = stringResource(R.string.discover_added),
                    tint = MaterialTheme.colorScheme.primary,
                )
            } else {
                IconButton(onClick = onAdd) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
                }
            }
        }
    }
}
