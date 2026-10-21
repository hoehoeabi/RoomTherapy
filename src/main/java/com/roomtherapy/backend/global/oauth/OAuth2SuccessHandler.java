package com.roomtherapy.backend.global.oauth;

import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import com.roomtherapy.backend.global.security.jwt.RefreshToken;
import com.roomtherapy.backend.global.security.jwt.RefreshTokenRepository;
import com.roomtherapy.backend.global.util.CookieUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String kakaoId = String.valueOf(oAuth2User.getAttributes().get("id"));

        User user = userRepository.findByKakaoId(kakaoId)
                .orElseThrow(() -> new IllegalStateException("해당 카카오 계정 사용자를 찾을 수 없습니다: " + kakaoId));

        // 1. JWT Access Token & Refresh Token 생성
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());

        // 2. Redis에 Refresh Token 저장 (RTR 준비)
        RefreshToken refreshTokenEntity = RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .ttl(jwtProvider.getRefreshTokenExpirationSec())
                .build();
        refreshTokenRepository.save(refreshTokenEntity);

        // 3. 브라우저 HttpOnly 쿠키에 토큰 저장
        CookieUtil.addCookie(response, "accessToken", accessToken, jwtProvider.getAccessTokenExpirationSec());
        CookieUtil.addCookie(response, "refreshToken", refreshToken, jwtProvider.getRefreshTokenExpirationSec());

        log.info("카카오 로그인 성공 및 JWT 발급 완료 - UserId: {}, Nickname: {}", user.getId(), user.getNickname());

        // 4. 메인 화면으로 리다이렉트
        getRedirectStrategy().sendRedirect(request, response, "/");
    }
}
