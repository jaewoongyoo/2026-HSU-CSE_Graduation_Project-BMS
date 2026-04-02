package com.han.battery.ui.components.animations

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween

/**
 * 토스 스타일의 부드러운 페이지 전환 애니메이션
 * - 오른쪽에서 왼쪽으로 슬라이드 인 (새로운 화면)
 * - 왼쪽으로 슬라이드 아웃 (이전 화면)
 */
fun <T> AnimatedContentTransitionScope<T>.slideInFromRightTransition(): ContentTransform {
    val enterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 350)
    ) + fadeIn(animationSpec = tween(durationMillis = 350))
    
    val exitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(durationMillis = 350)
    ) + fadeOut(animationSpec = tween(durationMillis = 350))
    
    return ContentTransform(enterTransition, exitTransition)
}

/**
 * 토스 스타일의 뒤로가기 애니메이션
 * - 왼쪽에서 오른쪽으로 슬라이드 인 (돌아올 화면)
 * - 오른쪽으로 슬라이드 아웃 (이전 화면)
 */
fun <T> AnimatedContentTransitionScope<T>.slideInFromLeftTransition(): ContentTransform {
    val enterTransition = slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth },
        animationSpec = tween(durationMillis = 350)
    ) + fadeIn(animationSpec = tween(durationMillis = 350))
    
    val exitTransition = slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(durationMillis = 350)
    ) + fadeOut(animationSpec = tween(durationMillis = 350))
    
    return ContentTransform(enterTransition, exitTransition)
}


/**
 * 페이드 + 스케일 애니메이션 (대화상자나 팝업용)
 */
fun dialogEnterTransition(): EnterTransition {
    return scaleIn(
        animationSpec = tween(durationMillis = 250),
        initialScale = 0.8f
    ) + fadeIn(animationSpec = tween(durationMillis = 250))
}

fun dialogExitTransition(): ExitTransition {
    return scaleOut(
        animationSpec = tween(durationMillis = 250),
        targetScale = 0.8f
    ) + fadeOut(animationSpec = tween(durationMillis = 250))
}

