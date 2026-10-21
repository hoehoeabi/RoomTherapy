package com.roomtherapy.backend.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 400 Bad Request
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "400", "입력값이 올바르지 않습니다."),
    INVALID_AUTH_CODE(HttpStatus.BAD_REQUEST, "AUTH_400_01", "유효하지 않거나 일치하지 않는 인증코드입니다."),
    BLANK_AUTH_CODE(HttpStatus.BAD_REQUEST, "AUTH_400_02", "인증코드를 입력해 주세요."),
    GOODS_OUT_OF_STOCK(HttpStatus.BAD_REQUEST, "GOODS_400_01", "해당 굿즈의 재고가 모두 소진되었습니다."),
    ORDER_CANNOT_BE_MODIFIED(HttpStatus.BAD_REQUEST, "ORDER_400_01", "이미 배송이 시작되었거나 완료/취소된 주문은 수정할 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "405", "지원하지 않는 요청 방식(HTTP 메서드)입니다."),

    // 401 Unauthorized
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "401", "로그인이 필요하거나 인증 세션이 만료되었습니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AUTH_401_01", "유효하지 않거나 만료된 Refresh Token입니다."),
    TOKEN_THEFT_DETECTED(HttpStatus.UNAUTHORIZED, "AUTH_401_02", "토큰 탈취 위험이 감지되어 강제 로그아웃되었습니다."),

    // 403 Forbidden
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "403", "해당 페이지에 접근할 권한이 없습니다."),
    NOT_ORDER_OWNER(HttpStatus.FORBIDDEN, "ORDER_403_01", "본인의 배송 신청 건만 조회하거나 수정할 수 있습니다."),
    NOT_SELLER_OWNER(HttpStatus.FORBIDDEN, "ORDER_403_02", "본인이 등록한 굿즈의 배송 주문만 상태를 변경할 수 있습니다."),

    // 404 Not Found
    NOT_FOUND(HttpStatus.NOT_FOUND, "404", "요청하신 페이지 또는 리소스를 찾을 수 없습니다."),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_404_01", "존재하지 않는 회원입니다."),
    GOODS_NOT_FOUND(HttpStatus.NOT_FOUND, "GOODS_404_01", "존재하지 않는 굿즈 상품입니다."),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "ORDER_404_01", "존재하지 않는 배송 주문입니다."),

    // 409 Conflict
    ALREADY_CLAIMED_CODE(HttpStatus.CONFLICT, "AUTH_409_01", "해당 인증코드로 이미 배송 신청을 완료하셨습니다."),
    DUPLICATE_GOODS_CLAIM(HttpStatus.CONFLICT, "GOODS_409_01", "이미 배송 신청을 완료하신 굿즈입니다. (동일 굿즈는 1인 1회만 신청 가능)"),

    // 500 Internal Server Error
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500", "서버 내부 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
