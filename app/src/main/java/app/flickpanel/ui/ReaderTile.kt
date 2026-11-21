package app.flickpanel.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.flickpanel.ha.ReadingContent

@Composable
internal fun ReaderTile(label: String, content: ReadingContent?, modifier: Modifier = Modifier, open: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = open,
        enabled = content != null,
        modifier = modifier.heightIn(min = 48.dp),
        shape = panelCardShape,
        color = colors.surfaceContainer,
    ) {
        Column(
            Modifier.padding(tileContentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TileTitle(label, Modifier.weight(1f))
                if (content != null) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Open full screen", Modifier.size(22.dp),
                        tint = colors.onSurfaceVariant)
                }
            }
            TileStatus(content?.preview ?: "Text unavailable")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderScreen(title: String, content: ReadingContent?, close: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(title, Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = close) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to dashboard")
                    }
                },
            )
        },
    ) { insets ->
        Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 720.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (content == null) {
                    Text("Text unavailable", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    SelectionContainer {
                        Text(content.text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 30.sp))
                    }
                    if (content.truncated) {
                        Text("This text was shortened.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
