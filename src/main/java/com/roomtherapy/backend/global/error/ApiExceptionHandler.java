package com.roomtherapy.backend.global.error;

import com.roomtherapy.backend.global.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 유니티(C#) 및 외부 클라이언트 REST API (@RestController) 전용 전역 예외 처리기
 * - 순수 ApiResponse<Void> 규격으로 통일하여 반환
 * - HTTP 상태 코드는 HttpServletResponse를 통해 직접 설정 (ResponseEntity 불필요)
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(annotations = RestController.class)
public class ApiExceptionHandler {

    /**
     * 비즈니스 로직 커스텀 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[REST API Business Exception] URI: {}, Code: {}, Message: {}", request.getRequestURI(), errorCode.getCode(), e.getMessage());
        response.setStatus(errorCode.getStatus().value());
        return ApiResponse.error(errorCode, e.getMessage());
    }

    /**
     * @Valid DTO 파라미터 유효성 검증 실패 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request, HttpServletResponse response) {
        BindingResult bindingResult = e.getBindingResult();
        String errorMessage = bindingResult.getFieldErrors().isEmpty()
                ? ErrorCode.INVALID_INPUT_VALUE.getMessage()
                : bindingResult.getFieldErrors().get(0).getDefaultMessage();

        log.warn("[REST API Validation Exception] URI: {}, Message: {}", request.getRequestURI(), errorMessage);
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResponse.error(ErrorCode.INVALID_INPUT_VALUE, errorMessage);
    }

    /**
     * 지원하지 않는 HTTP Method 호출 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ApiResponse<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[REST API Method Not Allowed] URI: {}, Message: {}", request.getRequestURI(), e.getMessage());
        response.setStatus(HttpStatus.METHOD_NOT_ALLOWED.value());
        return ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED);
    }

    /**
     * 일반 표준 런타임 예외 처리 (IllegalArgumentException, IllegalStateException)
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ApiResponse<Void> handleStandardRuntimeException(RuntimeException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[REST API Runtime Exception] URI: {}, Message: {}", request.getRequestURI(), e.getMessage());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return ApiResponse.error(HttpStatus.BAD_REQUEST.value(), ErrorCode.INVALID_INPUT_VALUE.getCode(), e.getMessage());
    }

    /**
     * 접근 권한 거부 예외 처리 (403 Forbidden - @PreAuthorize 등)
     */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDeniedException(org.springframework.security.access.AccessDeniedException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[REST API Access Denied] URI: {}, Message: {}", request.getRequestURI(), e.getMessage());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        return ApiResponse.error(HttpStatus.FORBIDDEN.value(), ErrorCode.NOT_GOODS_OWNER.getCode(), "접근 권한이 없습니다.");
    }

    /**
     * 서버 내부 미처리 예외 (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        log.error("[REST API Unhandled Server Error] URI: {}, Message: ", request.getRequestURI(), e);
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ApiResponse.error(ErrorCode.INTERNAL_SERVER_ERROR);
    }
}
