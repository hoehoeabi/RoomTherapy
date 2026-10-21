package com.roomtherapy.backend.domain.delivery.dto;

import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryOrderResponseDto(
        Long id,
        String applicantNickname,
        String stageCode,
        String goodsName,
        String recipientName,
        String phone,
        String zipcode,
        String address,
        String detailAddress,
        DeliveryStatus status,
        String statusDescription,
        LocalDateTime createdAt
) {
    public static DeliveryOrderResponseDto from(DeliveryOrder order) {
        return new DeliveryOrderResponseDto(
                order.getId(),
                order.getUser() != null ? order.getUser().getNickname() : "알 수 없음",
                order.getStageCode(),
                order.getGoods() != null ? order.getGoods().getName() : "스테이지 기본 리워드",
                order.getRecipientName(),
                order.getPhone(),
                order.getZipcode(),
                order.getAddress(),
                order.getDetailAddress(),
                order.getStatus(),
                order.getStatus().getDescription(),
                order.getCreatedAt()
        );
    }
}
