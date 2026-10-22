package com.roomtherapy.backend.domain.goods.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "인증코드 유효성 사전 검사 결과 DTO")
public record GoodsAuthCodeVerifyResponseDto(
        @Schema(description = "유효 여부", example = "true")
        boolean valid,

        @Schema(description = "검증 안내 메시지", example = "유효한 인증코드입니다!")
        String message,

        @Schema(description = "인증코드 유형 (REDIS_DYNAMIC, DB_STATIC, GENERAL, DEFAULT)", example = "REDIS_DYNAMIC")
        String codeType
) {}
