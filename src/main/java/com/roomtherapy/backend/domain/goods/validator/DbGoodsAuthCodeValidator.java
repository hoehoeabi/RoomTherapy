package com.roomtherapy.backend.domain.goods.validator;

import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 1차 방식: 판매자가 굿즈 등록 시 입력한 사전 인증코드 목록(DB)을 기반으로 검증하는 구현체
 * 유니티에 하드코딩된 인증코드 묶음과 대조합니다.
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class DbGoodsAuthCodeValidator implements GoodsAuthCodeValidator {

    private final GoodsAuthCodeRepository goodsAuthCodeRepository;
    private final DeliveryOrderRepository deliveryOrderRepository;

    @Override
    public boolean supports(Long goodsId, String code) {
        if (goodsId == null || code == null || code.isBlank()) {
            return false;
        }
        // DB에 해당 굿즈의 인증코드가 등록되어 있다면 DB 검증기 사용
        return goodsAuthCodeRepository.countByGoodsId(goodsId) > 0;
    }

    @Override
    public boolean validate(Long goodsId, String code, Long userId) {
        if (goodsId == null || code == null || code.isBlank()) {
            return false;
        }

        String targetCode = code.trim();

        // 1. 해당 굿즈에 등록된 유효한 인증코드인지 확인
        boolean existsForGoods = goodsAuthCodeRepository.existsByGoodsIdAndCodeIgnoreCase(goodsId, targetCode);
        if (!existsForGoods) {
            log.warn("굿즈({})에 등록되지 않은 인증코드입니다: {}", goodsId, targetCode);
            return false;
        }

        // 2. 해당 유저가 이미 이 굿즈를 수령했는지 검증 (1인 1회 제한)
        if (userId != null && deliveryOrderRepository.existsByUserIdAndGoodsId(userId, goodsId)) {
            log.warn("유저({})가 이미 해당 굿즈({})를 수령 신청했습니다. 중복 신청 불가.", userId, goodsId);
            throw new IllegalStateException("이미 배송 신청을 완료하신 굿즈입니다. (동일 굿즈는 1인 1회만 신청 가능)");
        }

        return true;
    }

    @Override
    public void consume(Long goodsId, String code, Long userId) {
        log.info("DB 기반 굿즈 인증코드 사용 검증 통과 - GoodsId: {}, Code: {}, UserId: {}", goodsId, code, userId);
    }
}
