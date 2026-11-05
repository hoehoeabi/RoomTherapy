package com.roomtherapy.backend.domain.goods.dto;

import com.roomtherapy.backend.domain.goods.entity.GoodsAuthCode;

import java.time.LocalDateTime;

/**
 * 인증코드 관리 화면에서 굿즈 정보 및 코드 메타데이터를 함께 전달하는 DTO
 */
public record GoodsAuthCodeItemDto(
        Long id,
        Long goodsId,
        String goodsName,
        String code,
        LocalDateTime createdAt
) {
    public static GoodsAuthCodeItemDto from(GoodsAuthCode entity) {
        return new GoodsAuthCodeItemDto(
                entity.getId(),
                entity.getGoods() != null ? entity.getGoods().getId() : null,
                entity.getGoods() != null ? entity.getGoods().getName() : "미지정 굿즈",
                entity.getCode(),
                entity.getCreatedAt()
        );
    }
}
