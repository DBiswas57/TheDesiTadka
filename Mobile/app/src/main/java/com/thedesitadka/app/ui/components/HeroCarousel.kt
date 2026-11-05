package com.thedesitadka.app.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.thedesitadka.app.ui.theme.LocalAppPalette
import com.thedesitadka.core.model.VideoItem
import kotlin.math.absoluteValue

@Composable
fun HeroCarousel(
    items: List<VideoItem>,
    onWatchClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (items.isEmpty()) return

    val palette = LocalAppPalette.current
    val pagerState = rememberPagerState(pageCount = { items.size })

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
        ) { page ->
            val featuredItem = items[page]
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction

            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .graphicsLayer {
                        val factor = 1f - (pageOffset.absoluteValue.coerceIn(0f, 1f) * 0.08f)
                        scaleX = factor
                        scaleY = factor
                        alpha = 1f - (pageOffset.absoluteValue.coerceIn(0f, 1f) * 0.25f)
                    }
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = palette.glowColor,
                        spotColor = palette.primary.copy(alpha = 0.35f)
                    )
                    .border(
                        border = BorderStroke(1.2.dp, palette.glassBorder),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onWatchClick(featuredItem) },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = palette.cardSurface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (featuredItem.thumbnailUrl.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(featuredItem.thumbnailUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Cinematic 3D Vignette & Multi-Stop Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.25f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.70f),
                                        Color.Black.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )

                    // Content overlay with glassmorphism touches
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(palette.primary)
                                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                ) {
                                    Text(
                                        text = featuredItem.providerId.uppercase(),
                                        color = palette.onPrimary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }

                                featuredItem.category?.let { cat ->
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = cat,
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // 3D Glowing "Watch Now" Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(palette.gradientBrush)
                                    .padding(horizontal = 12.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play",
                                        tint = palette.onPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "WATCH",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = palette.onPrimary,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = featuredItem.title,
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Animated 3D Indicator Dots/Pills
        if (items.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(items.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    val indicatorWidth by animateDpAsState(
                        targetValue = if (isSelected) 18.dp else 5.dp,
                        label = "indicator_width_$index"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.5.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isSelected) palette.gradientBrush
                                else Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.25f),
                                        Color.White.copy(alpha = 0.25f)
                                    )
                                )
                            )
                            .size(width = indicatorWidth, height = 4.5.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroCarousel(
    featuredItem: VideoItem?,
    onWatchClick: (VideoItem) -> Unit,
    modifier: Modifier = Modifier
) {
    HeroCarousel(
        items = if (featuredItem != null) listOf(featuredItem) else emptyList(),
        onWatchClick = onWatchClick,
        modifier = modifier
    )
}
