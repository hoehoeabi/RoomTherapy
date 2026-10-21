package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.dto.UnityCodeIssueResponseDto;
import com.roomtherapy.backend.domain.goods.dto.UnityGoodsResponseDto;
import com.roomtherapy.backend.domain.goods.service.GoodsService;
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
@Slf4j
@RestController
@RequestMapping("/api/unity/goods")
@RequiredArgsConstructor
public class UnityGoodsApiController {

    private final GoodsService goodsService;

    /**
     * 유니티에 노출할 굿즈 목록 조회
     */
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
    @PostMapping("/{goodsId}/issue-code")
    public ResponseEntity<UnityCodeIssueResponseDto> issueAuthCode(
            @PathVariable Long goodsId,
            @RequestParam(name = "durationMinutes", defaultValue = "60") int durationMinutes) {
        UnityCodeIssueResponseDto response = goodsService.issueDynamicCodeForUnity(goodsId, Duration.ofMinutes(durationMinutes));
        return ResponseEntity.ok(response);
    }
}
