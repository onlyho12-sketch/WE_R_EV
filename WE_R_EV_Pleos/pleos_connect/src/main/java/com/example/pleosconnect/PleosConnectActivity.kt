package com.example.pleosconnect

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf

/**
 * Pleos Connect 차량 화면 시뮬레이션 (계기판 · 내비 · We'r ev 르르르 위젯 · 하단 독).
 * 화면은 Compose 로 직접 그린다 → PleosConnectScreen.kt
 */
class PleosConnectActivity : ComponentActivity() {

    private val settings = mutableStateOf(WidgetSettings())
    private val appToastKey = mutableIntStateOf(0)
    private val appToast = mutableStateOf<String?>(null)

    // 르르링 앱에서 설정이 바뀌면 (Receiver → prefs) 화면에 바로 반영 + 토스트
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> reload(fromApp = true) }
    private var reloadPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings.value = WidgetSettings.load(this)
        setContent { PleosConnectScreen(settings.value, appToastKey.intValue, appToast.value) }
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onStart() {
        super.onStart()
        WidgetSettings.prefs(this).registerOnSharedPreferenceChangeListener(prefsListener)
        reload(fromApp = false) // 화면이 꺼져 있던 동안 바뀐 설정 반영
    }

    override fun onStop() {
        WidgetSettings.prefs(this).unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onStop()
    }

    private fun reload(fromApp: Boolean) {
        // anim/motion/toast 가 한 번에 저장되며 리스너가 여러 번 불리므로 한 번으로 합침
        if (reloadPending) return
        reloadPending = true
        window.decorView.post {
            reloadPending = false
            val next = WidgetSettings.load(this)
            if (next == settings.value) return@post
            settings.value = next
            if (fromApp) {
                appToast.value = settingsToastText(next)
                appToastKey.intValue++
            }
        }
    }
}
