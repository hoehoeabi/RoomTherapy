package com.roomtherapy.backend.domain.delivery.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DeliveryStatus {

    ORDERED("접수 완료"),
    PREPARING("배송 준비중"),
    SHIPPED("발송 완료"),
    DELIVERED("배송 완료"),
    CANCELLED("취소됨");

    private final String description;
}
