package com.roomtherapy.backend.global.security.jwt;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class JwtProviderTest {

    @Autowired
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("Access Token 생성 및 유효성 검증 성공")
    void createAndValidateAccessToken() {
        // given
        Long userId = 1L;
        String email = "test@kakao.com";
        String role = "ROLE_USER";

        // when
        String accessToken = jwtProvider.createAccessToken(userId, email, role);

        // then
        assertThat(accessToken).isNotNull();
        assertThat(jwtProvider.validateToken(accessToken)).isTrue();
        assertThat(jwtProvider.getUserId(accessToken)).isEqualTo(userId);

        Claims claims = jwtProvider.getClaims(accessToken);
        assertThat(claims.get("email")).isEqualTo(email);
        assertThat(claims.get("role")).isEqualTo(role);
    }

    @Test
    @DisplayName("Refresh Token 생성 및 만료 시간 확인")
    void createRefreshToken() {
        // given
        Long userId = 1L;

        // when
        String refreshToken = jwtProvider.createRefreshToken(userId);

        // then
        assertThat(refreshToken).isNotNull();
        assertThat(jwtProvider.validateToken(refreshToken)).isTrue();
        assertThat(jwtProvider.getUserId(refreshToken)).isEqualTo(userId);
        assertThat(jwtProvider.getRefreshTokenExpirationSec()).isGreaterThan(0);
    }

    @Test
    @DisplayName("위조되거나 잘못된 토큰 검증 시 false 반환")
    void validateInvalidToken() {
        // given
        String invalidToken = "invalid.dummy.jwt.token";

        // when & then
        assertThat(jwtProvider.validateToken(invalidToken)).isFalse();
    }
}
