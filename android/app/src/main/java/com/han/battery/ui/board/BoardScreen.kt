package com.han.battery.ui.board

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.han.battery.data.model.BatteryPerformancePost
import com.han.battery.data.model.SohCondition
import com.han.battery.ui.theme.Blue600
import com.han.battery.ui.theme.Slate50

@Composable
fun BoardScreen(
    uiState: BoardUiState,
    onCategorySelected: (BoardFilterCategory) -> Unit,
    onFilterSelected: (String) -> Unit,
    onDeletePost: (Int) -> Unit,
    onNavigateToHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Slate50,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToHome,
                containerColor = Blue600,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp),
                icon = {
                    Icon(
                        imageVector = Icons.Filled.BatteryChargingFull,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                text = {
                    Text(
                        text = "내 배터리 공유하기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFF8FAFC),
                            Color(0xFFEFF6FF),
                            Color(0xFFF8FAFC)
                        )
                    )
                )
        ) {
            BoardHeader(
                totalPosts = uiState.posts.size,
                filteredPosts = uiState.filteredPosts.size
            )

            CategoryFilterBar(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = onCategorySelected
            )

            OptionFilterBar(
                options = uiState.filterOptions,
                selectedValue = uiState.selectedValue,
                onFilterSelected = onFilterSelected
            )

            val isInitialLoading = uiState.isLoading && uiState.posts.isEmpty()
            val isSubsequentLoading = uiState.isLoading && uiState.posts.isNotEmpty()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (isInitialLoading) {
                    LoadingBoardState()
                } else if (uiState.errorMessage != null) {
                    MessageBoardState(message = uiState.errorMessage, onRetry = onRetry)
                } else if (uiState.filteredPosts.isEmpty()) {
                    EmptyBoardState()
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp), // 하단 FAB 여백 확보를 위해 bottom padding을 20.dp에서 80.dp로 확대
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(uiState.filteredPosts, key = { it.id }) { post ->
                            PerformancePostCard(
                                post = post,
                                currentUserName = uiState.currentUserName,
                                onDeletePost = onDeletePost
                            )
                        }
                    }
                }

                if (isSubsequentLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = Blue600
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardHeader(
    totalPosts: Int,
    filteredPosts: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Text(
            text = "SOH 경험 공유",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF0F172A)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "같은 폰과 보조배터리를 쓰는 사용자의 진단 기록과 SOH 변화를 비교해보세요.",
            fontSize = 14.sp,
            color = Color(0xFF64748B),
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryPill(label = "공유 기록", value = "${totalPosts}건")
            SummaryPill(label = "현재 보기", value = "${filteredPosts}건")
        }
    }
}

@Composable
private fun SummaryPill(label: String, value: String) {
    Surface(
        color = Color.White.copy(alpha = 0.85f),
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = label, fontSize = 12.sp, color = Color(0xFF64748B))
            Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Blue600)
        }
    }
}

@Composable
private fun CategoryFilterBar(
    selectedCategory: BoardFilterCategory,
    onCategorySelected: (BoardFilterCategory) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(BoardFilterCategory.entries) { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category.label, maxLines = 1) }
            )
        }
    }
}

@Composable
private fun OptionFilterBar(
    options: List<String>,
    selectedValue: String?,
    onFilterSelected: (String) -> Unit
) {
    if (options.isEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        return
    }

    Spacer(modifier = Modifier.height(8.dp))
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { option ->
            FilterChip(
                selected = selectedValue == option,
                onClick = { onFilterSelected(option) },
                label = { Text(option, maxLines = 1) }
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun PerformancePostCard(
    post: BatteryPerformancePost,
    currentUserName: String?,
    onDeletePost: (Int) -> Unit
) {
    val isOwnPost = !currentUserName.isNullOrBlank() && post.userName == currentUserName

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = post.userName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        if (isOwnPost) {
                            Surface(
                                color = Blue600.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "나",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Blue600,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (post.verified) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "검증된 진단 기록",
                                tint = Blue600,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Text(
                        text = post.createdAt,
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isOwnPost && post.sharedReportId != null) {
                        var showConfirmDelete by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { showConfirmDelete = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "공유 삭제",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        if (showConfirmDelete) {
                            AlertDialog(
                                onDismissRequest = { showConfirmDelete = false },
                                title = { Text("공유 취소") },
                                text = { Text("이 분석 결과 공유를 취소하시겠습니까?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showConfirmDelete = false
                                            onDeletePost(post.sharedReportId)
                                        }
                                    ) { Text("삭제", color = Color(0xFFEF4444)) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showConfirmDelete = false }) { Text("취소") }
                                }
                            )
                        }
                    }
                    SohBadge(condition = post.condition, soh = post.estimatedSoh)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            DeviceInfoBlock(post = post)
            Spacer(modifier = Modifier.height(14.dp))

            SohTrendBlock(post = post)

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFE2E8F0))
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricTile(
                    label = "SOH",
                    value = post.estimatedSoh?.let { "$it%" } ?: "-",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "예상 완충",
                    value = post.estimatedFullCharges?.let { "${it}회" } ?: "-",
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "누적 사용",
                    value = "${post.totalUsageHours.formatOneDecimal()}h",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = post.comment,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = Color(0xFF334155),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DeviceInfoBlock(post: BatteryPerformancePost) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = {},
            label = { Text(post.smartphoneModel.displayOrUnknown("폰 기종 미등록")) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.PhoneAndroid,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
        AssistChip(
            onClick = {},
            label = { Text(post.powerBankLabel()) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.BatteryChargingFull,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
private fun SohTrendBlock(post: BatteryPerformancePost) {
    val trendColor = post.condition.color()
    val first = post.sohHistory.firstOrNull()
    val last = post.sohHistory.lastOrNull()

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SOH 변화",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
                if (first != null && last != null) {
                    Text(
                        text = "최근 $first% -> $last%",
                        fontSize = 12.sp,
                        color = trendColor,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            if (post.sohHistory.size >= 2) {
                SohMiniChart(
                    points = post.sohHistory,
                    lineColor = trendColor
                )
            } else {
                Text(
                    text = if (post.estimatedSoh == null) {
                        "아직 SOH 분석 결과가 없습니다."
                    } else {
                        "추세 그래프는 누적 SOH 기록이 쌓이면 표시됩니다."
                    },
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
private fun SohMiniChart(
    points: List<Int>,
    lineColor: Color
) {
    val safePoints = points.takeIf { it.size >= 2 } ?: return

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val minValue = (safePoints.minOrNull() ?: 0).coerceAtMost(80)
        val maxValue = (safePoints.maxOrNull() ?: 100).coerceAtLeast(100)
        val valueRange = (maxValue - minValue).coerceAtLeast(1)
        val stepX = size.width / (safePoints.lastIndex)

        val offsets = safePoints.mapIndexed { index, value ->
            val normalized = (value - minValue).toFloat() / valueRange.toFloat()
            Offset(
                x = stepX * index,
                y = size.height - (normalized * size.height)
            )
        }

        val gridColor = Color(0xFFE2E8F0)
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 2f
        )
        drawLine(
            color = gridColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5f
        )

        offsets.zipWithNext().forEach { (start, end) ->
            drawLine(
                color = lineColor,
                start = start,
                end = end,
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
        }

        offsets.forEach { point ->
            drawCircle(
                color = Color.White,
                radius = 7f,
                center = point
            )
            drawCircle(
                color = lineColor,
                radius = 4.5f,
                center = point
            )
        }
    }
}

@Composable
private fun SohBadge(condition: SohCondition, soh: Int?) {
    val color = condition.color()
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = condition.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = soh?.let { "$it%" } ?: "-",
                fontSize = 12.sp,
                color = color
            )
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, fontSize = 11.sp, color = Color(0xFF64748B))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )
        }
    }
}

@Composable
private fun EmptyBoardState() {
    MessageBoardState(message = "선택한 조건에 맞는 공유 기록이 없습니다.")
}

@Composable
private fun LoadingBoardState() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Blue600)
    }
}

@Composable
private fun MessageBoardState(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "경고",
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = message,
                    color = Color(0xFF991B1B),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                if (onRetry != null) {
                    androidx.compose.material3.Button(
                        onClick = onRetry,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "다시 시도", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun SohCondition.color(): Color {
    return when (this) {
        SohCondition.UNKNOWN -> Color(0xFF64748B)
        SohCondition.GOOD -> Color(0xFF15803D)
        SohCondition.NORMAL -> Color(0xFFB45309)
        SohCondition.CAUTION -> Color(0xFFDC2626)
    }
}

private fun String?.displayOrUnknown(fallback: String): String {
    return this?.takeIf { it.isNotBlank() } ?: fallback
}

private fun BatteryPerformancePost.powerBankLabel(): String {
    val manufacturer = powerBankManufacturer.displayOrUnknown("제조사 미등록")
    val capacity = powerBankCapacityMah?.let { " ${it}mAh" }.orEmpty()
    return "$manufacturer $powerBankModel$capacity"
}

private fun Double.formatOneDecimal(): String {
    return String.format("%.1f", this)
}
