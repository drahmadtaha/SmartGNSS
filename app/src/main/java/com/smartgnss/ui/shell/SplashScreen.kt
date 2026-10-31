package com.smartgnss.ui.shell

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.smartgnss.R

private val Navy1 = Color(0xFF123455)
private val Navy2 = Color(0xFF03101F)
private val Gold = Color(0xFFE6A014)

/** Launch splash (device-hero): crosshair logo + "Smart GNSS", the M10 receiver
 *  as the hero, and the developer credit. Tap anywhere to skip. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Navy1, Navy2)))
            .clickable { onDone() },
    ) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Logo + title
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null, modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.size(8.dp))
                Column {
                    Text("Smart GNSS", color = Color.White, fontSize = 30.sp,
                        fontWeight = FontWeight.Bold)
                    Text("Professional GNSS Survey", color = Gold,
                        fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(26.dp))

            // M10 hero — framed product shot
            Image(
                painter = painterResource(R.drawable.m10_device),
                contentDescription = "ICE M10 GNSS receiver",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth(0.86f)
                    .clip(RoundedCornerShape(22.dp))
                    .border(2.dp, Gold.copy(alpha = 0.85f), RoundedCornerShape(22.dp)),
            )
            Spacer(Modifier.height(10.dp))
            Text("ICE M10 · RTK GNSS Receiver", color = Color(0xFFBFD2E6),
                fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }

        // Developer credit pinned to the bottom.
        Column(
            Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("Developed by", color = Color(0xFF9FB4CC), fontSize = 12.sp)
            Text("Dr. Ahmad Taha", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Nablus, West-Bank, Palestine", color = Color.White, fontSize = 13.sp,
                textAlign = TextAlign.Center)
            Text("ahmad.taha@najah.edu", color = Color(0xFFCFE0F0), fontSize = 13.sp,
                fontFamily = FontFamily.Monospace)
            Text("WhatsApp: 00972597143595", color = Color(0xFFCFE0F0), fontSize = 13.sp,
                fontFamily = FontFamily.Monospace)
        }
    }
}
