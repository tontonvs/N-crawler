package com.noven.ncrawler.ui.holiday

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noven.ncrawler.ui.components.glassCard
import com.noven.ncrawler.ui.theme.MontserratFamily

// ═════════════════════════════════════════════════════════════════════════════
// TESTING ONLY — the picker in Settings that lets you try every holiday today.
// Picking one also replays the app-open greeting.
//
// TO REMOVE once you are happy (everything else keeps working):
//   1. delete this file,
//   2. in SourceSettingsScreen.kt delete the "Holiday (testing)" item block and the
//      import of HolidayPickerCard,
//   3. optionally in HolidaySettings.kt leave `choice` at AUTO.
// ═════════════════════════════════════════════════════════════════════════════
@Composable
fun HolidayPickerCard(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val current = HolidaySettings.choice

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard(RoundedCornerShape(16.dp), elevation = 3.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            "Holiday visuals",
            fontFamily = MontserratFamily,
            fontWeight = FontWeight.Bold,
            fontSize   = 15.sp,
            color      = colors.onSurface
        )
        Text(
            "Automatic follows the calendar. Pick one to preview it now.",
            fontFamily = MontserratFamily,
            fontSize   = 12.sp,
            lineHeight = 17.sp,
            color      = colors.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))

        HolidayChoice.values().forEachIndexed { index, choice ->
            if (index > 0) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(colors.outline.copy(alpha = 0.25f))
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { HolidaySettings.setChoice(choice) }
                    .padding(vertical = 11.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(choice.emoji, fontSize = 18.sp)
                Spacer(Modifier.width(12.dp))
                Text(
                    choice.label,
                    modifier   = Modifier.weight(1f),
                    fontFamily = MontserratFamily,
                    fontWeight = if (choice == current) FontWeight.Bold else FontWeight.Medium,
                    fontSize   = 14.sp,
                    color      = colors.onSurface
                )
                if (choice == current) {
                    Icon(
                        imageVector        = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint               = colors.primary,
                        modifier           = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
