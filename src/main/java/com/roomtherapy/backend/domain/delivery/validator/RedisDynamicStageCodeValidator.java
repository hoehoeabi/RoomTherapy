package com.roomtherapy.backend.domain.delivery.validator;

import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RedisDynamicStageCodeValidator implements StageCodeValidator {

    private static final String REDIS_PREFIX = "stage:code:";
    private static final String DYNAMIC_PREFIX = "rt-";
    private static final String CODE_CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeliveryOrderRepository deliveryOrderRepository;
    private final SecureRandom random = new SecureRandom();

    /**
     * 서버 동적 난수 인증코드 발급 (Redis에 TTL과 함께 저장 - 분산/스케일 아웃 지원)
     * @param stage 스테이지 번호
     * @param validDuration 유효 시간
     * @return 발급된 고유 인증코드 (예: RT-X7K2M9PQ)
     */
    public String issueDynamicCode(int stage, Duration validDuration) {
        StringBuilder sb = new StringBuilder(DYNAMIC_PREFIX.toUpperCase());
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARACTERS.charAt(random.nextInt(CODE_CHARACTERS.length())));
        }
        String generatedCode = sb.toString();

        String redisKey = REDIS_PREFIX + generatedCode;
        // Redis에 stage 번호를 값으로 저장 (TTL 만료 시 자동 소멸)
        redisTemplate.opsForValue().set(redisKey, String.valueOf(stage), validDuration);

        log.info("Redis 동적 인증코드 발급 완료 - Code: {}, Stage: {}, TTL: {}분",
                generatedCode, stage, validDuration.toMinutes());

        return generatedCode;
    }

    @Override
    public boolean supports(String code) {
        if (code == null) return false;
        String trimmed = code.trim().toLowerCase();
        return trimmed.startsWith(DYNAMIC_PREFIX) || Boolean.TRUE.equals(redisTemplate.hasKey(REDIS_PREFIX + code.trim().toUpperCase()));
    }

    @Override
    public boolean validate(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        String targetCode = code.trim().toUpperCase();
        String redisKey = REDIS_PREFIX + targetCode;

        // 1. Redis에 코드가 존재하는지 확인 (만료되지 않은 유효한 난수 코드)
        Boolean exists = redisTemplate.hasKey(redisKey);
        if (Boolean.FALSE.equals(exists)) {
            log.warn("Redis에 존재하지 않거나 만료된 동적 인증코드입니다: {}", targetCode);
            return false;
        }

        // 2. 이미 RDB 배송 신청에 사용되었는지 중복 검사
        if (deliveryOrderRepository.existsByStageCode(targetCode)) {
            log.warn("이미 사용된 동적 인증코드입니다: {}", targetCode);
            return false;
        }

        return true;
    }

    @Override
    public void consume(String code, Long userId) {
        String targetCode = code.trim().toUpperCase();
        String redisKey = REDIS_PREFIX + targetCode;

        // Redis에서 즉시 삭제하여 중복 소모 방지 (원자적 처리)
        Boolean deleted = redisTemplate.delete(redisKey);
        log.info("Redis 동적 인증코드 소모 처리 완료 - Code: {}, Deleted: {}, UserId: {}", targetCode, deleted, userId);
    }
}
