package com.roomtherapy.backend.domain.delivery;

import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class DeliveryAndAdminIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private JwtProvider jwtProvider;

    private User normalUser;
    private User adminUser;
    private String userToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        deliveryOrderRepository.deleteAll();
        userRepository.deleteAll();

        normalUser = userRepository.save(User.builder()
                .kakaoId("kakao_user_1")
                .nickname("일반유저")
                .email("user1@roomtherapy.com")
                .role(Role.USER)
                .build());

        adminUser = userRepository.save(User.builder()
                .kakaoId("kakao_admin_1")
                .nickname("총괄관리자")
                .email("admin@roomtherapy.com")
                .role(Role.ADMIN)
                .build());

        userToken = jwtProvider.createAccessToken(normalUser.getId(), normalUser.getEmail(), normalUser.getRole().name());
        adminToken = jwtProvider.createAccessToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole().name());
    }

    @Test
    @DisplayName("정상적인 배송 신청 제출 시 DB에 저장되고 완료 페이지로 리다이렉트")
    void submitDelivery_success() throws Exception {
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", userToken))
                        .param("stageCode", "ew-stage1-clear-01")
                        .param("recipientName", "홍길동")
                        .param("phone", "010-1234-5678")
                        .param("zipcode", "12345")
                        .param("address", "서울특별시 강남구 테헤란로 123")
                        .param("detailAddress", "101동 202호"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/delivery/confirm/*"));

        // DB 검증
        assertThat(deliveryOrderRepository.count()).isEqualTo(1);
        DeliveryOrder savedOrder = deliveryOrderRepository.findAll().get(0);
        assertThat(savedOrder.getStageCode()).isEqualTo("ew-stage1-clear-01");
        assertThat(savedOrder.getRecipientName()).isEqualTo("홍길동");
        assertThat(savedOrder.getPhone()).isEqualTo("010-1234-5678");
        assertThat(savedOrder.getZipcode()).isEqualTo("12345");
        assertThat(savedOrder.getStatus()).isEqualTo(DeliveryStatus.ORDERED);
    }

    @Test
    @DisplayName("연락처 정규식 형식(010-XXXX-XXXX)이 아닐 경우 유효성 검증 실패로 폼 페이지 재표시")
    void submitDelivery_invalidPhone_failsValidation() throws Exception {
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", userToken))
                        .param("stageCode", "ew-stage1-clear-02")
                        .param("recipientName", "홍길동")
                        .param("phone", "01012345678") // 하이픈 없는 잘못된 포맷
                        .param("zipcode", "12345")
                        .param("address", "서울특별시 서초구 반포대로 1")
                        .param("detailAddress", "101호"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery"))
                .andExpect(model().hasErrors());

        assertThat(deliveryOrderRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("일반 사용자(ROLE_USER)가 관리자 배송 목록 페이지(/admin/deliveries) 접근 시 403 권한 차단")
    void normalUser_accessAdminDeliveries_forbidden() throws Exception {
        mockMvc.perform(get("/admin/deliveries")
                        .cookie(new Cookie("accessToken", userToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("관리자(ROLE_ADMIN)가 관리자 배송 목록 페이지(/admin/deliveries) 접근 시 200 OK 정상 접근")
    void adminUser_accessAdminDeliveries_success() throws Exception {
        // 배송 신청 1건 등록
        deliveryOrderRepository.save(DeliveryOrder.builder()
                .user(normalUser)
                .stageCode("ew-sample-01")
                .recipientName("수령자")
                .phone("010-9999-8888")
                .zipcode("54321")
                .address("경기도 성남시 분당구 판교로 1")
                .status(DeliveryStatus.ORDERED)
                .build());

        mockMvc.perform(get("/admin/deliveries")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/deliveries"))
                .andExpect(model().attributeExists("deliveries"));
    }

    @Test
    @DisplayName("관리자가 배송 상태를 배송 준비중(PREPARING)으로 변경 성공")
    void adminUser_updateOrderStatus_success() throws Exception {
        DeliveryOrder order = deliveryOrderRepository.save(DeliveryOrder.builder()
                .user(normalUser)
                .stageCode("ew-sample-02")
                .recipientName("수령자")
                .phone("010-9999-8888")
                .zipcode("54321")
                .address("부산광역시 해운대구")
                .status(DeliveryStatus.ORDERED)
                .build());

        mockMvc.perform(post("/admin/deliveries/" + order.getId() + "/status")
                        .cookie(new Cookie("accessToken", adminToken))
                        .param("status", "PREPARING"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/deliveries"));

        DeliveryOrder updated = deliveryOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(DeliveryStatus.PREPARING);
    }
}
