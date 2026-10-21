package com.roomtherapy.backend.domain.goods.service;

import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.validator.GoodsAuthCodeValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsAuthCodeManager {

    private final List<GoodsAuthCodeValidator> validators;
    private final GoodsAuthCodeRepository goodsAuthCodeRepository;

    /**
     * 굿즈 배송 신청 시 인증코드 적격성 검증 및 소진 처리
     * @param goodsId 신청하려는 굿즈 ID
     * @param code 유저가 입력한 인증코드
     * @param userId 신청자 회원 ID
     */
    public void validateAndConsume(Long goodsId, String code, Long userId) {
        if (goodsId == null) {
            // 굿즈 선택 없이 단독 신청하는 경우(레거시) 건너뜀
            return;
        }

        // 해당 굿즈에 DB 인증코드가 있거나 Redis에 코드가 있는 경우 검증 필수
        for (GoodsAuthCodeValidator validator : validators) {
            if (validator.supports(goodsId, code)) {
                boolean isValid = validator.validate(goodsId, code, userId);
                if (!isValid) {
                    throw new IllegalArgumentException("입력하신 인증코드가 해당 굿즈의 인증코드와 일치하지 않거나 유효하지 않습니다.");
                }
                validator.consume(goodsId, code, userId);
                return;
            }
        }

        // 지원하는 검증기가 없는 경우:
        // 만약 해당 굿즈에 판매자가 등록해둔 인증코드가 존재한다면, 입력한 코드가 틀린 것이므로 예외 발생
        if (goodsAuthCodeRepository.countByGoodsId(goodsId) > 0) {
            throw new IllegalArgumentException("입력하신 인증코드가 해당 굿즈에 등록된 유효 인증코드가 아닙니다.");
        }
    }
}
