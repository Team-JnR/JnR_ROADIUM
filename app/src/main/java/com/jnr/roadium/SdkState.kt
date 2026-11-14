package com.jnr.roadium

/** Pleos SDK 연결 상태 (시연 도구 패널에 표시) */
enum class SdkState(val label: String) {
    CHECKING("확인 중"),
    CONNECTED("연결됨"),
    FALLBACK("대체 동작"),
    UNAVAILABLE("없음"),
}
