package com.example.pleosconnect

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import org.json.JSONObject

/**
 * We'r ev 위젯 애니메이션 설정. 르르링 앱 → [WidgetSettingsReceiver] 로 들어와 여기 저장된다.
 *
 * - anim   : auto(주행 상태 따라 전환) | gen(발전 애니메이션 고정) | ev(EV 애니메이션 고정)
 * - motion : normal | calm(느리게) | off(정지 이미지)
 * - toast  : 모드 전환 토스트 표시 여부
 */
data class WidgetSettings(
    val anim: String = "auto",
    val motion: String = "normal",
    val toast: Boolean = true,
) {
    fun toJson(): String = JSONObject()
        .put("anim", anim).put("motion", motion).put("toast", toast)
        .toString()

    companion object {
        const val ACTION = "com.example.pleosconnect.action.WIDGET_SETTINGS"
        const val EXTRA_ANIM = "anim"
        const val EXTRA_MOTION = "motion"
        const val EXTRA_TOAST = "toast"

        private val ANIMS = setOf("auto", "gen", "ev")
        private val MOTIONS = setOf("normal", "calm", "off")

        fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences("widget_settings", Context.MODE_PRIVATE)

        fun load(context: Context): WidgetSettings {
            val p = prefs(context)
            return WidgetSettings(
                anim = p.getString(EXTRA_ANIM, null)?.takeIf { it in ANIMS } ?: "auto",
                motion = p.getString(EXTRA_MOTION, null)?.takeIf { it in MOTIONS } ?: "normal",
                toast = p.getBoolean(EXTRA_TOAST, true),
            )
        }

        /** 외부 앱이 보낸 값이므로 허용된 값만 반영 */
        fun saveFrom(context: Context, intent: Intent) {
            val cur = load(context)
            val next = WidgetSettings(
                anim = intent.getStringExtra(EXTRA_ANIM)?.takeIf { it in ANIMS } ?: cur.anim,
                motion = intent.getStringExtra(EXTRA_MOTION)?.takeIf { it in MOTIONS } ?: cur.motion,
                toast = if (intent.hasExtra(EXTRA_TOAST)) intent.getBooleanExtra(EXTRA_TOAST, true) else cur.toast,
            )
            prefs(context).edit()
                .putString(EXTRA_ANIM, next.anim)
                .putString(EXTRA_MOTION, next.motion)
                .putBoolean(EXTRA_TOAST, next.toast)
                .apply()
        }
    }
}
