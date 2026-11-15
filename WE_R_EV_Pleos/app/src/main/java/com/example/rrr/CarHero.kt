package com.example.rrr

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.SceneView
import io.github.sceneview.SurfaceType
import io.github.sceneview.math.Position
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelInstance
import io.github.sceneview.rememberModelLoader
import kotlinx.coroutines.launch

/** 카툰 네이비 싼타페 (unlit 재질이라 조명과 무관하게 일러스트 색 그대로 보임) */
private const val MODEL_FILE = "santafe_cartoon_navy.glb"

/** 처음 보이는 각도: 앞모습이 화면 왼쪽을 향한 3/4 정면 (Y축 회전, 도 — 이 모델은 앞이 -Z 라 180° 더함) */
private const val CAR_START_YAW_DEG = 145f

/** 고정 카메라 기준 차 크기 (클수록 크게 보임) */
private const val CAR_SIZE_UNITS = 2.45f

/** 손을 떼고 가만히 두면 천천히 도는 속도 (도/초) */
private const val IDLE_SPIN_DEG_PER_SEC = 12f

/** 드래그 1px 당 회전 각도 */
private const val DRAG_DEG_PER_PX = 0.35f

/**
 * 차량 일러스트 (drawable/santafe.png, 투명 배경 + 바닥 그림자 포함) — 오늘의 제안 패널 등 작은 자리용
 */
@Composable
fun CarHero(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.santafe),
        contentDescription = "싼타페 EREV",
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * 홈 화면용 3D 싼타페: 좌우로 드래그해서 돌려볼 수 있고, 손을 떼면 관성으로 조금 더 돌다가
 * 가만히 두면 천천히 혼자 돈다.
 * SceneView(TextureView) 가 터치를 먹지 않도록 투명 레이어를 위에 덮어 제스처를 받는다.
 */
@Composable
fun CarHero3D(modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val scope = rememberCoroutineScope()

    val yaw = remember { Animatable(CAR_START_YAW_DEG) }
    var touching by remember { mutableStateOf(false) }
    var lastTouchAt by remember { mutableStateOf(-1L) }

    // 가만히 있으면 천천히 회전 (처음엔 3/4 정면을 4초 보여주고, 터치 후엔 3초 뒤부터)
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) {
            withFrameMillis { now ->
                if (lastTouchAt < 0) lastTouchAt = now + 1000
                if (last >= 0 && !touching && !yaw.isRunning && now - lastTouchAt > 3000) {
                    scope.launch { yaw.snapTo(yaw.value + IDLE_SPIN_DEG_PER_SEC * (now - last) / 1000f) }
                }
                last = now
            }
        }
    }

    Box(modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            // 흰 배경 위에 자연스럽게 얹히도록 투명 배경
            surfaceType = SurfaceType.TextureSurface,
            isOpaque = false,
            engine = engine,
            modelLoader = modelLoader,
            cameraManipulator = null, // 카메라는 고정, 차를 돌린다
        ) {
            rememberModelInstance(modelLoader, MODEL_FILE)?.let { instance ->
                ModelNode(
                    modelInstance = instance,
                    scaleToUnits = CAR_SIZE_UNITS,
                    centerOrigin = Position(0f, 0f, 0f),
                    position = Position(0f, -0.2f, 0f),
                    rotation = Float3(0f, yaw.value, 0f),
                )
            }
        }

        // 제스처 레이어
        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        touching = true
                        tracker.resetTracking()
                        scope.launch { yaw.stop() }
                    },
                    onHorizontalDrag = { change, dx ->
                        tracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { yaw.snapTo(yaw.value + dx * DRAG_DEG_PER_PX) }
                    },
                    onDragEnd = {
                        touching = false
                        lastTouchAt = System.nanoTime() / 1_000_000
                        val v = tracker.calculateVelocity().x * DRAG_DEG_PER_PX
                        scope.launch {
                            yaw.animateDecay(v, exponentialDecay(frictionMultiplier = 2.5f))
                            lastTouchAt = System.nanoTime() / 1_000_000
                        }
                    },
                    onDragCancel = { touching = false },
                )
            }
        )
    }
}
