package com.roomtherapy.backend.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomtherapy.backend.global.common.ApiResponse;
import com.roomtherapy.backend.global.error.ErrorCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 스프링 시큐리티 인증 실패 / 미로그인 접근 (401 Unauthorized) 커스텀 핸들러
 * - 일반 웹 브라우저 요청: 친절한 로그인 안내 메시지와 함께 /error (error.html) 포워딩
 * - REST API / 유니티 요청: 일관된 JSON ApiResponse 반환
 */
@Slf4j
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException) throws IOException, ServletException {
        log.warn("[Security Unauthenticated 401] URI: {}, Message: {}", request.getRequestURI(), authException.getMessage());

        boolean isApi = request.getRequestURI().startsWith("/api/")
                || "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains(MediaType.APPLICATION_JSON_VALUE));

        if (isApi) {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(HttpStatus.UNAUTHORIZED.value(), ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage()));
            return;
        }

        // 일반 브라우저 요청: 카카오 소셜 로그인 페이지로 리다이렉트 (302)
        response.sendRedirect("/oauth2/authorization/kakao");
    }
}
