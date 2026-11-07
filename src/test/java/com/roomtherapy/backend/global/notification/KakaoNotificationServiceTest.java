package com.roomtherapy.backend.global.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.roomtherapy.backend.domain.delivery.controller.DeliveryController;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.service.DeliveryOrderService;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.error.BusinessException;
import com.roomtherapy.backend.global.error.ErrorCode;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.ui.ConcurrentModel;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KakaoNotificationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private KakaoNotificationService.KakaoApiSender kakaoApiSender;

    @Mock
    private DeliveryOrderService deliveryOrderService;

    @Mock
    private GoodsRepository goodsRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private KakaoNotificationService kakaoNotificationService;

    @BeforeEach
    void setUp() {
        kakaoNotificationService = new KakaoNotificationService(userRepository, objectMapper, kakaoApiSender);
        ReflectionTestUtils.setField(kakaoNotificationService, "baseUrl", "https://www.roomtherapy.kr");
        ReflectionTestUtils.setField(kakaoNotificationService, "kakaoClientId", "test-client-id");
        ReflectionTestUtils.setField(kakaoNotificationService, "kakaoClientSecret", "test-client-secret");
    }

    @Test
    @DisplayName("1. [룸메이트 -> 판매자] 신규 굿즈 신청 시 인증코드/전화번호 없이 신청일시·굿즈·주소·수령인과 배송관리 버튼이 전송된다")
    void sendOrderCreatedSync_FormatsMessageAndButtonCorrectly() throws Exception {
        User seller = User.builder()
                .kakaoId("seller-1")
                .nickname("판매자")
                .role(Role.SELLER)
                .build();
        ReflectionTestUtils.setField(seller, "id", 10L);
        seller.updateKakaoTokens("valid-seller-access-token", "valid-seller-refresh-token");

        when(userRepository.findById(10L)).thenReturn(Optional.of(seller));
        when(kakaoApiSender.sendMemo(eq("valid-seller-access-token"), anyString())).thenReturn(200);

        LocalDateTime orderedAt = LocalDateTime.of(2026, 11, 7, 21, 35);
        String fullAddress = KakaoNotificationService.formatFullAddress("04000", "서울 마포구 어울마당로 1", "101호");

        kakaoNotificationService.sendOrderCreatedSync(10L, "룸테라피 힐링 엽서 세트", fullAddress, "정용현", orderedAt);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(kakaoApiSender).sendMemo(eq("valid-seller-access-token"), jsonCaptor.capture());

        JsonNode payload = objectMapper.readTree(jsonCaptor.getValue());
        String text = payload.get("text").asText();

        assertThat(text).contains("🔔 [룸테라피 신규 굿즈 신청 알림]");
        assertThat(text).contains("• 신청 일시: 2026-11-07 21:35");
        assertThat(text).contains("• 신청 굿즈: 룸테라피 힐링 엽서 세트");
        assertThat(text).contains("• 배송 주소: (04000) 서울 마포구 어울마당로 1 101호");
        assertThat(text).contains("• 수령인: 정용현 님");
        assertThat(text).doesNotContain("인증 코드");
        assertThat(text).doesNotContain("연락처");

        assertThat(payload.get("button_title").asText()).isEqualTo("📦 판매자 배송 관리 바로가기");
        assertThat(payload.get("link").get("web_url").asText()).isEqualTo("https://www.roomtherapy.kr/seller/deliveries");
    }

    @Test
    @DisplayName("2. [룸메이트 -> 판매자] 배송지 수정 시(옵션 A) 변경된 항목 라벨에만 '(변경)'이 붙고 최종 주소와 수령인이 모두 표시된다")
    void buildOrderModifiedText_OptionA_MarksOnlyChangedFields() {
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 11, 7, 21, 40);

        // Case 1: 주소만 변경된 경우
        String addressOnlyChanged = kakaoNotificationService.buildOrderModifiedText(
                15L, "룸테라피 힐링 엽서 세트", "(06000) 서울 강남구 테헤란로 10 202호", true, "정용현", false, modifiedAt);
        assertThat(addressOnlyChanged).contains("• 주문 번호: #15 (룸테라피 힐링 엽서 세트)");
        assertThat(addressOnlyChanged).contains("• 배송 주소(변경): (06000) 서울 강남구 테헤란로 10 202호");
        assertThat(addressOnlyChanged).contains("• 수령인: 정용현 님");
        assertThat(addressOnlyChanged).doesNotContain("수령인(변경)");

        // Case 2: 수령인만 변경된 경우
        String recipientOnlyChanged = kakaoNotificationService.buildOrderModifiedText(
                15L, "룸테라피 힐링 엽서 세트", "(06000) 서울 강남구 테헤란로 10 202호", false, "홍길동", true, modifiedAt);
        assertThat(recipientOnlyChanged).contains("• 배송 주소: (06000) 서울 강남구 테헤란로 10 202호");
        assertThat(recipientOnlyChanged).contains("• 수령인(변경): 홍길동 님");
        assertThat(recipientOnlyChanged).doesNotContain("배송 주소(변경)");

        // Case 3: 주소와 수령인 모두 변경된 경우
        String bothChanged = kakaoNotificationService.buildOrderModifiedText(
                15L, "룸테라피 힐링 엽서 세트", "(06000) 서울 강남구 테헤란로 10 202호", true, "홍길동", true, modifiedAt);
        assertThat(bothChanged).contains("• 배송 주소(변경): (06000) 서울 강남구 테헤란로 10 202호");
        assertThat(bothChanged).contains("• 수령인(변경): 홍길동 님");
    }

    @Test
    @DisplayName("3. [판매자/관리자 -> 룸메이트] 배송 준비중(PREPARING) 및 발송 완료(SHIPPED) 전환 시 룸메이트에게 카톡 알림이 전송된다")
    void sendStatusChangedSync_SendsPreparingAndShippedMessages() throws Exception {
        User roommate = User.builder()
                .kakaoId("roommate-1")
                .nickname("룸메이트")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(roommate, "id", 20L);
        roommate.updateKakaoTokens("roommate-access-token", "roommate-refresh-token");

        when(userRepository.findById(20L)).thenReturn(Optional.of(roommate));
        when(kakaoApiSender.sendMemo(eq("roommate-access-token"), anyString())).thenReturn(200);

        LocalDateTime now = LocalDateTime.of(2026, 11, 7, 22, 0);

        // 1) 배송 준비중
        kakaoNotificationService.sendStatusChangedSync(20L, "룸테라피 힐링 엽서 세트", DeliveryStatus.PREPARING, now);
        // 2) 발송 완료
        kakaoNotificationService.sendStatusChangedSync(20L, "룸테라피 힐링 엽서 세트", DeliveryStatus.SHIPPED, now);
        // 3) 그 외 상태(DELIVERED)는 발송하지 않음
        kakaoNotificationService.sendStatusChangedSync(20L, "룸테라피 힐링 엽서 세트", DeliveryStatus.DELIVERED, now);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(kakaoApiSender, times(2)).sendMemo(eq("roommate-access-token"), jsonCaptor.capture());

        JsonNode preparingPayload = objectMapper.readTree(jsonCaptor.getAllValues().get(0));
        assertThat(preparingPayload.get("text").asText()).contains("🎁 [룸테라피 배송 상태 안내]");
        assertThat(preparingPayload.get("text").asText()).contains("• 현재 상태: 배송 준비중");
        assertThat(preparingPayload.get("button_title").asText()).isEqualTo("🔍 내 배송 현황 확인하기");
        assertThat(preparingPayload.get("link").get("web_url").asText()).isEqualTo("https://www.roomtherapy.kr/mypage?tab=orders");

        JsonNode shippedPayload = objectMapper.readTree(jsonCaptor.getAllValues().get(1));
        assertThat(shippedPayload.get("text").asText()).contains("🚚 [룸테라피 배송 출발 안내]");
        assertThat(shippedPayload.get("text").asText()).contains("• 현재 상태: 발송 완료");
    }

    @Test
    @DisplayName("4. 카카오 Access Token 만료(401) 시 저장된 Refresh Token으로 자동 갱신 후 재발송에 성공한다")
    void sendKakaoMessage_WhenAccessTokenExpired401_AutoRefreshesAndRetries() throws Exception {
        User seller = User.builder()
                .kakaoId("seller-expired")
                .nickname("판매자")
                .role(Role.SELLER)
                .build();
        ReflectionTestUtils.setField(seller, "id", 30L);
        seller.updateKakaoTokens("expired-access-token", "valid-refresh-token");

        when(userRepository.findById(30L)).thenReturn(Optional.of(seller));
        when(kakaoApiSender.sendMemo(eq("expired-access-token"), anyString())).thenReturn(401);
        when(kakaoApiSender.refreshAccessToken(eq("test-client-id"), eq("test-client-secret"), eq("valid-refresh-token")))
                .thenReturn("{\"access_token\":\"new-refreshed-access-token\",\"refresh_token\":\"new-refresh-token\"}");
        when(kakaoApiSender.sendMemo(eq("new-refreshed-access-token"), anyString())).thenReturn(200);

        kakaoNotificationService.sendOrderCreatedSync(
                30L, "엽서 세트", "(04000) 서울 마포구", "정용현", LocalDateTime.of(2026, 11, 7, 21, 35));

        verify(kakaoApiSender).sendMemo(eq("expired-access-token"), anyString());
        verify(kakaoApiSender).refreshAccessToken("test-client-id", "test-client-secret", "valid-refresh-token");
        verify(kakaoApiSender).sendMemo(eq("new-refreshed-access-token"), anyString());
        verify(userRepository).save(seller);
        assertThat(seller.getKakaoAccessToken()).isEqualTo("new-refreshed-access-token");
        assertThat(seller.getKakaoRefreshToken()).isEqualTo("new-refresh-token");
    }

    @Test
    @DisplayName("5. 카톡 발송 중 네트워크 예외가 발생해도 비즈니스 로직으로 예외가 전파되지 않는다 (Fault Isolation)")
    void sendKakaoMessage_WhenExceptionOccurs_DoesNotPropagateException() throws Exception {
        User seller = User.builder()
                .kakaoId("seller-error")
                .nickname("판매자")
                .role(Role.SELLER)
                .build();
        ReflectionTestUtils.setField(seller, "id", 40L);
        seller.updateKakaoTokens("access-token", "refresh-token");

        when(userRepository.findById(40L)).thenReturn(Optional.of(seller));
        when(kakaoApiSender.sendMemo(anyString(), anyString())).thenThrow(new RuntimeException("카카오 서버 일시 장애"));

        assertThatCode(() -> kakaoNotificationService.sendOrderCreatedSync(
                40L, "엽서 세트", "(04000) 서울 마포구", "정용현", LocalDateTime.now()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("6. 발송 완료(SHIPPED)된 주문의 수정 폼(GET /delivery/edit/{orderId})에 URL로 직접 접근하면 ORDER_CANNOT_BE_MODIFIED 예외가 발생한다")
    void editDeliveryForm_WhenOrderIsShipped_ThrowsOrderCannotBeModified() {
        DeliveryController controller = new DeliveryController(deliveryOrderService, goodsRepository, userRepository);

        User user = User.builder()
                .kakaoId("user-1")
                .nickname("룸메이트")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        DeliveryOrder orderEntity = DeliveryOrder.builder()
                .user(user)
                .snapshotGoodsName("엽서 세트")
                .stageCode("STAGE-1")
                .recipientName("정용현")
                .phone("")
                .zipcode("04000")
                .address("서울 마포구")
                .detailAddress("101호")
                .status(DeliveryStatus.SHIPPED)
                .build();
        ReflectionTestUtils.setField(orderEntity, "id", 99L);

        DeliveryOrderResponseDto shippedOrder = DeliveryOrderResponseDto.from(orderEntity);
        when(deliveryOrderService.getOrder(99L)).thenReturn(shippedOrder);

        CustomUserDetails principal = new CustomUserDetails(user);
        ConcurrentModel model = new ConcurrentModel();

        assertThatThrownBy(() -> controller.editDeliveryForm(99L, principal, model))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ORDER_CANNOT_BE_MODIFIED);
    }

    @Test
    @DisplayName("7. 배송 준비중(PREPARING) 상태인 주문은 수정 폼(GET /delivery/edit/{orderId})에 정상 접근 가능하다")
    void editDeliveryForm_WhenOrderIsPreparing_ReturnsEditView() {
        DeliveryController controller = new DeliveryController(deliveryOrderService, goodsRepository, userRepository);

        User user = User.builder()
                .kakaoId("user-1")
                .nickname("룸메이트")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);

        DeliveryOrder orderEntity = DeliveryOrder.builder()
                .user(user)
                .snapshotGoodsName("엽서 세트")
                .stageCode("STAGE-1")
                .recipientName("정용현")
                .phone("")
                .zipcode("04000")
                .address("서울 마포구")
                .detailAddress("101호")
                .status(DeliveryStatus.PREPARING)
                .build();
        ReflectionTestUtils.setField(orderEntity, "id", 100L);

        DeliveryOrderResponseDto preparingOrder = DeliveryOrderResponseDto.from(orderEntity);
        when(deliveryOrderService.getOrder(100L)).thenReturn(preparingOrder);

        CustomUserDetails principal = new CustomUserDetails(user);
        ConcurrentModel model = new ConcurrentModel();

        String viewName = controller.editDeliveryForm(100L, principal, model);
        assertThat(viewName).isEqualTo("delivery/edit");
    }
}
