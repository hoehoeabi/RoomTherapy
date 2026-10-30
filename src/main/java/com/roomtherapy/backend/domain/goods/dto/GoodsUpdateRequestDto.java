package com.roomtherapy.backend.domain.goods.dto;

import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record GoodsUpdateRequestDto(
        @NotBlank(message = "굿즈 명칭을 입력해주세요.")
        @Size(max = 100, message = "굿즈 명칭은 최대 100자까지 가능합니다.")
        String name,

        @Size(max = 1000, message = "설명은 최대 1000자까지 가능합니다.")
        String description,

        Integer price,

        String imageUrl,

        @Min(value = 0, message = "재고 수량은 0개 이상이어야 합니다.")
        Integer stockQuantity,

        GoodsStatus status,

        // 추가할 인증코드 텍스트 (줄바꿈 또는 쉼표 구분)
        String additionalAuthCodesInput
) {
    public GoodsUpdateRequestDto {
        price = price != null ? Math.max(0, price) : 0;
        stockQuantity = stockQuantity != null ? Math.max(0, stockQuantity) : 0;
        description = description != null ? description : "";
        imageUrl = imageUrl != null ? imageUrl : "";
        additionalAuthCodesInput = additionalAuthCodesInput != null ? additionalAuthCodesInput : "";
    }

    public static GoodsUpdateRequestDto from(Goods goods) {
        return new GoodsUpdateRequestDto(
                goods.getName(),
                goods.getDescription(),
                goods.getPrice(),
                goods.getImageUrl(),
                goods.getStockQuantity(),
                goods.getStatus(),
                ""
        );
    }

    public List<String> extractAdditionalAuthCodes() {
        List<String> result = new ArrayList<>();
        if (additionalAuthCodesInput != null && !additionalAuthCodesInput.isBlank()) {
            Arrays.stream(additionalAuthCodesInput.split("[\r\n,]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(result::add);
        }
        return result.stream().distinct().toList();
    }

    public GoodsUpdateRequestDto withImageUrl(String newImageUrl) {
        return new GoodsUpdateRequestDto(name, description, price, newImageUrl, stockQuantity, status, additionalAuthCodesInput);
    }
}
