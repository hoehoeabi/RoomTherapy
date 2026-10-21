package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.dto.UnityCodeIssueResponseDto;
import com.roomtherapy.backend.domain.goods.dto.UnityGoodsResponseDto;
import com.roomtherapy.backend.domain.goods.service.GoodsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * 유니티(게임) 클라이언트 연동용 REST API 컨트롤러
 * 1. 굿즈 목록 제공 (게임 내 리워드 UI 구성용)
 * 2. 굿즈 PK 전송 시 동적 인증코드 발급 (스테이지 클리어 시 Redis 기반 일회용 코드 발급)
 */
@Tag(name = "1. Unity Goods API", description = "유니티(게임) 클라이언트 연동용 굿즈 목록 조회 및 동적 인증코드 발급 API")
@Slf4j
@RestController
@RequestMapping("/api/unity/goods")
@RequiredArgsConstructor
public class UnityGoodsApiController {

    private final GoodsService goodsService;

    /**
     * 유니티에 노출할 굿즈 목록 조회
     */
    @Operation(summary = "유니티 노출용 굿즈 목록 조회", description = "게임 내 리워드 UI 구성을 위한 활성 상태 굿즈 목록을 반환합니다.")
    @GetMapping
    public ResponseEntity<List<UnityGoodsResponseDto>> getGoodsList() {
        List<UnityGoodsResponseDto> list = goodsService.getGoodsForUnity();
        return ResponseEntity.ok(list);
    }

    /**
     * 유니티에서 굿즈 PK를 전달받아 Redis 기반 동적 인증코드 발급
     * @param goodsId 굿즈 PK
     * @param durationMinutes 유효시간(분) (기본 60분)
     */
    @Operation(summary = "스테이지 클리어 시 굿즈 동적 인증코드 발급", description = "유저가 스테이지 클리어 시 굿즈 PK를 전송하면, Redis에 TTL(기본 60분)로 저장되는 1회용 고유 난수 코드(예: RT-A8F2K9Z)를 즉시 발급합니다.")
    @PostMapping("/{goodsId}/issue-code")
    public ResponseEntity<UnityCodeIssueResponseDto> issueAuthCode(
            @Parameter(description = "리워드 굿즈 PK ID", example = "1") @PathVariable Long goodsId,
            @Parameter(description = "인증코드 유효 시간(분 단위, 기본 60분)", example = "60") @RequestParam(name = "durationMinutes", defaultValue = "60") int durationMinutes) {
        UnityCodeIssueResponseDto response = goodsService.issueDynamicCodeForUnity(goodsId, Duration.ofMinutes(durationMinutes));
        return ResponseEntity.ok(response);
    }
}
