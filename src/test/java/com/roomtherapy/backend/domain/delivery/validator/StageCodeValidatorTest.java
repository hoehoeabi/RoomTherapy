package com.roomtherapy.backend.domain.delivery.validator;

import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.delivery.service.StageCodeService;
import com.roomtherapy.backend.global.error.BusinessException;
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

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        deliveryOrderRepository.deleteAll();
    }

    @Test
    @DisplayName("고정 Set 기반 인증코드(ew 접두사) 검증 성공")
    void validateFixedSetCode_success() {
        String fixedCode = "ew-stage1-clear-01";

        // 검증 및 소모 예외 발생하지 않음
        stageCodeService.validateAndConsume(fixedCode, 1L);
    }

    @Test
    @DisplayName("빈 문자열이거나 null인 코드는 예외 발생")
    void validateBlankCode_throwsException() {
        assertThatThrownBy(() -> stageCodeService.validateAndConsume("   ", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("인증코드를 입력해 주세요");

        assertThatThrownBy(() -> stageCodeService.validateAndConsume(null, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("인증코드를 입력해 주세요");
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
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("유효하지 않거나 일치하지 않는 인증코드입니다");
    }
}
