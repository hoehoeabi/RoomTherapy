package com.roomtherapy.backend.domain.goods.dto;

public record UnityCodeIssueResponseDto(
        Long goodsId,
        String goodsName,
        String code,
        long expiresInSeconds
) {
}
