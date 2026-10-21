package com.roomtherapy.backend.domain.user.controller;

import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "3. Test & Utility API", description = "로컬 개발 및 테스트 편의를 위한 유틸리티 API")
@Slf4j
@Controller
@RequiredArgsConstructor
public class TestAuthController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    /**
     * 개발 및 테스트 편의를 위한 원클릭 테스트 계정 로그인
     * /test/login?role=SELLER | ADMIN | USER
     */
    @Operation(summary = "원클릭 테스트 계정 로그인", description = "OAuth 없이 SELLER, ADMIN, USER 권한의 테스트 계정으로 즉시 로그인 쿠키를 발급받습니다.")
    @GetMapping("/test/login")
    public String testLogin(
            @Parameter(description = "부여할 권한 (SELLER, ADMIN, USER)", example = "SELLER") @RequestParam(name = "role", defaultValue = "USER") String roleStr,
            HttpServletResponse response) {

        Role role;
        try {
            role = Role.valueOf(roleStr.toUpperCase());
        } catch (Exception e) {
            role = Role.USER;
        }

        String kakaoId = switch (role) {
            case SELLER -> "test_seller_1001";
            case ADMIN -> "test_admin_2002";
            default -> "test_user_3003";
        };

        final Role finalRole = role;
        User user = userRepository.findByKakaoId(kakaoId)
                .orElseGet(() -> userRepository.save(User.builder()
                        .kakaoId(kakaoId)
                        .nickname("테스트 " + finalRole.getTitle())
                        .email(finalRole.name().toLowerCase() + "@roomtherapy.test")
                        .role(finalRole)
                        .build()));

        // AccessToken 발급 및 쿠키 저장
        String accessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        Cookie cookie = new Cookie("accessToken", accessToken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(1800); // 30분
        response.addCookie(cookie);

        log.info("테스트 계정 로그인 성공 - UserId: {}, Nickname: {}, Role: {}", user.getId(), user.getNickname(), user.getRole());
        return "redirect:/";
    }
}
