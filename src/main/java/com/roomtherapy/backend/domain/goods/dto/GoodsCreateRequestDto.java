package com.roomtherapy.backend.domain.goods.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public record GoodsCreateRequestDto(
        @NotBlank(message = "굿즈 명칭을 입력해주세요.")
        @Size(max = 100, message = "굿즈 명칭은 최대 100자까지 가능합니다.")
        String name,

        @Size(max = 1000, message = "설명은 최대 1000자까지 가능합니다.")
        String description,

        Integer price,

        String imageUrl,

        @Min(value = 1, message = "재고 수량은 최소 1개 이상이어야 합니다.")
        Integer stockQuantity,

        // 판매자가 입력한 인증코드 목록 텍스트 (줄바꿈 또는 쉼표 구분)
        String authCodesInput,

        // 또는 직접 전달된 인증코드 리스트
        List<String> authCodes
) {
    public GoodsCreateRequestDto {
        price = price != null ? Math.max(0, price) : 0;
        stockQuantity = stockQuantity != null && stockQuantity >= 1 ? stockQuantity : 1;
        description = description != null ? description : "";
        imageUrl = imageUrl != null ? imageUrl : "";
        authCodesInput = authCodesInput != null ? authCodesInput : "";
        authCodes = authCodes != null ? authCodes : List.of();
    }

    public List<String> extractAuthCodes() {
        List<String> result = new ArrayList<>();
        if (authCodes != null && !authCodes.isEmpty()) {
            authCodes.stream()
                    .filter(c -> c != null && !c.isBlank())
                    .map(String::trim)
                    .forEach(result::add);
        }
        if (authCodesInput != null && !authCodesInput.isBlank()) {
            Arrays.stream(authCodesInput.split("[\r\n,]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(result::add);
        }
        return result.stream().distinct().toList();
    }

    public GoodsCreateRequestDto withImageUrl(String newImageUrl) {
        return new GoodsCreateRequestDto(name, description, price, newImageUrl, stockQuantity, authCodesInput, authCodes);
    }
}
