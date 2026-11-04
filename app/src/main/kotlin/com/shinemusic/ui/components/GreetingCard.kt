package com.shinemusic.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

private data class Greeting(val title: String, val sub: String, val emoji: String, val colors: List<Color>)

@Composable
fun GreetingCard(onSearch: () -> Unit) {
    val g = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> Greeting("Good Morning", "Start your day with music", "🌤️", listOf(Color(0x40FFC46B), Color(0x207B8CFF)))
            in 12..16 -> Greeting("Good Afternoon", "Keep the vibe going", "☀️", listOf(Color(0x40FFE066), Color(0x205BC0FF)))
            in 17..20 -> Greeting("Good Evening", "Wind down with your tunes", "🌇", listOf(Color(0x40FF9E80), Color(0x209C6BFF)))
            else -> Greeting("Good Night", "Soft songs for late hours", "🌙", listOf(Color(0x405C6BC0), Color(0x20311B92)))
        }
    }
    val cs = MaterialTheme.colorScheme

    val bobT = rememberInfiniteTransition(label = "bob")
    val bob by bobT.animateFloat(
        -4f, 4f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob"
    )

    Row(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth()
            .glass(RoundedCornerShape(34.dp), wash = Brush.linearGradient(g.colors))
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            g.emoji, fontSize = 40.sp,
            modifier = Modifier.graphicsLayer { translationY = bob.dp.toPx() }
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(g.title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp), color = cs.onSurface)
            Text(g.sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        Box(
            Modifier.size(52.dp)
                .bounceClick(onClick = onSearch)
                .glass(CircleShape, tint = cs.primary.copy(alpha = 0.55f), strength = 1.2f),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.Search, "Search", tint = Color.White) }
    }
}
