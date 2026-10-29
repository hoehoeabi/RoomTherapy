package com.roomtherapy.backend.domain.goods.service;

import com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.validator.GoodsAuthCodeValidator;
import com.roomtherapy.backend.global.error.BusinessException;
import com.roomtherapy.backend.global.error.ErrorCode;
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

        if (code == null || code.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
        }

        // 해당 굿즈에 DB 인증코드가 있거나 Redis에 코드가 있는 경우 검증 필수
        for (GoodsAuthCodeValidator validator : validators) {
            if (validator.supports(goodsId, code)) {
                boolean isValid = validator.validate(goodsId, code, userId);
                if (!isValid) {
                    throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
                }
                validator.consume(goodsId, code, userId);
                return;
            }
        }

        // 지원하는 검증기가 없거나 일치하는 유효 인증코드가 존재하지 않는 경우 무조건 차단
        log.warn("굿즈({})에 일치하거나 유효한 인증코드가 없습니다: {}", goodsId, code);
        throw new BusinessException(ErrorCode.INVALID_AUTH_CODE);
    }

    /**
     * 굿즈 배송 신청 전 인증코드 유효성 사전 검사 (소진하지 않고 적격 여부만 판별)
     */
    public com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto verifyOnly(Long goodsId, String code, Long userId) {
        if (code == null || code.isBlank()) {
            return new com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto(false, "인증코드를 입력해주세요.", null);
        }

        if (goodsId == null) {
            return new com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto(true, "굿즈 미선택 기본 코드입니다.", "DEFAULT");
        }

        for (GoodsAuthCodeValidator validator : validators) {
            if (validator.supports(goodsId, code)) {
                try {
                    boolean isValid = validator.validate(goodsId, code, userId);
                    if (isValid) {
                        String type = validator.getClass().getSimpleName().contains("Redis") ? "REDIS_DYNAMIC" : "DB_STATIC";
                        return new com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto(true, "유효한 인증코드입니다!", type);
                    } else {
                        return new com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto(false, "해당 굿즈에 유효하지 않거나 일치하지 않는 인증코드입니다.", null);
                    }
                } catch (BusinessException e) {
                    return new com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto(false, e.getMessage(), null);
                }
            }
        }

        return new com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeVerifyResponseDto(false, "해당 굿즈에 등록되지 않았거나 일치하지 않는 인증코드입니다.", null);
    }
}
