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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 스프링 시큐리티 인가 실패 (403 Forbidden) 커스텀 핸들러
 * - 일반 웹 브라우저 요청: 친절한 안내 메시지와 함께 /error (error.html) 포워딩
 * - REST API / 유니티 요청: 일관된 JSON ApiResponse 반환
 */
@Slf4j
@Component
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException) throws IOException, ServletException {
        log.warn("[Security Access Denied 403] URI: {}, Message: {}", request.getRequestURI(), accessDeniedException.getMessage());

        boolean isApi = request.getRequestURI().startsWith("/api/")
                || "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains(MediaType.APPLICATION_JSON_VALUE));

        if (isApi) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
            objectMapper.writeValue(response.getWriter(), ApiResponse.error(HttpStatus.FORBIDDEN.value(), ErrorCode.ACCESS_DENIED.getCode(), ErrorCode.ACCESS_DENIED.getMessage()));
            return;
        }

        // 일반 브라우저 요청: 친절한 에러 코드와 메시지 설정 후 /error로 포워딩
        request.setAttribute("status", HttpStatus.FORBIDDEN.value());
        request.setAttribute("code", ErrorCode.ACCESS_DENIED.getCode());
        request.setAttribute("message", ErrorCode.ACCESS_DENIED.getMessage());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        request.getRequestDispatcher("/error").forward(request, response);
    }
}
