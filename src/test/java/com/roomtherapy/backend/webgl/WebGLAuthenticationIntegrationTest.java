package com.roomtherapy.backend.webgl;

import com.roomtherapy.backend.domain.auth.dto.TokenResponseDto;
import com.roomtherapy.backend.domain.auth.service.AuthService;
import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import com.roomtherapy.backend.global.security.jwt.RefreshToken;
import com.roomtherapy.backend.global.security.jwt.RefreshTokenRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class WebGLAuthenticationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private AuthService authService;

    @Autowired
    private com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private com.roomtherapy.backend.domain.goods.repository.GoodsRepository goodsRepository;

    @Autowired
    private com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository goodsAuthCodeRepository;

    private User testUser;
    private String validAccessToken;
    private String validRefreshToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        deliveryOrderRepository.deleteAll();
        goodsAuthCodeRepository.deleteAll();
        goodsRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        // 1. 테스트 유저 생성
        testUser = userRepository.save(User.builder()
                .kakaoId("kakao_12345678")
                .nickname("룸테라피유저")
                .email("user@roomtherapy.com")
                .role(Role.USER)
                .build());

        // 2. JWT Access Token & Refresh Token 발급
        validAccessToken = jwtProvider.createAccessToken(
                testUser.getId(),
                testUser.getEmail(),
                testUser.getRole().name()
        );
        validRefreshToken = jwtProvider.createRefreshToken(testUser.getId());

        // 3. Redis에 Refresh Token 저장
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(testUser.getId())
                .token(validRefreshToken)
                .ttl(jwtProvider.getRefreshTokenExpirationSec())
                .build());
    }

    @Test
    @DisplayName("시나리오 1: WebGL에서 비로그인 상태로 배송 신청 페이지(/delivery) 진입 시 카카오 로그인으로 리다이렉트 (302)")
    void unauthenticatedWebGLUser_accessDelivery_redirectsToKakaoOAuthLogin() throws Exception {
        mockMvc.perform(get("/delivery"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("/oauth2/authorization/kakao")));
    }

    @Test
    @DisplayName("시나리오 2: WebGL에서 카카오 로그인 완료 후 쿠키(accessToken)를 지닌 채 배송 신청 페이지(/delivery) 진입 성공")
    void authenticatedWebGLUser_withAccessTokenCookie_accessDelivery_success() throws Exception {
        mockMvc.perform(get("/delivery")
                        .cookie(new Cookie("accessToken", validAccessToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    @DisplayName("시나리오 3: WebGL에서 쿠키(accessToken)를 지닌 채 메인 페이지(/) 진입 시 환영 화면 정상 렌더링")
    void authenticatedWebGLUser_withAccessTokenCookie_accessHome_success() throws Exception {
        mockMvc.perform(get("/")
                        .cookie(new Cookie("accessToken", validAccessToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("user"));
    }

    @Test
    @DisplayName("시나리오 4: WebGL 환경에서 쿠키(refreshToken) 기반 RTR 토큰 재발급 성공")
    void rtrTokenReissue_withRefreshTokenCookie_success() throws Exception {
        mockMvc.perform(post("/api/auth/reissue")
                        .cookie(new Cookie("refreshToken", validRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(header().exists("Set-Cookie"));

        // Redis의 토큰이 신규 토큰으로 로테이션(RTR)되었는지 검증
        RefreshToken updatedToken = refreshTokenRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updatedToken.getToken()).isNotEqualTo(validRefreshToken);
    }

    @Test
    @DisplayName("시나리오 5: RTR 탈취 감지 - 이미 사용된 이전 Refresh Token으로 재발급 시도 시 차단 및 Redis 세션 삭제")
    void rtrTheftDetection_outdatedRefreshToken_failsAndRevokesToken() {
        // 1차 정상 RTR 재발급 진행 -> Redis에는 newRefreshToken이 저장됨
        TokenResponseDto response = authService.reissue(validRefreshToken);
        String newRefreshToken = response.refreshToken();

        // 공격자(또는 과거 토큰)가 이전 validRefreshToken으로 재발급을 시도할 때 탈취 감지 예외 발생
        assertThatThrownBy(() -> authService.reissue(validRefreshToken))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("토큰 탈취 위험이 감지되어 강제 로그아웃되었습니다.");

        // Redis에 보관된 해당 유저의 세션이 전면 삭제되었는지 검증
        assertThat(refreshTokenRepository.findById(testUser.getId())).isEmpty();
    }

    @Test
    @DisplayName("시나리오 6: 향후 유니티 직접 통신 상정 - Authorization Bearer 헤더로 요청 시에도 인증 성공")
    void futureUnityDirectApiCall_withBearerHeader_accessDelivery_success() throws Exception {
        mockMvc.perform(get("/delivery")
                        .header("Authorization", "Bearer " + validAccessToken))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery"))
                .andExpect(model().attributeExists("user"));
    }
}
