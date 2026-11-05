package com.shinemusic.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shinemusic.MusicViewModel

/**
 * Bottom padding for scrolling lists: exactly the height of the floating nav (+ mini-player when a song
 * is loaded) so the last row ends right above them instead of leaving a big black gap.
 *  nav row 68 + bottom margin 12 = 80;  mini-player 68 + 2 x 8 = 84;  +4 breathing room.
 */
@Composable
fun bottomBarSpace(vm: MusicViewModel): Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val target = navInset + 84.dp + if (vm.current != null) 84.dp else 0.dp
    val animated by animateDpAsState(target, label = "bottomBarSpace")
    return animated
}
