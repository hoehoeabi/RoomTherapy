package com.roomtherapy.backend.global.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 일반 웹 브라우저(타임리프 뷰, @Controller) 전용 전역 예외 처리기
 * - 예외 발생 시 일관된 error.html 뷰(상태코드, 에러코드, 메시지, 이전/홈 버튼) 렌더링
 */
@Slf4j
@Order(Ordered.LOWEST_PRECEDENCE)
@ControllerAdvice(annotations = Controller.class)
public class WebExceptionHandler {

    /**
     * 비즈니스 로직 커스텀 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ModelAndView handleBusinessException(BusinessException e, HttpServletRequest request, HttpServletResponse response) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[Web Business Exception] URI: {}, Code: {}, Message: {}", request.getRequestURI(), errorCode.getCode(), e.getMessage());
        return createErrorView(response, errorCode.getStatus(), errorCode.getCode(), e.getMessage());
    }

    /**
     * @Valid DTO 파라미터 유효성 검증 실패 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ModelAndView handleMethodArgumentNotValidException(MethodArgumentNotValidException e, HttpServletRequest request, HttpServletResponse response) {
        BindingResult bindingResult = e.getBindingResult();
        String errorMessage = bindingResult.getFieldErrors().isEmpty()
                ? ErrorCode.INVALID_INPUT_VALUE.getMessage()
                : bindingResult.getFieldErrors().get(0).getDefaultMessage();

        log.warn("[Web Validation Exception] URI: {}, Message: {}", request.getRequestURI(), errorMessage);
        return createErrorView(response, ErrorCode.INVALID_INPUT_VALUE.getStatus(), ErrorCode.INVALID_INPUT_VALUE.getCode(), errorMessage);
    }

    /**
     * 지원하지 않는 HTTP Method 호출 처리
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ModelAndView handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[Web Method Not Allowed] URI: {}, Message: {}", request.getRequestURI(), e.getMessage());
        return createErrorView(response, ErrorCode.METHOD_NOT_ALLOWED.getStatus(), ErrorCode.METHOD_NOT_ALLOWED.getCode(), ErrorCode.METHOD_NOT_ALLOWED.getMessage());
    }

    /**
     * 정적 리소스 및 없는 경로(404 Not Found) 처리
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ModelAndView handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[Web Resource Not Found] URI: {}", request.getRequestURI());
        return createErrorView(response, ErrorCode.NOT_FOUND.getStatus(), ErrorCode.NOT_FOUND.getCode(), ErrorCode.NOT_FOUND.getMessage());
    }

    /**
     * 일반 표준 런타임 예외 처리 (IllegalArgumentException, IllegalStateException)
     */
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ModelAndView handleStandardRuntimeException(RuntimeException e, HttpServletRequest request, HttpServletResponse response) {
        log.warn("[Web Runtime Exception] URI: {}, Message: {}", request.getRequestURI(), e.getMessage());
        return createErrorView(response, HttpStatus.BAD_REQUEST, ErrorCode.INVALID_INPUT_VALUE.getCode(), e.getMessage());
    }

    /**
     * 서버 내부 미처리 예외 (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ModelAndView handleException(Exception e, HttpServletRequest request, HttpServletResponse response) {
        log.error("[Web Unhandled Server Error] URI: {}, Message: ", request.getRequestURI(), e);
        return createErrorView(response, ErrorCode.INTERNAL_SERVER_ERROR.getStatus(), ErrorCode.INTERNAL_SERVER_ERROR.getCode(), ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
    }

    private ModelAndView createErrorView(HttpServletResponse response, HttpStatus status, String code, String message) {
        response.setStatus(status.value());
        ModelAndView mav = new ModelAndView("error");
        mav.addObject("status", status.value());
        mav.addObject("code", code);
        mav.addObject("message", message);
        return mav;
    }
}
