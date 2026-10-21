package com.roomtherapy.backend.global.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 도메인 비즈니스 커스텀 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[Business Exception] URI: {}, Code: {}, Message: {}", request.getRequestURI(), errorCode.getCode(), e.getMessage());
        return renderOrJsonResponse(request, response, errorCode.getStatus(), errorCode.getCode(), e.getMessage());
    }

    /**
     * @Valid DTO 유효성 검증 실패 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Object handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request, HttpServletResponse response) {
        BindingResult bindingResult = e.getBindingResult();
        String errorMessage = bindingResult.getFieldErrors().isEmpty()
                ? ErrorCode.INVALID_INPUT_VALUE.getMessage()
                : bindingResult.getFieldErrors().get(0).getDefaultMessage();

        log.warn("[Validation Exception] URI: {}, Message: {}", request.getRequestURI(), errorMessage);
        return renderOrJsonResponse(request, response, ErrorCode.INVALID_INPUT_VALUE.getStatus(), ErrorCode.INVALID_INPUT_VALUE.getCode(), errorMessage);
    }

    /**
     * 지원하지 않는 HTTP Method 호출 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Object handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[Method Not Allowed] URI: {}, Message: {}", request.getRequestURI(), e.getMessage());
        return renderOrJsonResponse(request, response, ErrorCode.METHOD_NOT_ALLOWED.getStatus(), ErrorCode.METHOD_NOT_ALLOWED.getCode(), ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    /**
     * 정적 리소스 및 없는 경로(404 Not Found) 처리
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public Object handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[Resource Not Found] URI: {}", request.getRequestURI());
        return renderOrJsonResponse(request, response, ErrorCode.NOT_FOUND.getStatus(), ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    /**
     * 일반 표준 런타임 예외 호환 처리
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public Object handleStandardRuntimeException(RuntimeException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[Standard Runtime Exception] URI: {}, Message: {}", request.getRequestURI(), e.getMessage());
        return renderOrJsonResponse(request, response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT_VALUE.getCode(), e.getMessage());
    }

    /**
     * 기타 미처리 서버 내부 오류 (500)
     */
    @ExceptionHandler(Exception.class)
    public Object handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        log.error("[Unhandled Server Error] URI: {}, Message: ", request.getRequestURI(), e);
        return renderOrJsonResponse(request, response, ErrorCode.INTERNAL_SERVER_ERROR.getStatus(), ErrorCode.INTERNAL_SERVER_ERROR.getCode(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    /**
     * 클라이언트 요청 성격(HTML 브라우저 vs REST API)에 따라 에러 페이지 또는 JSON 응답 분기
     */
    private Object renderOrJsonResponse(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String message) {
        if (isApiRequest(request)) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", status.value());
            body.put("code", code);
            body.put("message", message);
            return ResponseEntity.status(status).body(body);
        }

        response.setStatus(status.value());
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", status.value());
        mav.addObject("code", code);
        mav.addObject("message", message);
        return mav;
    }

    private boolean isApiRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/")) {
            return true;
        }
        String accept = request.getHeader("Accept");
        if (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE)) {
            return true;
        }
        String requestedWith = request.getHeader("X-Requested-With");
        return "XMLHttpRequest".equalsIgnoreCase(requestedWith);
    }
}
