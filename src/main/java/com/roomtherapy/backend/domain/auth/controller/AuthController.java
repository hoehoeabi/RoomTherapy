package com.roomtherapy.backend.domain.auth.controller;

import com.roomtherapy.backend.domain.auth.dto.TokenResponseDto;
import com.roomtherapy.backend.domain.auth.service.AuthService;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import com.roomtherapy.backend.global.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final JwtProvider jwtProvider;

    /**
     * RTR 토큰 재발급 엔드포인트 (웹 브라우저 쿠키 및 REST API/유니티 겸용)
     */
    @PostMapping("/api/auth/reissue")
    @ResponseBody
    public ResponseEntity<TokenResponseDto> reissue(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestBody(required = false) Map<String, String> body) {

        // 1. Refresh Token 추출 (쿠키 우선 -> 요청 바디)
        String refreshToken = CookieUtil.getCookie(request, "refreshToken")
                .map(cookie -> cookie.getValue())
                .orElse(body != null ? body.get("refreshToken") : null);

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        // 2. RTR 기반 토큰 재발급
        TokenResponseDto tokenResponse = authService.reissue(refreshToken);

        // 3. 브라우저 쿠키 갱신
        CookieUtil.addCookie(response, "accessToken", tokenResponse.accessToken(), jwtProvider.getAccessTokenExpirationSec());
        CookieUtil.addCookie(response, "refreshToken", tokenResponse.refreshToken(), jwtProvider.getRefreshTokenExpirationSec());

        return ResponseEntity.ok(tokenResponse);
    }

    /**
     * 로그아웃 처리 (Redis 토큰 삭제 + 쿠키 무효화)
     */
    @GetMapping("/logout")
    public String logout(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletResponse response) {

        if (userDetails != null) {
            authService.logout(userDetails.getUserId());
        }

        // 쿠키 삭제
        CookieUtil.deleteCookie(response, "accessToken");
        CookieUtil.deleteCookie(response, "refreshToken");

        return "redirect:/";
    }

    @PostMapping("/logout")
    public String logoutPost(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletResponse response) {
        return logout(userDetails, response);
    }
}
