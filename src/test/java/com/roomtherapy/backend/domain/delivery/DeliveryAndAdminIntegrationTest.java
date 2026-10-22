package com.roomtherapy.backend.domain.delivery;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderRequestDto;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.goods.service.RedisGoodsStockService;
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
    private GoodsRepository goodsRepository;

    @Autowired
    private GoodsAuthCodeRepository goodsAuthCodeRepository;

    @Autowired
    private RedisGoodsStockService redisGoodsStockService;

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
        goodsAuthCodeRepository.deleteAll();
        goodsRepository.deleteAll();
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

    @Test
    @DisplayName("사용자가 내 배송 목록(/delivery/my) 조회 시 마이페이지 배송 내역 탭으로 리다이렉트")
    void user_getMyDeliveries_redirectsToMyPage() throws Exception {
        mockMvc.perform(get("/delivery/my")
                        .cookie(new Cookie("accessToken", userToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage?tab=orders"));
    }

    @Test
    @DisplayName("사용자가 배송지 정보를 수정하면 isModified가 true가 되고 어드민 수정 목록에 노출")
    void user_updateDeliveryOrder_marksModified_andAppearsInAdminFilter() throws Exception {
        DeliveryOrder order = deliveryOrderRepository.save(DeliveryOrder.builder()
                .user(normalUser)
                .stageCode("ew-edit-code-1")
                .recipientName("초기수령인")
                .phone("010-1111-2222")
                .zipcode("12345")
                .address("초기주소")
                .status(DeliveryStatus.ORDERED)
                .build());

        // 1. 배송지 수정 폼 조회
        mockMvc.perform(get("/delivery/edit/" + order.getId())
                        .cookie(new Cookie("accessToken", userToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery-edit"))
                .andExpect(model().attributeExists("form", "order"));

        // 2. 배송지 수정 제출
        mockMvc.perform(post("/delivery/edit/" + order.getId())
                        .cookie(new Cookie("accessToken", userToken))
                        .param("recipientName", "수정된수령인")
                        .param("phone", "010-9999-7777")
                        .param("zipcode", "54321")
                        .param("address", "수정된주소")
                        .param("detailAddress", "302호"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage?tab=orders"))
                .andExpect(flash().attributeExists("successMessage"));

        // 3. DB 확인: isModified == true, 수령인 및 주소 업데이트 확인
        DeliveryOrder modifiedOrder = deliveryOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(modifiedOrder.isModified()).isTrue();
        assertThat(modifiedOrder.getRecipientName()).isEqualTo("수정된수령인");
        assertThat(modifiedOrder.getPhone()).isEqualTo("010-9999-7777");
        assertThat(modifiedOrder.getAddress()).isEqualTo("수정된주소");

        // 4. 어드민이 수정된 신청 목록 필터링(?filter=modified) 조회 시 해당 주문이 포함되는지 검증
        mockMvc.perform(get("/admin/deliveries?filter=modified")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/deliveries"))
                .andExpect(model().attribute("currentFilter", "modified"))
                .andExpect(model().attribute("modifiedCount", 1));
    }

    @Test
    @DisplayName("인증코드에 형식 제한 없이 임의의 코드(예: ㅁㅁ)로도 정상 신청 가능")
    void submitDelivery_arbitraryStageCode_success() throws Exception {
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", userToken))
                        .param("stageCode", "ㅁㅁ") // 임의의 한글/자유 형식 코드
                        .param("recipientName", "홍길동")
                        .param("phone", "010-1234-5678")
                        .param("zipcode", "12345")
                        .param("address", "서울특별시 강남구 테헤란로 123")
                        .param("detailAddress", "101호"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/delivery/confirm/*"));

        DeliveryOrder saved = deliveryOrderRepository.findAll().get(0);
        assertThat(saved.getStageCode()).isEqualTo("ㅁㅁ");
    }

    @Test
    @DisplayName("사용자 권한 전환 API(/user/role) 호출 시 SELLER로 변경되고 새 토큰 쿠키 발급")
    void switchRole_updatesUserRoleAndCookie() throws Exception {
        mockMvc.perform(post("/user/role")
                        .cookie(new Cookie("accessToken", userToken))
                        .param("role", "SELLER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        User updatedUser = userRepository.findById(normalUser.getId()).orElseThrow();
        assertThat(updatedUser.getRole()).isEqualTo(Role.SELLER);
    }

    @Test
    @DisplayName("원클릭 테스트 로그인(/test/login?role=SELLER) 성공")
    void testLogin_seller_success() throws Exception {
        mockMvc.perform(get("/test/login").param("role", "SELLER"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(cookie().exists("accessToken"));
    }

    @Test
    @DisplayName("굿즈 상품 목록(/goods) 조회 성공")
    void getGoodsList_success() throws Exception {
        mockMvc.perform(get("/goods"))
                .andExpect(status().isOk())
                .andExpect(view().name("goods/list"))
                .andExpect(model().attributeExists("goodsList"));
    }

    @Test
    @DisplayName("판매자가 등록한 굿즈를 일반유저가 배송 신청 -> 판매자 대시보드(/seller/deliveries)에서 조회 성공")
    void sellerGoods_orderedByUser_visibleInSellerDashboard() throws Exception {
        // 1. 판매자 생성 및 굿즈 등록
        User seller = userRepository.save(User.builder()
                .kakaoId("seller_test_99")
                .nickname("테스트판매자")
                .email("seller99@test.com")
                .role(Role.SELLER)
                .build());

        var goods = goodsRepository.save(Goods.builder()
                .name("수제 원목 시계")
                .description("핸드메이드 원목 시계")
                .price(0)
                .stockQuantity(10)
                .seller(seller)
                .build());

        redisGoodsStockService.syncStock(goods.getId(), 10);

        String sellerToken = jwtProvider.createAccessToken(seller.getId(), seller.getEmail(), seller.getRole().name());

        // 2. 일반 유저가 해당 굿즈 배송 신청
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", userToken))
                        .param("goodsId", String.valueOf(goods.getId()))
                        .param("stageCode", "SELLER-REWARD-001")
                        .param("recipientName", "구매자")
                        .param("phone", "010-8888-9999")
                        .param("zipcode", "55555")
                        .param("address", "서울시 마포구")
                        .param("detailAddress", "1층"))
                .andExpect(status().is3xxRedirection());

        // 3. 재고 차감 검증
        var updatedGoods = goodsRepository.findById(goods.getId()).orElseThrow();
        assertThat(updatedGoods.getStockQuantity()).isEqualTo(9);

        // 4. 판매자가 본인 대시보드(/seller/deliveries) 접속 시 해당 주문이 노출되는지 검증
        mockMvc.perform(get("/seller/deliveries")
                        .cookie(new Cookie("accessToken", sellerToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/deliveries"))
                .andExpect(model().attribute("allCount", 1));
    }

    @Test
    @DisplayName("마이페이지(/mypage) 프로필 탭 기본 조회 성공")
    void myPage_viewProfile_success() throws Exception {
        mockMvc.perform(get("/mypage")
                        .cookie(new Cookie("accessToken", userToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage"))
                .andExpect(model().attributeExists("userProfile", "nicknameForm", "deliveryInfoForm"))
                .andExpect(model().attribute("currentTab", "profile"));
    }

    @Test
    @DisplayName("마이페이지(/mypage?tab=orders) 주문 내역 탭 조회 및 페이징 객체 검증")
    void myPage_viewOrdersTab_success() throws Exception {
        deliveryOrderRepository.save(DeliveryOrder.builder()
                .user(normalUser)
                .stageCode("ew-my-tab-1")
                .recipientName("홍길동")
                .phone("010-1234-5678")
                .zipcode("12345")
                .address("서울특별시")
                .status(DeliveryStatus.ORDERED)
                .build());

        mockMvc.perform(get("/mypage?tab=orders")
                        .cookie(new Cookie("accessToken", userToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("mypage"))
                .andExpect(model().attributeExists("orders"))
                .andExpect(model().attribute("currentTab", "orders"));
    }

    @Test
    @DisplayName("마이페이지 닉네임 수정 시 커스텀 플래그 설정 및 카카오 동기화 시 덮어쓰기 방지 검증")
    void myPage_updateNickname_preservesAgainstKakaoSync() throws Exception {
        mockMvc.perform(post("/mypage/nickname")
                        .cookie(new Cookie("accessToken", userToken))
                        .param("nickname", "수정된마이닉네임"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage?tab=profile"))
                .andExpect(flash().attributeExists("successMessage"));

        User updatedUser = userRepository.findById(normalUser.getId()).orElseThrow();
        assertThat(updatedUser.getNickname()).isEqualTo("수정된마이닉네임");
        assertThat(updatedUser.isCustomNickname()).isTrue();

        // 카카오 로그인 재진입 시도시 닉네임이 카카오 정보로 덮어씌워지지 않는지 도메인 검증
        updatedUser.update("카카오새로운이름", null);
        assertThat(updatedUser.getNickname()).isEqualTo("수정된마이닉네임");
    }

    @Test
    @DisplayName("마이페이지 기본 배송지 저장 성공")
    void myPage_updateDeliveryInfo_success() throws Exception {
        mockMvc.perform(post("/mypage/delivery-info")
                        .cookie(new Cookie("accessToken", userToken))
                        .param("recipientName", "기본수령인")
                        .param("phone", "010-3333-4444")
                        .param("zipcode", "06000")
                        .param("address", "서울시 강남구 압구정로")
                        .param("detailAddress", "101호"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mypage?tab=profile"))
                .andExpect(flash().attributeExists("successMessage"));

        User updatedUser = userRepository.findById(normalUser.getId()).orElseThrow();
        assertThat(updatedUser.getRecipientName()).isEqualTo("기본수령인");
        assertThat(updatedUser.getPhone()).isEqualTo("010-3333-4444");
        assertThat(updatedUser.getZipcode()).isEqualTo("06000");
        assertThat(updatedUser.getAddress()).isEqualTo("서울시 강남구 압구정로");
        assertThat(updatedUser.getDetailAddress()).isEqualTo("101호");
    }

    @Test
    @DisplayName("기본 배송지가 등록된 유저는 배송 신청 페이지(/delivery) 진입 시 폼에 기본 정보 자동 채움")
    void deliveryForm_autoFillsDefaultDeliveryInfo() throws Exception {
        normalUser.updateDefaultDeliveryInfo("자동완성수령인", "010-7777-8888", "04524", "서울시 중구 세종대로", "본관 3층");
        userRepository.save(normalUser);

        mockMvc.perform(get("/delivery")
                        .cookie(new Cookie("accessToken", userToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery"))
                .andExpect(model().attributeExists("form"))
                .andExpect(result -> {
                    var form = (DeliveryOrderRequestDto) result.getModelAndView().getModel().get("form");
                    assertThat(form.recipientName()).isEqualTo("자동완성수령인");
                    assertThat(form.phone()).isEqualTo("010-7777-8888");
                    assertThat(form.zipcode()).isEqualTo("04524");
                    assertThat(form.address()).isEqualTo("서울시 중구 세종대로");
                    assertThat(form.detailAddress()).isEqualTo("본관 3층");
                });
    }

    @Test
    @DisplayName("배송 신청 시 '기본 배송지로 저장' 체크 시 마이페이지 기본 배송지로 자동 동기화")
    void deliverySubmit_saveAsDefaultAddress_updatesUserProfile() throws Exception {
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", userToken))
                        .param("stageCode", "ew-autofill-test")
                        .param("recipientName", "새기본수령인")
                        .param("phone", "010-5555-6666")
                        .param("zipcode", "03186")
                        .param("address", "서울시 종로구 세종대로")
                        .param("detailAddress", "201호")
                        .param("saveAsDefaultAddress", "true"))
                .andExpect(status().is3xxRedirection());

        User updatedUser = userRepository.findById(normalUser.getId()).orElseThrow();
        assertThat(updatedUser.getRecipientName()).isEqualTo("새기본수령인");
        assertThat(updatedUser.getPhone()).isEqualTo("010-5555-6666");
        assertThat(updatedUser.getZipcode()).isEqualTo("03186");
        assertThat(updatedUser.getAddress()).isEqualTo("서울시 종로구 세종대로");
    }

    @Test
    @DisplayName("관리자 대시보드 날짜 필터링 및 페이징 파라미터 정상 동작 검증")
    void adminDeliveries_dateFilterAndPagination() throws Exception {
        // 주문 2건 생성
        deliveryOrderRepository.save(DeliveryOrder.builder()
                .user(normalUser)
                .stageCode("ew-date-1")
                .recipientName("수령인1")
                .phone("010-1111-2222")
                .zipcode("11111")
                .address("서울")
                .status(DeliveryStatus.ORDERED)
                .build());

        mockMvc.perform(get("/admin/deliveries")
                        .cookie(new Cookie("accessToken", adminToken))
                        .param("page", "0")
                        .param("size", "10")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/deliveries"))
                .andExpect(model().attributeExists("deliveries", "allCount"))
                .andExpect(model().attribute("startDate", java.time.LocalDate.of(2026, 1, 1)))
                .andExpect(model().attribute("endDate", java.time.LocalDate.of(2026, 12, 31)));
    }
}
