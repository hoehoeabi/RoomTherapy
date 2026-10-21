package com.roomtherapy.backend.domain.goods.dto;

import com.roomtherapy.backend.domain.goods.entity.Goods;

public record UnityGoodsResponseDto(
        Long id,
        String name,
        String description,
        int price,
        String imageUrl,
        int stockQuantity,
        String status
) {
    public static UnityGoodsResponseDto from(Goods goods) {
        return new UnityGoodsResponseDto(
                goods.getId(),
                goods.getName(),
                goods.getDescription(),
                goods.getPrice(),
                goods.getImageUrl(),
                goods.getStockQuantity(),
                goods.getStatus().name()
        );
    }
}
