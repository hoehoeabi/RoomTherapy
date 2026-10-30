package com.roomtherapy.backend.domain.goods.dto;

import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;

import java.time.LocalDateTime;
import java.util.List;

public record GoodsResponseDto(
        Long id,
        String name,
        String description,
        int price,
        String imageUrl,
        int stockQuantity,
        GoodsStatus status,
        Long sellerId,
        String sellerNickname,
        int authCodeCount,
        List<String> authCodes,
        LocalDateTime createdAt
) {
    public static GoodsResponseDto from(Goods goods, List<String> authCodes) {
        return new GoodsResponseDto(
                goods.getId(),
                goods.getName(),
                goods.getDescription(),
                goods.getPrice(),
                goods.getImageUrl(),
                goods.getStockQuantity(),
                goods.getStatus(),
                goods.getSeller() != null ? goods.getSeller().getId() : null,
                goods.getSeller() != null ? goods.getSeller().getNickname() : null,
                authCodes != null ? authCodes.size() : 0,
                authCodes != null ? authCodes : List.of(),
                goods.getCreatedAt()
        );
    }

    public List<String> imageList() {
        if (imageUrl == null || imageUrl.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(imageUrl.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}
