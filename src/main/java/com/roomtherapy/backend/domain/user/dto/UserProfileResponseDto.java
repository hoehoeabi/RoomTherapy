package com.roomtherapy.backend.domain.user.dto;

import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;

public record UserProfileResponseDto(
        Long id,
        String kakaoId,
        String nickname,
        String email,
        Role role,
        String roleTitle,
        String recipientName,
        String phone,
        String zipcode,
        String address,
        String detailAddress,
        boolean hasDefaultDeliveryInfo
) {
    public static UserProfileResponseDto from(User user) {
        boolean hasInfo = user.getAddress() != null && !user.getAddress().isBlank();
        return new UserProfileResponseDto(
                user.getId(),
                user.getKakaoId(),
                user.getNickname(),
                user.getEmail() != null ? user.getEmail() : "이메일 미제공",
                user.getRole(),
                user.getRole().getTitle(),
                user.getRecipientName(),
                user.getPhone(),
                user.getZipcode(),
                user.getAddress(),
                user.getDetailAddress(),
                hasInfo
        );
    }
}
