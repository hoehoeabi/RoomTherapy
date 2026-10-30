package com.roomtherapy.backend.domain.goods.service;

import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsUpdateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.UnityCodeIssueResponseDto;
import com.roomtherapy.backend.domain.goods.dto.UnityGoodsResponseDto;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsAuthCode;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.goods.validator.RedisGoodsAuthCodeValidator;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.error.BusinessException;
import com.roomtherapy.backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsService {

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;
    private static final String CODE_PREFIX = "RT-";

    private final GoodsRepository goodsRepository;
    private final GoodsAuthCodeRepository goodsAuthCodeRepository;
    private final UserRepository userRepository;
    private final RedisGoodsStockService redisGoodsStockService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final SecureRandom random = new SecureRandom();

    /**
     * 판매자의 굿즈 및 인증코드 묶음 등록 처리
     */
    @Transactional
    public GoodsResponseDto registerGoods(Long sellerId, GoodsCreateRequestDto request) {
        User seller = null;
        if (sellerId != null) {
            seller = userRepository.findById(sellerId).orElse(null);
        }

        Goods goods = Goods.builder()
                .name(request.name().trim())
                .description(request.description() != null ? request.description().trim() : "")
                .price(request.price())
                .imageUrl(request.imageUrl() != null ? request.imageUrl().trim() : "")
                .stockQuantity(request.stockQuantity())
                .status(GoodsStatus.ACTIVE)
                .seller(seller)
                .build();

        Goods savedGoods = goodsRepository.save(goods);

        // 판매자가 입력한 인증코드 목록 일괄 저장
        List<String> codes = request.extractAuthCodes();
        if (!codes.isEmpty()) {
            List<GoodsAuthCode> authCodes = codes.stream()
                    .map(code -> GoodsAuthCode.builder()
                            .goods(savedGoods)
                            .code(code)
                            .build())
                    .toList();
            goodsAuthCodeRepository.saveAll(authCodes);
            log.info("굿즈 ID({})에 인증코드 {}개 등록 완료", savedGoods.getId(), authCodes.size());
        }

        // Redis 재고 동기화 (선착순 경합 방지용)
        try {
            redisGoodsStockService.syncStock(savedGoods.getId(), savedGoods.getStockQuantity());
        } catch (Exception e) {
            log.warn("Redis 재고 동기화 실패 (DB 저장은 정상 완료): {}", e.getMessage());
        }

        return GoodsResponseDto.from(savedGoods, codes);
    }

    /**
     * 유니티 연동용 굿즈 목록 조회 (활성 상태 굿즈)
     */
    @Transactional(readOnly = true)
    public List<UnityGoodsResponseDto> getGoodsForUnity() {
        return goodsRepository.findAll().stream()
                .filter(g -> g.getStatus() == GoodsStatus.ACTIVE)
                .map(UnityGoodsResponseDto::from)
                .toList();
    }

    /**
     * 유니티에서 스테이지 클리어 후 굿즈 PK로 동적 인증코드 발급 요청 처리 (Redis 기반)
     */
    public UnityCodeIssueResponseDto issueDynamicCodeForUnity(Long goodsId, Duration validDuration) {
        Goods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));

        if (goods.getStockQuantity() <= 0 || goods.getStatus() == GoodsStatus.SOLD_OUT) {
            throw new BusinessException(ErrorCode.GOODS_OUT_OF_STOCK);
        }

        // 고유 난수 코드 생성 (예: RT-A7X9K2M3)
        StringBuilder sb = new StringBuilder(CODE_PREFIX);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }
        String generatedCode = sb.toString();

        // Redis에 저장: Key = goods:code:{code}, Value = goodsId, TTL 설정
        String redisKey = RedisGoodsAuthCodeValidator.REDIS_GOODS_CODE_PREFIX + generatedCode;
        redisTemplate.opsForValue().set(redisKey, String.valueOf(goodsId), validDuration);

        log.info("유니티 동적 인증코드 발급 완료 - GoodsId: {}, Code: {}, TTL: {}분",
                goodsId, generatedCode, validDuration.toMinutes());

        return new UnityCodeIssueResponseDto(goods.getId(), goods.getName(), generatedCode, validDuration.toSeconds());
    }

    /**
     * 굿즈 단건 조회 (인증코드 목록 포함)
     */
    @Transactional(readOnly = true)
    public GoodsResponseDto getGoodsWithCodes(Long goodsId) {
        Goods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        List<String> codes = goodsAuthCodeRepository.findByGoodsId(goodsId).stream()
                .map(GoodsAuthCode::getCode)
                .toList();
        return GoodsResponseDto.from(goods, codes);
    }

    /**
     * 굿즈 등록자(소유자) 여부 검증 (SpEL / @PreAuthorize 연동)
     */
    @Transactional(readOnly = true)
    public boolean isOwner(Long goodsId, Long userId) {
        if (goodsId == null || userId == null) {
            return false;
        }
        return goodsRepository.findById(goodsId)
                .map(goods -> goods.isOwnedBy(userId))
                .orElse(false);
    }

    /**
     * 굿즈 수정 화면용 조회 (권한 검증 포함)
     */
    @Transactional(readOnly = true)
    public Goods getGoodsForManagement(Long goodsId, Long currentUserId, boolean isAdmin) {
        Goods goods = goodsRepository.findById(goodsId)
                .orElseThrow(() -> new BusinessException(ErrorCode.GOODS_NOT_FOUND));
        if (!goods.isManageableBy(currentUserId, isAdmin)) {
            throw new BusinessException(ErrorCode.NOT_GOODS_OWNER);
        }
        return goods;
    }

    /**
     * 굿즈 수정 처리 (권한 검증, 추가 인증코드 등록, Redis 재고 동기화)
     */
    @Transactional
    public GoodsResponseDto updateGoods(Long goodsId, Long currentUserId, boolean isAdmin, GoodsUpdateRequestDto request) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);

        goods.update(
                request.name().trim(),
                request.description() != null ? request.description().trim() : "",
                request.price(),
                request.imageUrl() != null ? request.imageUrl().trim() : "",
                request.stockQuantity(),
                request.status()
        );

        // 추가 인증코드가 입력된 경우 등록
        List<String> additionalCodes = request.extractAdditionalAuthCodes();
        if (!additionalCodes.isEmpty()) {
            List<GoodsAuthCode> newAuthCodes = additionalCodes.stream()
                    .map(code -> GoodsAuthCode.builder()
                            .goods(goods)
                            .code(code)
                            .build())
                    .toList();
            goodsAuthCodeRepository.saveAll(newAuthCodes);
            log.info("굿즈 ID({})에 추가 인증코드 {}개 등록 완료", goods.getId(), newAuthCodes.size());
        }

        // Redis 재고 동기화
        try {
            redisGoodsStockService.syncStock(goods.getId(), goods.getStockQuantity());
        } catch (Exception e) {
            log.warn("Redis 재고 동기화 실패: {}", e.getMessage());
        }

        List<String> allCodes = goodsAuthCodeRepository.findByGoodsId(goods.getId()).stream()
                .map(GoodsAuthCode::getCode)
                .toList();
        return GoodsResponseDto.from(goods, allCodes);
    }

    /**
     * 굿즈 재고 단독 변경 처리
     */
    @Transactional
    public void updateGoodsStock(Long goodsId, Long currentUserId, boolean isAdmin, int newStock) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);
        goods.updateStock(newStock);

        try {
            redisGoodsStockService.syncStock(goods.getId(), goods.getStockQuantity());
        } catch (Exception e) {
            log.warn("Redis 재고 동기화 실패: {}", e.getMessage());
        }
    }

    /**
     * 굿즈 삭제 처리 (권한 검증 포함)
     */
    @Transactional
    public void deleteGoods(Long goodsId, Long currentUserId, boolean isAdmin) {
        Goods goods = getGoodsForManagement(goodsId, currentUserId, isAdmin);
        goodsRepository.delete(goods);
        log.info("굿즈 ID({}) 및 연동 인증코드 삭제 완료 - 처리자: {}", goodsId, currentUserId);
    }
}
