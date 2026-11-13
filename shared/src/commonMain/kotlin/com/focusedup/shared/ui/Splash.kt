package com.focusedup.shared.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Welcome screen shown at launch. Tap anywhere to skip. */
@Composable
fun SplashScreen(onDone: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        shown = true
        delay(1500)
        onDone()
    }
    val logoAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(550), label = "logoAlpha")
    val logoScale by animateFloatAsState(if (shown) 1f else 0.86f, tween(650, easing = FastOutSlowInEasing), label = "logoScale")
    val creditAlpha by animateFloatAsState(if (shown) 1f else 0f, tween(550, delayMillis = 200), label = "creditAlpha")

    AppBackground {
        Box(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDone)
        ) {
            Column(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = logoAlpha
                        scaleX = logoScale
                        scaleY = logoScale
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FocusLogo(112.dp)
                Spacer(Modifier.height(24.dp))
                Text("Focused Up", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp)
                Spacer(Modifier.height(6.dp))
                Text("Stay locked in.", color = FColors.TextSecondary, fontSize = 16.sp)
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 24.dp, end = 24.dp, bottom = 56.dp)
                    .graphicsLayer { alpha = creditAlpha },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("This app was made by", color = FColors.TextTertiary, fontSize = 13.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(4.dp))
                Text("STG Studios", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.height(4.dp))
                Text("A faith based company", color = FColors.BlueBright, fontSize = 14.sp, textAlign = TextAlign.Center)
            }
        }
    }
}
