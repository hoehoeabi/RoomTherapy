package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto;
import com.roomtherapy.backend.domain.goods.service.GoodsAuthCodeManager;
import com.roomtherapy.backend.global.common.ApiResponse;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "2. Goods & Code Verification API", description = "굿즈 및 인증코드 유효성 사전 검사 API")
@Slf4j
@RestController
@RequestMapping("/api/goods")
@RequiredArgsConstructor
public class GoodsApiController {

    private final GoodsAuthCodeManager goodsAuthCodeManager;

    @Operation(summary = "굿즈 인증코드 유효성 사전 검증", description = "배송 신청 전 사용자가 입력한 인증코드가 유효한지(Redis 동적 코드, DB 사전 코드 여부 등) 사전 검사합니다.")
    @GetMapping("/verify-code")
    public ApiResponse<GoodsAuthCodeVerifyResponseDto> verifyCode(
            @Parameter(description = "대상 굿즈 PK (없으면 기본 코드)", example = "1") @RequestParam(name = "goodsId", required = false) Long goodsId,
            @Parameter(description = "검증할 인증코드", example = "RT-A8F2K9Z") @RequestParam(name = "code") String code,
            @AuthenticationPrincipal CustomUserDetails userDetails) {

        Long userId = userDetails != null ? userDetails.getUserId() : null;
        GoodsAuthCodeVerifyResponseDto result = goodsAuthCodeManager.verifyOnly(goodsId, code, userId);
        return ApiResponse.success("인증코드 검증 완료", result);
    }
}
