package com.rajatxo.micup.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rajatxo.micup.ui.theme.GlassFill
import com.rajatxo.micup.ui.theme.GlassFillStrong
import com.rajatxo.micup.ui.theme.GlassStroke

/**
 * Frosted-glass card — the signature surface of the Coral X design language.
 *
 * On AMOLED black, a translucent white fill + hairline stroke + soft blur
 * reads as "premium" without breaking the dark theme. Used for the room
 * code card, the participant cells, and the action buttons.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    cornerRadius: Int = 24,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(cornerRadius.dp),
        color = if (strong) GlassFillStrong else GlassFill,
        border = BorderStroke(1.dp, GlassStroke),
        content = {
            Box(modifier = Modifier.padding(0.dp)) {
                content()
            }
        }
    )
}
