package com.roomtherapy.backend.domain.delivery.validator;

import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class SetBasedStageCodeValidator implements StageCodeValidator {

    private final DeliveryOrderRepository deliveryOrderRepository;

    // 접두사 정의 (예: 1스테이지는 'ew' 접두사)
    private static final String STAGE_1_PREFIX = "ew";

    // 전달받은 고정 인증코드 묶음 Set (초기 샘플 데이터 포함 및 확장 가능)
    private final Set<String> validCodeSet = Collections.synchronizedSet(new HashSet<>());

    {
        // 유니티에서 전달받을 고정 코드 샘플 (추후 외부 주입/확장 가능)
        validCodeSet.add("ew-stage1-clear-01");
        validCodeSet.add("ew-stage1-clear-02");
        validCodeSet.add("ew-stage1-clear-03");
        validCodeSet.add("ew-1010-clear");
        validCodeSet.add("ew-2020-clear");
    }

    public void addValidCodes(Set<String> codes) {
        if (codes != null) {
            validCodeSet.addAll(codes);
        }
    }

    @Override
    public boolean supports(String code) {
        if (code == null || code.isBlank()) return false;
        // RedisDynamicStageCodeValidator가 'RT-'를 처리하고, 나머지 모든 코드를 지원
        return true;
    }

    @Override
    public boolean validate(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }

        String targetCode = code.trim();

        // 이미 배송 신청에 사용된 코드인지 중복 검사 (1회용 보장)
        if (deliveryOrderRepository.existsByStageCode(targetCode)) {
            log.warn("이미 사용된 인증코드입니다: {}", targetCode);
            return false;
        }

        return true;
    }

    @Override
    public void consume(String code, Long userId) {
        // RDB에 배송 신청(DeliveryOrder)이 저장되면서 자동으로 소모 처리됨 (existsByStageCode로 차단)
        log.info("고정 인증코드 사용 완료 - Code: {}, UserId: {}", code, userId);
    }
}
