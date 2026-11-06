package com.roomtherapy.backend.global.security;

import com.roomtherapy.backend.domain.auth.service.AuthService;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.delivery.service.DeliveryExcelService;
import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.goods.service.GoodsService;
import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.error.BusinessException;
import com.roomtherapy.backend.global.error.ErrorCode;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import com.roomtherapy.backend.global.storage.StorageService;
import jakarta.servlet.http.Cookie;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class SecurityHardeningIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private GoodsAuthCodeRepository goodsAuthCodeRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private DeliveryExcelService deliveryExcelService;

    @Autowired
    private StorageService storageService;

    private User normalUser;
    private User sellerUser;
    private User adminUser;
    private String userAccessToken;
    private String userRefreshToken;
    private String sellerAccessToken;
    private String adminAccessToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        deliveryOrderRepository.deleteAll();
        goodsAuthCodeRepository.deleteAll();
        goodsRepository.deleteAll();
        userRepository.deleteAll();

        normalUser = userRepository.save(User.builder()
                .kakaoId("sec_user_1")
                .nickname("보안유저")
                .email("sec_user@test.com")
                .role(Role.USER)
                .build());

        sellerUser = userRepository.save(User.builder()
                .kakaoId("sec_seller_1")
                .nickname("보안판매자")
                .email("sec_seller@test.com")
                .role(Role.SELLER)
                .build());

        adminUser = userRepository.save(User.builder()
                .kakaoId("sec_admin_1")
                .nickname("보안관리자")
                .email("sec_admin@test.com")
                .role(Role.ADMIN)
                .build());

        userAccessToken = jwtProvider.createAccessToken(normalUser.getId(), normalUser.getEmail(), normalUser.getRole().name());
        userRefreshToken = jwtProvider.createRefreshToken(normalUser.getId());
        sellerAccessToken = jwtProvider.createAccessToken(sellerUser.getId(), sellerUser.getEmail(), sellerUser.getRole().name());
        adminAccessToken = jwtProvider.createAccessToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole().name());
    }

    @Test
    @DisplayName("보안 1: 기본 리워드(goodsId=null)에서 등록되지 않은 임의 문자열 코드는 배송 신청 및 사전 검증에서 차단됨")
    void unregisteredDefaultStageCode_isRejected() throws Exception {
        // 1. 사전 검증 API (GET /api/goods/verify-code)에서 미등록 코드 거절
        mockMvc.perform(get("/api/goods/verify-code")
                        .cookie(new Cookie("accessToken", userAccessToken))
                        .param("code", "random-unregistered-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(false));

        // 2. 배송 신청 (/delivery)에서 미등록 코드 거절
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", userAccessToken))
                        .param("stageCode", "random-unregistered-code")
                        .param("recipientName", "홍길동")
                        .param("phone", "010-1111-2222")
                        .param("zipcode", "12345")
                        .param("address", "서울시 강남구"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery/apply"))
                .andExpect(model().attributeExists("errorMessage"));

        assertThat(deliveryOrderRepository.count()).isZero();
    }

    @Test
    @DisplayName("보안 2: 유니티 동적 코드 발급 API는 비로그인 호출 및 비정상 durationMinutes 범위를 차단함")
    void unityIssueCode_requiresAuthAndValidDuration() throws Exception {
        GoodsResponseDto goods = goodsService.registerGoods(sellerUser.getId(), new GoodsCreateRequestDto(
                "유니티 보안 굿즈", "설명", 0, "https://img.test/a.png", 10, "CODE-1"
        ));

        // 1. 비인증 호출 시 401 Unauthorized 차단
        mockMvc.perform(post("/api/unity/goods/" + goods.id() + "/issue-code")
                        .param("durationMinutes", "30"))
                .andExpect(status().isUnauthorized());

        // 2. 비정상 TTL (0분 또는 1440분 초과) 요청 시 400 Bad Request
        mockMvc.perform(post("/api/unity/goods/" + goods.id() + "/issue-code")
                        .cookie(new Cookie("accessToken", userAccessToken))
                        .param("durationMinutes", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));

        mockMvc.perform(post("/api/unity/goods/" + goods.id() + "/issue-code")
                        .cookie(new Cookie("accessToken", userAccessToken))
                        .param("durationMinutes", "99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_INPUT_VALUE.getCode()));
    }

    @Test
    @DisplayName("보안 3: Refresh Token을 Access Token으로 사용하거나 Access Token으로 재발급(reissue) 시도 시 차단됨")
    void tokenTypeConfusion_isBlocked() throws Exception {
        assertThat(jwtProvider.isAccessToken(userAccessToken)).isTrue();
        assertThat(jwtProvider.isRefreshToken(userAccessToken)).isFalse();
        assertThat(jwtProvider.isRefreshToken(userRefreshToken)).isTrue();
        assertThat(jwtProvider.isAccessToken(userRefreshToken)).isFalse();

        // 1. Refresh Token을 accessToken 쿠키에 담아 인증 필요 페이지 접근 시 인증되지 않음 (카카오 로그인 페이지로 리다이렉트)
        mockMvc.perform(post("/user/role")
                        .cookie(new Cookie("accessToken", userRefreshToken))
                        .param("role", "SELLER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oauth2/authorization/kakao"));

        // 2. Access Token을 AuthService.reissue()에 전달 시 INVALID_REFRESH_TOKEN 예외 발생
        assertThatThrownBy(() -> authService.reissue(userAccessToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    @DisplayName("보안 4: Open Redirect 외부 도메인(https://evil.com, //evil.com) 파라미터 전달 시 내부 기본 경로로 안전하게 리다이렉트됨")
    void openRedirect_isBlockedAndFallsBackToSafeInternalPath() throws Exception {
        GoodsResponseDto goods = goodsService.registerGoods(sellerUser.getId(), new GoodsCreateRequestDto(
                "리다이렉트 테스트 굿즈", "설명", 0, "https://img.test/a.png", 10, "CODE-REDIR"
        ));

        // 외부 URL 주입 시도 -> /goods로 안전하게 폴백
        mockMvc.perform(post("/seller/goods/" + goods.id() + "/stock")
                        .cookie(new Cookie("accessToken", sellerAccessToken))
                        .param("stockQuantity", "20")
                        .param("redirect", "https://evil.com/phishing"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        // 프로토콜 상대 URL(//evil.com) 주입 시도 -> /goods로 안전하게 폴백
        mockMvc.perform(post("/seller/goods/" + goods.id() + "/stock")
                        .cookie(new Cookie("accessToken", sellerAccessToken))
                        .param("stockQuantity", "25")
                        .param("redirect", "//evil.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));
    }

    @Test
    @DisplayName("보안 5: 엑셀 다운로드 시 수식 인젝션 문자(=, +, -, @)로 시작하는 셀 값 앞에 작은따옴표(')가 자동 추가됨")
    void excelFormulaInjection_isSanitized() throws Exception {
        DeliveryOrder maliciousOrder = DeliveryOrder.builder()
                .user(normalUser)
                .stageCode("ew-stage1-clear-01")
                .recipientName("=cmd|' /C calc'!A0")
                .phone("+82-10-1234-5678")
                .zipcode("12345")
                .address("@SUM(1+1)")
                .detailAddress("-101호")
                .feedback("=HYPERLINK(\"http://evil.com\")")
                .status(DeliveryStatus.ORDERED)
                .build();

        byte[] excelBytes = deliveryExcelService.exportDeliveriesToExcel(
                List.of(maliciousOrder),
                List.of("recipientName", "phone", "address", "feedback")
        );

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(excelBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            String recipientCell = sheet.getRow(1).getCell(0).getStringCellValue();
            String phoneCell = sheet.getRow(1).getCell(1).getStringCellValue();
            String addressCell = sheet.getRow(1).getCell(2).getStringCellValue();
            String feedbackCell = sheet.getRow(1).getCell(3).getStringCellValue();

            assertThat(recipientCell).startsWith("'=cmd|");
            assertThat(phoneCell).startsWith("'+82");
            assertThat(addressCell).startsWith("'@SUM");
            assertThat(feedbackCell).startsWith("'=HYPERLINK");
        }
    }

    @Test
    @DisplayName("보안 6: 스토리지 파일 업로드 시 허용되지 않은 위험 확장자(.html, .svg, .jsp)는 차단됨")
    void storageUpload_blocksDangerousFileExtensions() {
        MockMultipartFile htmlFile = new MockMultipartFile(
                "file", "xss.html", "text/html", "<script>alert(1)</script>".getBytes()
        );
        MockMultipartFile svgFile = new MockMultipartFile(
                "file", "xss.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes()
        );

        assertThatThrownBy(() -> storageService.uploadFile(htmlFile, "goods"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 파일 확장자");

        assertThatThrownBy(() -> storageService.uploadFile(svgFile, "goods"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 파일 확장자");
    }

    @Test
    @DisplayName("보안 7 (Silent Refresh): Access Token이 없거나 만료되어도 유효한 Refresh Token 쿠키가 있으면 페이지 접근 성공 및 새 토큰 쿠키 자동 발급")
    void silentRefresh_withValidRefreshToken_autoAuthenticatesAndRefreshesCookies() throws Exception {
        // Given: Redis에 정상 저장된 Refresh Token 준비
        com.roomtherapy.backend.global.security.jwt.RefreshToken tokenEntity =
                com.roomtherapy.backend.global.security.jwt.RefreshToken.builder()
                        .userId(normalUser.getId())
                        .token(userRefreshToken)
                        .ttl(jwtProvider.getRefreshTokenExpirationSec())
                        .build();
        context.getBean(com.roomtherapy.backend.global.security.jwt.RefreshTokenRepository.class).save(tokenEntity);

        // When: accessToken 쿠키 없이 refreshToken 쿠키만 들고 보호된 경로(/mypage) 요청
        mockMvc.perform(get("/mypage")
                        .cookie(new Cookie("refreshToken", userRefreshToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("user/mypage"))
                .andExpect(model().attributeExists("userProfile"))
                // Then: 응답 헤더(Set-Cookie)에 새 accessToken과 새 refreshToken이 구워졌는지 확인
                .andExpect(header().exists("Set-Cookie"));
    }
}
