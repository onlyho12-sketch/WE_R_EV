# 르르링 워크플로우 (SDK · API 연결 최종안)

```mermaid
%%{init: {'theme':'base','flowchart':{'padding':24,'nodeSpacing':40,'rankSpacing':50},'themeVariables':{'fontSize':'15px','primaryColor':'#ececec','primaryBorderColor':'#9a9a9a','primaryTextColor':'#222','lineColor':'#666','clusterBkg':'#fafafa','clusterBorder':'#777'}}}%%
flowchart TB
    subgraph IN[입력]
        direction LR
        A1["출발지 · 도착지<br/>직접 입력 · 자주 가는 경로"]
        A2["음성 명령<br/>Gleo AI"]
        A3["차량 상태<br/>Pleos Vehicle SDK"]
        A1 ~~~ A2 ~~~ A3
    end

    subgraph EXT[외부 데이터]
        direction LR
        E1["경로 · 거리 · 시간<br/>지도 경로 API"]
        E2["충전소 위치 · 요금<br/>환경부 충전소 API"]
        E3["주유소 가격<br/>오피넷 유가 API"]
        E1 ~~~ E2 ~~~ E3
    end

    subgraph APP[르르링 · 연료 공급 전략]
        direction LR
        B1["전기 주행 가능 거리<br/>배터리 − 예비 10%"]
        B2{"시간 우선?"}
        B3{"목적지<br/>충전 인프라"}
        C0["기름으로 바로 출발"]
        C1["집 충전<br/>배터리 다 쓰고 들어가기"]
        C2["완속<br/>주차하는 동안 충전"]
        C3["급속만<br/>건너뛰고 기름으로"]
        C4["없음<br/>배터리 아끼고 발전"]
        D1["오늘의 제안<br/>공급 타임라인 · 기름 전략"]
        B1 --> B2
        B2 -->|예| C0
        B2 -->|아니오| B3
        B3 -->|집| C1
        B3 -->|완속| C2
        B3 -->|급속| C3
        B3 -->|없음| C4
        C0 --> D1
        C1 --> D1
        C2 --> D1
        C3 --> D1
        C4 --> D1
    end

    subgraph OUT[운전자 · 차량]
        direction LR
        F1["채우기 행동 카드"]
        F2["심야 충전 예약<br/>Vehicle SDK"]
        F3["주유소 경유지 등록<br/>NaviHelper"]
        F4["주행 중 르르르 위젯<br/>내비 위 한 줄"]
        F1 -->|승인| F2
        F1 -->|승인| F3
        F3 -->|출발| F4
    end

    IN --> APP
    EXT --> APP
    APP -->|제안| OUT

    classDef default fill:#ececec,stroke:#9a9a9a,color:#222
    style IN fill:#fafafa,stroke:#777
    style EXT fill:#fafafa,stroke:#777
    style APP fill:#fafafa,stroke:#777
    style OUT fill:#fafafa,stroke:#777
```

## 연결 현황 (데모 → 최종)

| 구간 | 지금 (데모) | 최종 연결 |
|---|---|---|
| 차량 배터리 · 연료 | 화면의 −/+ 값, 프리셋 | Pleos Vehicle SDK |
| 음성 출발/도착 | 앱 안 Gleo 버튼 + 인텐트(`SET_TRIP`) | Gleo AI App Schema → Gleo Action |
| 경로 · 거리 · 시간 | OSRM 공개 API (실제 호출) | 상용 지도 경로 API |
| 충전소 위치 · 요금 | `Places.kt` 데모 DB | 환경부 전기차 충전소 API |
| 주유소 가격 | `Places.kt` 데모 가격 | 오피넷 유가 API |
| 심야 충전 예약 | 버튼 완료 상태만 | 차량 충전 예약 (SDK) |
| 주유소 경유지 | 지도에 표시만 | NaviHelper 경유지 등록 |
| 내비 위젯 | 정적 목업 화면 | 내비 위 오버레이 위젯 |
