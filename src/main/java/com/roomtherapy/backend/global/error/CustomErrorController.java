package com.roomtherapy.backend.global.error;

import com.roomtherapy.backend.global.common.ApiResponse;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 스프링 부트 기본 /error 경로 전담 커스텀 컨트롤러
 * - 시큐리티 필터, 톰캣/서블릿 레벨 등에서 전달된 모든 에러(401, 403, 404, 405, 413, 500 등)를 처리
 * - 비개발자 사용자도 쉽게 이해할 수 있는 친절한 한국어 안내 메시지와 상태코드 보장
 * - REST API 요청 시 규격화된 JSON ApiResponse 반환
 */
@Slf4j
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request, HttpServletResponse response, Model model) {
        Integer status = (Integer) request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (status == null) {
            status = (Integer) request.getAttribute("status");
        }
        if (status == null || status == 200) {
            status = response.getStatus();
        }
        if (status == null || status == 200) {
            status = HttpStatus.INTERNAL_SERVER_ERROR.value();
        }
        response.setStatus(status);

        String code = (String) request.getAttribute("code");
        String message = (String) request.getAttribute("message");

        // 구체적인 메시지가 없는 경우 HTTP 상태 코드 기반 친절한 기본 한국어 메시지 제공
        if (message == null || message.isBlank()) {
            switch (status) {
                case 401 -> {
                    code = (code != null) ? code : ErrorCode.UNAUTHORIZED.getCode();
                    message = "로그인이 필요한 서비스입니다. 카카오 소셜 로그인 후 이용해 주세요.";
                }
                case 403 -> {
                    code = (code != null) ? code : ErrorCode.ACCESS_DENIED.getCode();
                    message = "접근 권한이 없습니다. 해당 기능은 판매자 또는 관리자 전용입니다.";
                }
                case 404 -> {
                    code = (code != null) ? code : ErrorCode.NOT_FOUND.getCode();
                    message = "요청하신 페이지를 찾을 수 없습니다. 주소를 다시 확인해 주세요.";
                }
                case 405 -> {
                    code = (code != null) ? code : ErrorCode.METHOD_NOT_ALLOWED.getCode();
                    message = "지원하지 않는 요청 방식(HTTP 메서드)입니다.";
                }
                case 413 -> {
                    code = (code != null) ? code : ErrorCode.FILE_SIZE_EXCEEDED.getCode();
                    message = ErrorCode.FILE_SIZE_EXCEEDED.getMessage();
                }
                default -> {
                    code = (code != null) ? code : ErrorCode.INTERNAL_SERVER_ERROR.getCode();
                    message = ErrorCode.INTERNAL_SERVER_ERROR.getMessage();
                }
            }
        }

        boolean isApi = request.getRequestURI().startsWith("/api/")
                || "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains(MediaType.APPLICATION_JSON_VALUE));

        if (isApi) {
            return ResponseEntity.status(status).body(ApiResponse.error(status, code != null ? code : String.valueOf(status), message));
        }

        model.addAttribute("status", status);
        model.addAttribute("code", code);
        model.addAttribute("message", message);
        return "error";
    }
}
