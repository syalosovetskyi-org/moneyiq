package org.syalosovetskyi.onemoney.ui.components.icons

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun CircleIconBox(
    icon:     ImageVector,
    color:    Color,
    boxSize:  Dp      = 40.dp,
    iconSize: Dp      = 20.dp,
    tint:     Color   = Color.White,
    modifier: Modifier = Modifier
) {
    Box(
        modifier         = modifier
            .size(boxSize)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
internal fun RoundedIconBox(
    icon:         ImageVector,
    color:        Color,
    cornerRadius: Dp      = 12.dp,
    boxSize:      Dp      = 48.dp,
    iconSize:     Dp      = 26.dp,
    tint:         Color   = Color.White,
    modifier:     Modifier = Modifier
) {
    Box(
        modifier         = modifier
            .size(boxSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(iconSize))
    }
}
