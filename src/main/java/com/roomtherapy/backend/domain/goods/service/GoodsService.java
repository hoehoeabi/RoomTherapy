package com.roomtherapy.backend.domain.goods.service;

import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
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
        redisGoodsStockService.syncStock(savedGoods.getId(), savedGoods.getStockQuantity());

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
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 굿즈입니다: " + goodsId));

        if (goods.getStockQuantity() <= 0 || goods.getStatus() == GoodsStatus.SOLD_OUT) {
            throw new IllegalStateException("해당 굿즈는 품절되어 인증코드를 발급할 수 없습니다.");
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
                .orElseThrow(() -> new IllegalArgumentException("굿즈를 찾을 수 없습니다: " + goodsId));
        List<String> codes = goodsAuthCodeRepository.findByGoodsId(goodsId).stream()
                .map(GoodsAuthCode::getCode)
                .toList();
        return GoodsResponseDto.from(goods, codes);
    }
}
