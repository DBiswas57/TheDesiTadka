package com.thedesitadka.app.monetization.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thedesitadka.app.monetization.AdLoadResult
import com.thedesitadka.app.monetization.AdPlacementType
import com.thedesitadka.app.monetization.MonetizationManager

@Composable
fun AdSlotView(
    placement: AdPlacementType,
    monetizationManager: MonetizationManager,
    modifier: Modifier = Modifier
) {
    var adResult by remember { mutableStateOf<AdLoadResult?>(null) }
    var isRendered by remember { mutableStateOf(false) }
    var isCollapsed by remember { mutableStateOf(false) }

    LaunchedEffect(placement) {
        val result = monetizationManager.loadPlacement(placement)
        adResult = result
        if (result is AdLoadResult.Failure) {
            isCollapsed = true
        }
    }

    val currentResult = adResult

    AnimatedVisibility(
        visible = currentResult is AdLoadResult.Success && !isCollapsed,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        if (currentResult is AdLoadResult.Success) {
            val format = currentResult.format
            val heightDp = if (format.heightDp > 0) format.heightDp.dp else 250.dp
            val widthDp = if (format.widthDp > 0) format.widthDp.dp else 300.dp

            Column(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Discreet Ad Label compliant with publisher guidelines
                Text(
                    text = "SPONSORED",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Box(
                    modifier = Modifier
                        .width(widthDp)
                        .height(heightDp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF141414)),
                    contentAlignment = Alignment.Center
                ) {
                    currentResult.htmlContent?.let { html ->
                        AdWebView(
                            htmlContent = html,
                            onAdRendered = {
                                if (!isRendered) {
                                    isRendered = true
                                    monetizationManager.onAdDisplayed(placement, currentResult.providerType)
                                }
                            },
                            onAdClicked = {
                                monetizationManager.onAdClicked(placement, currentResult.providerType)
                            },
                            onError = {
                                isCollapsed = true
                            },
                            modifier = Modifier
                                .width(widthDp)
                                .height(heightDp)
                        )
                    }
                }
            }
        }
    }
}
