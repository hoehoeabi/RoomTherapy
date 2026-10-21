package com.roomtherapy.backend.domain.goods.validator;

import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 2차 방식: 유니티에서 API로 동적 발급받아 Redis에 TTL로 저장된 일회용 코드를 검증/소진하는 구현체
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class RedisGoodsAuthCodeValidator implements GoodsAuthCodeValidator {

    public static final String REDIS_GOODS_CODE_PREFIX = "goods:code:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeliveryOrderRepository deliveryOrderRepository;

    @Override
    public boolean supports(Long goodsId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String redisKey = REDIS_GOODS_CODE_PREFIX + code.trim().toUpperCase();
        return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey));
    }

    @Override
    public boolean validate(Long goodsId, String code, Long userId) {
        if (goodsId == null || code == null || code.isBlank()) {
            return false;
        }

        String targetCode = code.trim().toUpperCase();
        String redisKey = REDIS_GOODS_CODE_PREFIX + targetCode;

        // 1. Redis에서 대상 굿즈 ID 확인
        Object storedGoodsIdObj = redisTemplate.opsForValue().get(redisKey);
        if (storedGoodsIdObj == null) {
            log.warn("Redis에 존재하지 않거나 만료된 굿즈 인증코드입니다: {}", targetCode);
            return false;
        }

        Long storedGoodsId = Long.parseLong(storedGoodsIdObj.toString());
        if (!goodsId.equals(storedGoodsId)) {
            log.warn("인증코드가 발급된 굿즈(ID: {})와 신청 굿즈(ID: {})가 일치하지 않습니다.", storedGoodsId, goodsId);
            return false;
        }

        // 2. 해당 유저가 이미 이 굿즈를 수령했는지 검증 (1인 1회 제한)
        if (userId != null && deliveryOrderRepository.existsByUserIdAndGoodsId(userId, goodsId)) {
            log.warn("유저({})가 이미 해당 굿즈({})를 수령 신청했습니다. 중복 신청 불가.", userId, goodsId);
            throw new IllegalStateException("이미 배송 신청을 완료하신 굿즈입니다. (동일 굿즈는 1인 1회만 신청 가능)");
        }

        return true;
    }

    @Override
    public void consume(Long goodsId, String code, Long userId) {
        String targetCode = code.trim().toUpperCase();
        String redisKey = REDIS_GOODS_CODE_PREFIX + targetCode;

        // Redis에서 즉시 삭제하여 1회용 소진 완료
        Boolean deleted = redisTemplate.delete(redisKey);
        log.info("Redis 굿즈 동적 인증코드 소진 완료 - GoodsId: {}, Code: {}, Deleted: {}, UserId: {}",
                goodsId, targetCode, deleted, userId);
    }
}
