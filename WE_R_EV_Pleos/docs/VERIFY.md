# 검증 기록 (2026-11-14)

## 환경
- AVD `Pleos_Connect_v2.1.0` (시스템 이미지: `android-35-ext15 / android-automotive`, 2560×1440)
- **이 AVD에는 Pleos 서비스가 없다.** `pm list packages`에 `ai.pleos.playground.*` 패키지가 없고
  `pleos.car.permission.*` 권한도 시스템에 정의돼 있지 않다 (`pm grant` → `Unknown permission`).
  기기 프로필만 Pleos이고 이미지는 일반 Android Automotive다.
- 따라서 **Pleos SDK의 실제 호출 결과는 아직 한 번도 확인하지 못했다.** 공식 Pleos Connect 에뮬레이터 이미지에서 다시 확인해야 한다.

## SDK별 결과 (이 AVD)
| SDK | 결과 | 로그 |
|---|---|---|
| Vehicle 2.3.2 | DEMO로 대체 | `getEvBatteryStateOfCharge need Permission` |
| NaviHelper 3.0.5 | DEMO로 대체 (약 20초 바인드 재시도 후) | `service has not started yet` |
| TextToSpeech 3.3.1 | DEMO로 대체 | `Permission pleos.car.permission.TTS_SERVICE is required` |

세 경우 모두 앱은 죽지 않고 데모 값으로 동작했다 (화면 배지 `DEMO`).

## 화면으로 확인한 것
- WE'R Score: 스크롤 없이 한 화면 (2560×1440)
- WE'R Rewards: 한 화면, 포인트는 기록에서 계산 (한국 123P)
- 한국 퇴근길: 기본 모드 813원 → We'r ev 542원, 271원 절약
- 미국 출근길: $ · mi · gal · TOU, 스키매틱 지도, 기본 모드 $1.90 → $1.04, $0.86 절약
- 시연 도구: 트립 시작 → 결정적 순간 음성(문구 토스트) → 다시 누르면 "트립당 1회" 안내 → 트립 종료 시 이번 달 절약 $7.30 → $8.16

## 확인하지 못한 것 (공식 에뮬레이터 필요)
- Virtual Console에서 SOC를 바꾸면 제안이 바뀌는지
- 내비 목적지 자동 수신, 경유지 등록, 도착 이벤트
- 실제 음성 출력, 충전 한도·충전 스위치 쓰기
- 권한이 런타임 팝업으로 뜨는지, 매니페스트 선언만으로 되는지
