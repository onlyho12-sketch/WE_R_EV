package com.example.pleosconnect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** 르르링 앱 '위젯 설정'에서 보낸 애니메이션 설정을 저장. 화면이 떠 있으면 Activity가 prefs 변경을 받아 바로 반영한다. */
class WidgetSettingsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != WidgetSettings.ACTION) return
        WidgetSettings.saveFrom(context, intent)
    }
}
