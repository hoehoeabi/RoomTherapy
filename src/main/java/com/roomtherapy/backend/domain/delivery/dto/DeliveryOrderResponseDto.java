package com.roomtherapy.backend.domain.delivery.dto;

import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;

import java.time.LocalDateTime;

public record DeliveryOrderResponseDto(
        Long id,
        String applicantNickname,
        String stageCode,
        String goodsName,
        String goodsImageUrl,
        String recipientName,
        String phone,
        String zipcode,
        String address,
        String detailAddress,
        String feedback,
        DeliveryStatus status,
        String statusDescription,
        boolean isModified,
        boolean isGoodsDeleted,
        boolean isGoodsInactive,
        String goodsStatusBadge,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public boolean isCanModify() {
        return this.status == DeliveryStatus.ORDERED || this.status == DeliveryStatus.PREPARING;
    }

    public java.util.List<String> goodsImageList() {
        if (goodsImageUrl == null || goodsImageUrl.isBlank()) {
            return java.util.List.of();
        }
        return java.util.Arrays.stream(goodsImageUrl.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }

    public static DeliveryOrderResponseDto from(DeliveryOrder order) {
        boolean isDeleted = order.isGoodsDeleted();
        boolean isInactive = order.isGoodsInactive();
        String goodsBadge = null;
        if (isDeleted) {
            goodsBadge = "삭제된 굿즈";
        } else if (isInactive) {
            goodsBadge = "숨김 굿즈";
        }

        return new DeliveryOrderResponseDto(
                order.getId(),
                order.getUser() != null ? order.getUser().getNickname() : "알 수 없음",
                order.getStageCode(),
                order.getEffectiveGoodsName(),
                order.getGoods() != null ? order.getGoods().getImageUrl() : "",
                order.getRecipientName(),
                order.getPhone(),
                order.getZipcode(),
                order.getAddress(),
                order.getDetailAddress(),
                order.getFeedback(),
                order.getStatus(),
                order.getStatus().getDescription(),
                order.isModified(),
                isDeleted,
                isInactive,
                goodsBadge,
                order.getCreatedAt(),
                order.getUpdatedAt()
        );
    }
}
