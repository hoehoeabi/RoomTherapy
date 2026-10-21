package com.roomtherapy.backend.domain.delivery.validator;

import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.delivery.service.StageCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class StageCodeValidatorTest {

    @Autowired
    private StageCodeService stageCodeService;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Test
    @DisplayName("고정 Set 기반 인증코드(ew 접두사) 검증 성공")
    void validateFixedSetCode_success() {
        String fixedCode = "ew-stage1-clear-01";

        // 검증 및 소모 예외 발생하지 않음
        stageCodeService.validateAndConsume(fixedCode, 1L);
    }

    @Test
    @DisplayName("지원하지 않는 잘못된 형식의 코드는 예외 발생")
    void validateUnsupportedCode_throwsException() {
        String invalidCode = "unknown-invalid-code";

        assertThatThrownBy(() -> stageCodeService.validateAndConsume(invalidCode, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("올바른 형식의 인증코드가 아닙니다");
    }

    @Test
    @DisplayName("Redis 기반 동적 난수 인증코드(RT- 접두사) 발급 및 검증/소모 성공 (스케일 아웃 지원)")
    void issueAndConsumeDynamicCode_success() {
        // 1. 유니티 게임 클리어 시 서버가 동적 난수 발급
        int stage = 1;
        String issuedCode = stageCodeService.issueDynamicCodeForUnity(stage);

        assertThat(issuedCode).startsWith("RT-");

        // 2. 1회차 사용 - 검증 통과 및 Redis 소모
        stageCodeService.validateAndConsume(issuedCode, 1L);

        // 3. 2회차 재사용 시도 - 이미 Redis에서 소모(삭제)되었으므로 차단
        assertThatThrownBy(() -> stageCodeService.validateAndConsume(issuedCode, 1L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("유효하지 않거나 이미 사용된 인증코드입니다");
    }
}
