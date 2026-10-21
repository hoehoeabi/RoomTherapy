package com.roomtherapy.backend.domain.delivery.validator;

public interface StageCodeValidator {

    /**
     * 해당 검증기가 지원하는 코드 형식인지 판단
     */
    boolean supports(String code);

    /**
     * 인증코드 유효성 검증
     * @param code 검증할 인증코드
     * @return 유효 여부
     */
    boolean validate(String code);

    /**
     * 인증코드 소모 처리 (1회 사용 완료)
     * @param code 소모할 인증코드
     * @param userId 사용한 유저 ID
     */
    void consume(String code, Long userId);
}
