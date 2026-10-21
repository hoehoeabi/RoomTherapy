package com.roomtherapy.backend.domain.goods.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GoodsStatus {

    ACTIVE("활성 / 판매중"),
    SOLD_OUT("품절"),
    INACTIVE("비활성화 / 숨김");

    private final String description;
}
