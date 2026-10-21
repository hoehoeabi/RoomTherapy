package com.roomtherapy.backend.domain.auth.dto;

public record TokenResponseDto(
        String accessToken,
        String refreshToken
) {
}
