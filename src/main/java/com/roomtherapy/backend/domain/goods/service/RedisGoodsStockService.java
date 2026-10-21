package com.roomtherapy.backend.domain.goods.service;

import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 굿즈 신청 시 동시성/선착순 경합을 Redis 원자적 연산으로 제어하는 서비스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisGoodsStockService {

    public static final String REDIS_STOCK_PREFIX = "goods:stock:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final GoodsRepository goodsRepository;

    /**
     * Redis 원자적 감소(DECR)를 통한 재고 차감 (선착순 경합 방지)
     * @param goodsId 굿즈 ID
     * @param quantity 차감 수량
     * @return 차감 성공 여부
     */
    public boolean tryDecreaseStock(Long goodsId, int quantity) {
        String stockKey = REDIS_STOCK_PREFIX + goodsId;

        // Redis에 재고 키가 없으면 DB에서 조회하여 초기화
        if (Boolean.FALSE.equals(redisTemplate.hasKey(stockKey))) {
            Goods goods = goodsRepository.findById(goodsId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 굿즈입니다: " + goodsId));
            redisTemplate.opsForValue().set(stockKey, String.valueOf(goods.getStockQuantity()), Duration.ofDays(7));
        }

        // Redis DECR 원자적 연산 실행
        Long remainingStock = redisTemplate.opsForValue().decrement(stockKey, quantity);

        if (remainingStock == null || remainingStock < 0) {
            // 재고 부족 시 롤백(복구)
            redisTemplate.opsForValue().increment(stockKey, quantity);
            log.warn("Redis 재고 부족으로 차감 실패 - GoodsId: {}, 요청 수량: {}, 잔여 재고: 0", goodsId, quantity);
            return false;
        }

        log.info("Redis 원자적 재고 차감 성공 - GoodsId: {}, 차감 수량: {}, 잔여 재고: {}", goodsId, quantity, remainingStock);
        return true;
    }

    /**
     * DB 재고와 Redis 재고 동기화
     */
    public void syncStock(Long goodsId, int stockQuantity) {
        String stockKey = REDIS_STOCK_PREFIX + goodsId;
        redisTemplate.opsForValue().set(stockKey, String.valueOf(stockQuantity), Duration.ofDays(7));
        log.info("Redis 재고 동기화 완료 - GoodsId: {}, Stock: {}", goodsId, stockQuantity);
    }
}
