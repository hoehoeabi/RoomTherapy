package com.roomtherapy.backend.domain.delivery.service;

import com.roomtherapy.backend.domain.delivery.validator.RedisDynamicStageCodeValidator;
import com.roomtherapy.backend.domain.delivery.validator.StageCodeValidator;
import com.roomtherapy.backend.global.error.BusinessException;
import com.roomtherapy.backend.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StageCodeService {

    private final List<StageCodeValidator> validators;
    private final RedisDynamicStageCodeValidator redisDynamicStageCodeValidator;

    /**
     * 인증코드 검증 및 1회성 소모 처리 (전략 패턴 적용)
     */
    public void validateAndConsume(String code, Long userId) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.BLANK_AUTH_CODE);
        }

        String targetCode = code.trim();

        // 1. 해당 코드를 지원하는 검증기 찾기
        StageCodeValidator matchingValidator = validators.stream()
                .filter(validator -> validator.supports(targetCode))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_AUTH_CODE));

        // 2. 유효성 및 중복 검증
        if (!matchingValidator.validate(targetCode)) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }

        // 3. 코드 소모 처리
        matchingValidator.consume(targetCode, userId);
    }

    /**
     * (미래 확장용) 유니티가 직접 API 호출 시 스테이지 클리어 동적 난수 코드 발급
     * @param stage 클리어한 스테이지
     * @return 발급된 1회용 인증코드 (유효기간 7일)
     */
    public String issueDynamicCodeForUnity(int stage) {
        return redisDynamicStageCodeValidator.issueDynamicCode(stage, Duration.ofDays(7));
    }
}
