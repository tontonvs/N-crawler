package com.noven.ncrawler.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.ui.theme.GlassMode
import com.noven.ncrawler.ui.theme.MontserratFamily

// Settings switch for Glass mode. OFF (default) = the classic look.
// ON = tv3 glass: translucent cards + real blur on the nav, Detail buttons and
// Reader pills. Flips instantly — no restart — and is remembered.
@Composable
fun GlassModeCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(16.dp), elevation = 3.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Glass mode",
                fontFamily = MontserratFamily,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp,
                color      = colors.onSurface
            )
            Text(
                "Frosted look with blur",
                fontFamily = MontserratFamily,
                fontSize   = 12.sp,
                lineHeight = 17.sp,
                color      = colors.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked         = GlassMode.enabled,
            onCheckedChange = { GlassMode.set(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor    = Color.White,
                checkedTrackColor    = accent,
                checkedBorderColor   = accent,
                uncheckedThumbColor  = colors.onSurfaceVariant,
                uncheckedTrackColor  = colors.background,
                uncheckedBorderColor = colors.outline
            )
        )
    }
}
