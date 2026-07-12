package com.hermes.broker.trading.domain;

public enum OrderStatus {
    PENDING,   // 주문 전/대기
    SUBMITTED, // 주문 완료 (제출됨)
    EXECUTED,  // 체결 완료
    CANCELED,  // 주문 취소
    FAILED     // 주문 실패
}
