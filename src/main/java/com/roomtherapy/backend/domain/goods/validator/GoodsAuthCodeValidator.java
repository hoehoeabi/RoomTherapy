package com.roomtherapy.backend.domain.goods.validator;

public interface GoodsAuthCodeValidator {

    /**
     * 해당 굿즈 및 인증코드에 대해 이 검증기가 처리를 지원하는지 여부
     */
    boolean supports(Long goodsId, String code);

    /**
     * 굿즈 인증코드 유효성 검증
     * @param goodsId 신청하려는 굿즈 ID
     * @param code 유저가 입력한 인증코드
     * @param userId 신청자 회원 ID
     * @return 유효 여부
     */
    boolean validate(Long goodsId, String code, Long userId);

    /**
     * 배송 신청 완료 후 인증코드 소모 처리 (필요시 삭제 또는 로깅)
     * @param goodsId 신청된 굿즈 ID
     * @param code 사용된 인증코드
     * @param userId 신청자 회원 ID
     */
    void consume(Long goodsId, String code, Long userId);
}
