package com.roomtherapy.backend.domain.delivery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record DeliveryOrderRequestDto(
        @NotBlank(message = "클리어 인증코드를 입력해 주세요.")
        String stageCode,

        @NotBlank(message = "수령인 성함을 입력해 주세요.")
        String recipientName,

        String phone, // 수집 제외 (선택/하위호환)

        @NotBlank(message = "우편번호를 입력해 주세요.")
        @Pattern(regexp = "^\\d{5}$", message = "우편번호는 5자리 숫자로 입력해 주세요.")
        String zipcode,

        @NotBlank(message = "기본 배송지 주소를 입력해 주세요.")
        String address,

        String detailAddress,

        Long goodsId, // 선택적 리워드 물품 ID

        Boolean saveAsDefaultAddress, // 기본 배송지로 저장 여부

        String feedback // 선택적 게임 피드백
) {
    public DeliveryOrderRequestDto(
            String stageCode, String recipientName, String phone,
            String zipcode, String address, String detailAddress,
            Long goodsId, Boolean saveAsDefaultAddress
    ) {
        this(stageCode, recipientName, phone, zipcode, address, detailAddress, goodsId, saveAsDefaultAddress, null);
    }
}
