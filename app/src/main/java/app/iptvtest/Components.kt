package app.iptvtest

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/**
 * Base de tudo que pode ser selecionado com o controle remoto:
 * mostra borda + leve zoom quando está com o foco e reage ao botão central (OK).
 */
@Composable
fun FocusBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
    content: @Composable BoxScope.(Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (focused) 1.05f else 1f, label = "focusScale")
    val requesterModifier = if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(requesterModifier)
            .onFocusChanged { focused = it.isFocused }
            .border(width = 3.dp, color = if (focused) Accent else Color.Transparent, shape = shape)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        content(focused)
    }
}

@Composable
fun TvButton(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    focusRequester: FocusRequester? = null,
    onClick: () -> Unit,
) {
    FocusBox(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        focusRequester = focusRequester,
        onClick = onClick,
    ) { focused ->
        Box(
            modifier = Modifier
                .background(if (focused) Accent else if (selected) PanelAlt else Panel)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                color = if (focused) Color.Black else TextMain,
                fontSize = 15.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun PosterCard(entry: MediaEntry, aspect: Float, fit: Boolean, onClick: () -> Unit) {
    FocusBox(modifier = Modifier.fillMaxWidth(), onClick = onClick) { focused ->
        Column(modifier = Modifier.background(if (focused) PanelAlt else Panel)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspect)
                    .background(PanelAlt),
                contentAlignment = Alignment.Center,
            ) {
                if (entry.image != null) {
                    AsyncImage(
                        model = entry.image,
                        contentDescription = null,
                        contentScale = if (fit) ContentScale.Fit else ContentScale.Crop,
                        modifier = if (fit) Modifier.fillMaxSize().padding(10.dp) else Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = entry.title.take(1).uppercase(),
                        color = Muted,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = entry.title,
                color = if (focused) Color.White else TextMain,
                fontSize = 13.sp,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}
