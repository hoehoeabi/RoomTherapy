package com.roomtherapy.backend.global.security.jwt;

import com.roomtherapy.backend.domain.auth.dto.TokenResponseDto;
import com.roomtherapy.backend.domain.auth.service.AuthService;
import com.roomtherapy.backend.global.security.user.CustomUserDetailsService;
import com.roomtherapy.backend.global.util.CookieUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String ACCESS_TOKEN_COOKIE = "accessToken";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;
    private final AuthService authService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 토큰 추출 (헤더 우선 -> 없을 시 브라우저 쿠키에서 추출)
        String token = resolveToken(request);

        // 2. Access Token이 유효한 경우 바로 SecurityContext에 인증 정보 저장
        if (StringUtils.hasText(token) && jwtProvider.validateToken(token) && jwtProvider.isAccessToken(token)) {
            setAuthentication(request, token);
        } else {
            // 3. [서버 사이드 Silent Refresh]
            // Access Token이 만료/부재 상태이며, 브라우저에 유효한 Refresh Token 쿠키가 있는 경우 자동 갱신
            trySilentRefresh(request, response);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Access Token이 만료/부재 시 Refresh Token 쿠키를 확인하여 새 토큰을 자동 발급하고 응답 쿠키에 갱신
     */
    private void trySilentRefresh(HttpServletRequest request, HttpServletResponse response) {
        // 정적 자원(css, js, images 등) 및 명시적 인증/재발급/로그아웃 API 요청은 필터 자동 재발급에서 제외
        String uri = request.getRequestURI();
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/") 
                || uri.equals("/favicon.ico") || uri.startsWith("/api/auth/") || uri.equals("/logout")) {
            return;
        }

        // 브라우저 쿠키에서 refreshToken 추출
        String refreshToken = CookieUtil.getCookie(request, REFRESH_TOKEN_COOKIE)
                .map(cookie -> cookie.getValue())
                .orElse(null);

        if (!StringUtils.hasText(refreshToken)) {
            return;
        }

        try {
            // RTR 기반 토큰 재발급 (Redis 검증 및 토큰 교체)
            TokenResponseDto newTokens = authService.reissue(refreshToken);

            // 신규 Access Token 및 Refresh Token 브라우저 쿠키 갱신
            CookieUtil.addCookie(response, ACCESS_TOKEN_COOKIE, newTokens.accessToken(), jwtProvider.getAccessTokenExpirationSec());
            CookieUtil.addCookie(response, REFRESH_TOKEN_COOKIE, newTokens.refreshToken(), jwtProvider.getRefreshTokenExpirationSec());

            // 갱신된 Access Token으로 즉시 SecurityContext 인증 완료
            setAuthentication(request, newTokens.accessToken());
            log.info("🔄 [Silent Refresh] 만료된 세션이 Refresh Token을 통해 자동 갱신되었습니다. URI: {}", uri);
        } catch (Exception e) {
            log.debug("Silent Refresh 실패 (만료/유효하지 않은 Refresh Token): {}", e.getMessage());
        }
    }

    /**
     * Request Header 또는 Cookie에서 토큰 추출
     */
    private String resolveToken(HttpServletRequest request) {
        // 1) Authorization 헤더 검사 (API 호출 / 미래 유니티 직접 통신 대응)
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }

        // 2) 브라우저 쿠키 검사 (Thymeleaf 웹 화면 및 WebGL 브라우저 전환 대응)
        return CookieUtil.getCookie(request, ACCESS_TOKEN_COOKIE)
                .map(cookie -> cookie.getValue())
                .orElse(null);
    }

    /**
     * 토큰 기반으로 UserDetails를 조회하여 SecurityContext에 Authentication 저장
     */
    private void setAuthentication(HttpServletRequest request, String token) {
        try {
            Long userId = jwtProvider.getUserId(token);
            UserDetails userDetails = userDetailsService.loadUserById(userId);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (Exception e) {
            log.error("SecurityContext 사용자 인증 설정 실패: {}", e.getMessage());
        }
    }
}
