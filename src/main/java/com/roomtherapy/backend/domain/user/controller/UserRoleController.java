package com.roomtherapy.backend.domain.user.controller;

import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    /**
     * 개발 및 테스트 편의를 위한 사용자 권한 전환 기능 (USER <-> SELLER <-> ADMIN)
     */
    @PostMapping("/user/role")
    @Transactional
    public String switchRole(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam("role") Role targetRole,
            HttpServletResponse response) {

        if (userDetails == null) {
            return "redirect:/";
        }

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        user.updateRole(targetRole);
        userRepository.save(user);

        // 변경된 권한으로 신규 AccessToken 발급 및 쿠키 갱신
        String newAccessToken = jwtProvider.createAccessToken(user.getId(), user.getEmail(), user.getRole().name());

        Cookie cookie = new Cookie("accessToken", newAccessToken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(1800); // 30분
        response.addCookie(cookie);

        log.info("사용자 권한 전환 완료 - UserId: {}, NewRole: {}", user.getId(), targetRole);
        return "redirect:/";
    }
}
