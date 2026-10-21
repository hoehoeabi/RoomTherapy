package com.roomtherapy.backend.global.config.swagger;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    private static final String BEARER_AUTH_SCHEME = "JWT-Bearer-Auth";
    private static final String COOKIE_AUTH_SCHEME = "Cookie-AccessToken";

    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("Room Therapy (룸테라피) REST API 명세서")
                .description("룸테라피 유니티(게임) 클라이언트 연동 및 굿즈 배송/인증 백엔드 REST API 문서입니다.\n\n" +
                        "• **유니티 게임 연동 API**: 굿즈 목록 조회, 동적 인증코드 발급\n" +
                        "• **배송 및 인증 관리 API**: 굿즈 신청, 인증코드 검증, 빠른 테스트 로그인")
                .version("v1.0.0")
                .contact(new Contact().name("RoomTherapy Team").url("https://github.com/hoehoeabi/RoomTherapy"));

        // 1. JWT Bearer 인증 스키마
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Access Token을 입력하세요 (Bearer 접두사는 자동 추가됨)");

        // 2. Cookie 인증 스키마
        SecurityScheme cookieScheme = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("accessToken")
                .description("브라우저 쿠키(accessToken) 기반 인증");

        Components components = new Components()
                .addSecuritySchemes(BEARER_AUTH_SCHEME, bearerScheme)
                .addSecuritySchemes(COOKIE_AUTH_SCHEME, cookieScheme);

        // 기본 SecurityRequirement 설정
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList(BEARER_AUTH_SCHEME)
                .addList(COOKIE_AUTH_SCHEME);

        return new OpenAPI()
                .info(info)
                .components(components)
                .addSecurityItem(securityRequirement);
    }
}
