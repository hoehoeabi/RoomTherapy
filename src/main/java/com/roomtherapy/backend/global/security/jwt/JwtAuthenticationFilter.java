package com.roomtherapy.backend.global.security.jwt;

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

    private final JwtProvider jwtProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 토큰 추출 (헤더 우선 -> 없을 시 브라우저 쿠키에서 추출)
        String token = resolveToken(request);

        // 2. 토큰 유효성 검증 및 SecurityContext 인증 정보 저장
        if (StringUtils.hasText(token) && jwtProvider.validateToken(token)) {
            setAuthentication(request, token);
        }

        filterChain.doFilter(request, response);
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
