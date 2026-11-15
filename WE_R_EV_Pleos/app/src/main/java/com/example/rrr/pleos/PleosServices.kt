package com.example.rrr.pleos

import android.content.Context

/**
 * Pleos SDK 게이트웨이 모음. UI · Planner 는 인터페이스만 본다.
 *
 * 각 Pleos 구현은 SDK 연결·권한에 실패하면 스스로 DEMO 값으로 동작하므로
 * 일반 폰/에뮬레이터에서도 앱이 그대로 뜬다.
 */
object PleosServices {
    var vehicle: VehicleGateway = DemoVehicleGateway()
        private set
    var navi: NaviGateway = DemoNaviGateway()
        private set
    var voice: VoiceGateway = DemoVoiceGateway()
        private set

    private var created = false

    /** 앱에 필요한 Pleos 런타임 권한 (AndroidManifest 와 같은 목록) */
    val permissions = arrayOf(
        "pleos.car.permission.CAR_ENERGY",
        "pleos.car.permission.CONTROL_CAR_ENERGY",
        "pleos.car.permission.CAR_DRIVE",
        "pleos.car.permission.NAVI_ROUTE",
        "pleos.car.permission.TTS_SERVICE",
    )

    /** 처음 호출하면 게이트웨이를 만들고 연결, 다시 호출하면(권한 허용 직후 등) 다시 연결한다 */
    fun start(context: Context) {
        if (!created) {
            val app = context.applicationContext
            vehicle = PleosVehicleGateway(app)
            navi = PleosNaviGateway(app)
            voice = PleosVoiceGateway(app)
            created = true
        }
        vehicle.start()
        navi.start()
        voice.start()
    }

    fun stop() {
        vehicle.stop()
        navi.stop()
        voice.stop()
    }
}
