package com.roomtherapy.backend.global.oauth;

import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import com.roomtherapy.backend.global.security.jwt.RefreshToken;
import com.roomtherapy.backend.global.security.jwt.RefreshTokenRepository;
import com.roomtherapy.backend.global.util.CookieUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private static final String RECONSENT_COOKIE_NAME = "kakao_reconsent_tried";

    private final JwtProvider jwtProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Autowired(required = false)
    private OAuth2AuthorizedClientService authorizedClientService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String kakaoId = String.valueOf(oAuth2User.getAttributes().get("id"));

        User user = userRepository.findByKakaoId(kakaoId)
                .orElseThrow(() -> new IllegalStateException("해당 카카오 계정 사용자를 찾을 수 없습니다: " + kakaoId));

        boolean hasTalkMessageScope = false;

        // 0. 카카오 OAuth2 Access Token 및 Refresh Token 저장 (판매자 카카오톡 알림 발송용)
        if (authorizedClientService != null && authentication instanceof OAuth2AuthenticationToken oauthToken) {
            try {
                OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken.getName()
                );
                if (client != null) {
                    String kakaoAccess = client.getAccessToken() != null ? client.getAccessToken().getTokenValue() : null;
                    String kakaoRefresh = client.getRefreshToken() != null ? client.getRefreshToken().getTokenValue() : null;
                    Set<String> scopes = client.getAccessToken() != null ? client.getAccessToken().getScopes() : null;
                    hasTalkMessageScope = scopes != null && scopes.contains("talk_message");

                    log.info("카카오 OAuth2 토큰 수신 - UserId: {}, Role: {}, Scopes: {}",
                            user.getId(), user.getRole(), scopes);

                    if (kakaoAccess != null || kakaoRefresh != null) {
                        user.updateKakaoTokens(kakaoAccess, kakaoRefresh);
                        userRepository.save(user);
                    }
                }
            } catch (Exception e) {
                log.warn("카카오 OAuth2 토큰 저장 중 예외 발생 (로그인은 정상 진행): {}", e.getMessage());
            }
        }

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

        // 4. 판매자/관리자인데 카카오톡 메시지(talk_message) 권한이 빠져 있다면 1회 자동 추가 동의창으로 리다이렉트
        if (authorizedClientService != null
                && (user.getRole() == Role.SELLER || user.getRole() == Role.ADMIN)
                && !hasTalkMessageScope
                && !hasReconsentCookie(request)) {
            CookieUtil.addCookie(response, RECONSENT_COOKIE_NAME, "1", 60);
            log.info("판매자/관리자(userId={})의 talk_message 권한 미동의 감지 -> 카카오 추가 동의 화면으로 자동 리다이렉트", user.getId());
            getRedirectStrategy().sendRedirect(request, response, "/oauth2/authorization/kakao?reconsent=talk_message");
            return;
        }

        log.info("카카오 로그인 성공 및 JWT 발급 완료 - UserId: {}, Nickname: {}", user.getId(), user.getNickname());

        // 5. 메인 화면으로 리다이렉트
        getRedirectStrategy().sendRedirect(request, response, "/");
    }

    private boolean hasReconsentCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return false;
        }
        for (Cookie cookie : request.getCookies()) {
            if (RECONSENT_COOKIE_NAME.equals(cookie.getName())) {
                return true;
            }
        }
        return false;
    }
}
