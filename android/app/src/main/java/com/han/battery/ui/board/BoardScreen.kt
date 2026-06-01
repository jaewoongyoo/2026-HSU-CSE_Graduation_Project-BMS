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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Clear
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    uiState: BoardUiState,
    onCategorySelected: (BoardFilterCategory) -> Unit,
    onFilterSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onDeletePost: (Int) -> Unit,
    onNavigateToHome: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showShareGuide by remember { mutableStateOf(false) }
    var selectedPost by remember { mutableStateOf<BatteryPerformancePost?>(null) }
    val isDark = isSystemInDarkTheme()

    if (showShareGuide) {
        AlertDialog(
            onDismissRequest = { showShareGuide = false },
            title = {
                Text(
                    text = "내 배터리 공유 안내",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "내 배터리 성능(SOH)을 공유하려면 상세 대시보드로 이동해야 합니다.\n\n등록된 배터리 기기를 선택해 들어간 뒤, 우측 상단 더보기(⋮) 메뉴에서 '커뮤니티에 공유'를 누르면 간편하게 공유할 수 있습니다.\n\n기기를 선택하러 이동하시겠습니까?",
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showShareGuide = false
                        onNavigateToHome()
                    }
                ) {
                    Text("이동하기", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showShareGuide = false }
                ) {
                    Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }

    val bgGradient = if (isDark) {
        listOf(
            MaterialTheme.colorScheme.background,
            Color(0xFF020617),
            Color(0xFF0B1329)
        )
    } else {
        listOf(
            Color(0xFFF8FAFC),
            Color(0xFFEFF6FF),
            Color(0xFFF8FAFC)
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showShareGuide = true },
                containerColor = MaterialTheme.colorScheme.primary,
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
                .background(Brush.verticalGradient(bgGradient))
        ) {
            BoardHeader(
                totalPosts = uiState.posts.size,
                filteredPosts = uiState.filteredPosts.size
            )

            // Toss 스타일의 미니멀 검색 창 추가
            SearchBar(
                query = uiState.searchQuery,
                onQueryChanged = onSearchQueryChanged,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
                                onDeletePost = onDeletePost,
                                onClick = { selectedPost = post }
                            )
                        }
                    }
                }

                // 상세 보기 다이얼로그 추가
                if (selectedPost != null) {
                    val post = selectedPost!!
                    AlertDialog(
                        onDismissRequest = { selectedPost = null },
                        title = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "진단 기록 상세",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                IconButton(onClick = { selectedPost = null }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "닫기",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = post.userName,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (post.verified) {
                                                Icon(
                                                    imageVector = Icons.Filled.Verified,
                                                    contentDescription = "검증됨",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = post.createdAt,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    SohBadge(condition = post.condition, soh = post.estimatedSoh)
                                }

                                DeviceInfoBlock(post = post)
                                SohTrendBlock(post = post)
                                PostMetricsRow(post = post)

                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                                Column {
                                    Text(
                                        text = "사용자 후기",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = post.comment,
                                        fontSize = 14.sp,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { selectedPost = null }) {
                                Text("확인")
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                }

                if (isSubsequentLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                        color = MaterialTheme.colorScheme.primary
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
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "같은 폰과 보조배터리를 쓰는 사용자의 진단 기록과 SOH 변화를 비교해보세요.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF1E293B) else Color(0xFFEFF6FF)
    val textColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, fontSize = 12.sp, color = textColor)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun TossFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        isDark -> Color(0xFF1E293B)
        else -> Color(0xFFF1F5F9)
    }
    val contentColor = when {
        selected -> MaterialTheme.colorScheme.primary
        isDark -> Color(0xFFCBD5E1)
        else -> Color(0xFF475569)
    }
    val textWeight = if (selected) FontWeight.Bold else FontWeight.Medium

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .height(36.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = textWeight,
                maxLines = 1
            )
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
            TossFilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = category.label
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
            TossFilterChip(
                selected = selectedValue == option,
                onClick = { onFilterSelected(option) },
                label = option
            )
        }
    }
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun PerformancePostCard(
    post: BatteryPerformancePost,
    currentUserName: String?,
    onDeletePost: (Int) -> Unit,
    onClick: () -> Unit
) {
    val isOwnPost = !currentUserName.isNullOrBlank() && post.userName == currentUserName
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val cardBorderColor = if (isDark) Color(0xFF334155).copy(alpha = 0.7f) else Color(0xFFEFF1F3)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(width = 1.dp, color = cardBorderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = post.userName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (isOwnPost) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "나",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (post.verified) {
                            Icon(
                                imageVector = Icons.Filled.Verified,
                                contentDescription = "검증된 진단 기록",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = post.createdAt,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                tint = MaterialTheme.colorScheme.error,
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
                                    ) { Text("삭제", color = MaterialTheme.colorScheme.error) }
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

            Spacer(modifier = Modifier.height(10.dp))
            DeviceInfoBlock(post = post)

            if (post.comment.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = post.comment,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoBlock(post: BatteryPerformancePost) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f, fill = false)) {
            DeviceInfoTag(
                icon = Icons.Filled.PhoneAndroid,
                text = post.smartphoneModel.displayOrUnknown("폰 기종 미등록")
            )
        }
        Box(modifier = Modifier.weight(1f, fill = false)) {
            DeviceInfoTag(
                icon = Icons.Filled.BatteryChargingFull,
                text = post.powerBankLabel()
            )
        }
    }
}

@Composable
private fun DeviceInfoTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF1F5F9)
    val contentColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF475569)
    Row(
        modifier = Modifier
            .background(bg, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SohTrendBlock(post: BatteryPerformancePost) {
    val trendColor = post.condition.color()
    val first = post.sohHistory.firstOrNull()
    val last = post.sohHistory.lastOrNull()
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.3f) else Color(0xFFF8FAFC)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SOH 변화 추세",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (first != null && last != null) {
                    Text(
                        text = "최근 $first% → $last%",
                        fontSize = 12.sp,
                        color = trendColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val gridColor = MaterialTheme.colorScheme.outlineVariant

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
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, RoundedCornerShape(999.dp))
        )
        Text(
            text = "${condition.label} ${soh?.let { "$it%" } ?: "-"}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun PostMetricsRow(post: BatteryPerformancePost) {
    val isDark = isSystemInDarkTheme()
    val bg = if (isDark) Color(0xFF1E293B).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PostMetricItem(
            label = "SOH",
            value = post.estimatedSoh?.let { "$it%" } ?: "-",
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(1.dp).height(24.dp).background(dividerColor))
        PostMetricItem(
            label = "예상 완충",
            value = post.estimatedFullCharges?.let { "${it}회" } ?: "-",
            modifier = Modifier.weight(1f)
        )
        Box(modifier = Modifier.width(1.dp).height(24.dp).background(dividerColor))
        PostMetricItem(
            label = "누적 사용",
            value = "${post.totalUsageHours.formatOneDecimal()}h",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PostMetricItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
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
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MessageBoardState(
    message: String,
    onRetry: (() -> Unit)? = null
) {
    val isDark = isSystemInDarkTheme()
    val errBg = if (isDark) Color(0xFF3B1E1E) else Color(0xFFFEF2F2)
    val errText = if (isDark) Color(0xFFFFDAD6) else Color(0xFF991B1B)
    val errIconTint = if (isDark) Color(0xFFFFB4AB) else Color(0xFFDC2626)
    val btnBg = if (isDark) MaterialTheme.colorScheme.error else Color(0xFFDC2626)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = errBg),
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
                    tint = errIconTint,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    text = message,
                    color = errText,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                if (onRetry != null) {
                    androidx.compose.material3.Button(
                        onClick = onRetry,
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = btnBg
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

@Composable
private fun SohCondition.color(): Color {
    val isDark = isSystemInDarkTheme()
    return when (this) {
        SohCondition.UNKNOWN -> if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
        SohCondition.GOOD -> if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
        SohCondition.NORMAL -> if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
        SohCondition.CAUTION -> if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChanged: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val containerColor = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        placeholder = {
            Text(
                text = "사용자, 기종 또는 코멘트 검색",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "검색",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChanged("") }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "지우기",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            cursorColor = MaterialTheme.colorScheme.primary
        )
    )
}
