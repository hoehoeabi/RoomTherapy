package com.roomtherapy.backend.domain.goods;

import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsAuthCodeItemDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsUpdateRequestDto;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.Goods3dObjectRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.goods.service.GoodsService;
import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.error.BusinessException;
import com.roomtherapy.backend.global.error.ErrorCode;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@SpringBootTest
class GoodsManagementIntegrationTest {

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
    private Goods3dObjectRepository goods3dObjectRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private JwtProvider jwtProvider;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ClientRegistrationRepository clientRegistrationRepository() {
            return Mockito.mock(ClientRegistrationRepository.class);
        }
    }

    private User seller1;
    private User seller2;
    private User admin;
    private String seller1Token;
    private String seller2Token;
    private String adminToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        deliveryOrderRepository.deleteAll();
        goods3dObjectRepository.deleteAll();
        goodsAuthCodeRepository.deleteAll();
        goodsRepository.deleteAll();
        userRepository.deleteAll();

        seller1 = userRepository.save(User.builder()
                .kakaoId("seller_001")
                .nickname("판매자1")
                .email("seller1@test.com")
                .role(Role.SELLER)
                .build());

        seller2 = userRepository.save(User.builder()
                .kakaoId("seller_002")
                .nickname("판매자2")
                .email("seller2@test.com")
                .role(Role.SELLER)
                .build());

        admin = userRepository.save(User.builder()
                .kakaoId("admin_001")
                .nickname("관리자")
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build());

        seller1Token = jwtProvider.createAccessToken(seller1.getId(), seller1.getEmail(), seller1.getRole().name());
        seller2Token = jwtProvider.createAccessToken(seller2.getId(), seller2.getEmail(), seller2.getRole().name());
        adminToken = jwtProvider.createAccessToken(admin.getId(), admin.getEmail(), admin.getRole().name());
    }

    @Test
    @DisplayName("판매자 본인은 자신의 굿즈를 수정하고 재고 및 정보를 변경할 수 있다")
    void seller_can_update_own_goods() {
        // given
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 무드등", "설명", 0, "/img.png", 10, "CODE-1\nCODE-2"));

        GoodsUpdateRequestDto updateDto = new GoodsUpdateRequestDto(
                "수정된 무드등", "수정 설명", 5000, "/updated.png", 25, GoodsStatus.ACTIVE, "CODE-3"
        );

        // when
        GoodsResponseDto updated = goodsService.updateGoods(created.id(), seller1.getId(), false, updateDto);

        // then
        assertThat(updated.name()).isEqualTo("수정된 무드등");
        assertThat(updated.stockQuantity()).isEqualTo(25);
        assertThat(updated.authCodeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("다른 판매자의 굿즈는 수정할 수 없으며 NOT_GOODS_OWNER 예외가 발생한다")
    void seller_cannot_update_other_seller_goods() {
        // given
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 무드등", "설명", 0, "/img.png", 10, "CODE-1"));

        GoodsUpdateRequestDto updateDto = new GoodsUpdateRequestDto(
                "해킹시도", "설명", 0, "/img.png", 10, GoodsStatus.ACTIVE, ""
        );

        // when & then
        assertThatThrownBy(() -> goodsService.updateGoods(created.id(), seller2.getId(), false, updateDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("본인이 등록한 굿즈만");
    }

    @Test
    @DisplayName("관리자는 다른 판매자가 등록한 굿즈라도 수정 및 삭제할 수 있다")
    void admin_can_update_and_delete_any_goods() {
        // given
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 무드등", "설명", 0, "/img.png", 10, "CODE-1"));

        GoodsUpdateRequestDto updateDto = new GoodsUpdateRequestDto(
                "관리자가 수정한 굿즈", "관리자 설명", 0, "/img.png", 0, GoodsStatus.ACTIVE, ""
        );

        // when (관리자 권한 수정: 재고를 0으로 변경 시 자동 SOLD_OUT)
        GoodsResponseDto updated = goodsService.updateGoods(created.id(), admin.getId(), true, updateDto);

        // then
        assertThat(updated.name()).isEqualTo("관리자가 수정한 굿즈");
        assertThat(updated.stockQuantity()).isEqualTo(0);
        assertThat(updated.status()).isEqualTo(GoodsStatus.SOLD_OUT);

        // when (관리자 권한 삭제)
        goodsService.deleteGoods(created.id(), admin.getId(), true);

        // then
        assertThat(goodsRepository.findById(created.id())).isEmpty();
    }

    @Test
    @DisplayName("재고가 0이 되면 SOLD_OUT으로 전환되고 양수가 되면 ACTIVE로 복원된다")
    void stock_update_sold_out_transition() {
        // given
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("무드등", "설명", 0, "/img.png", 5, "C-1\nC-2"));

        // when: 재고 0으로 변경
        goodsService.updateGoodsStock(created.id(), seller1.getId(), false, 0);
        Goods goodsZero = goodsRepository.findById(created.id()).orElseThrow();
        assertThat(goodsZero.getStockQuantity()).isEqualTo(0);
        assertThat(goodsZero.isSoldOut()).isTrue();
        assertThat(goodsZero.getStatus()).isEqualTo(GoodsStatus.SOLD_OUT);

        // when: 재고 다시 10으로 채움
        goodsService.updateGoodsStock(created.id(), seller1.getId(), false, 10);
        Goods goodsRefilled = goodsRepository.findById(created.id()).orElseThrow();
        assertThat(goodsRefilled.getStockQuantity()).isEqualTo(10);
        assertThat(goodsRefilled.isSoldOut()).isFalse();
        assertThat(goodsRefilled.getStatus()).isEqualTo(GoodsStatus.ACTIVE);
    }

    @Test
    @DisplayName("MVC: 관리자는 굿즈 수정 화면(/seller/goods/{id}/edit)에 정상 접근할 수 있다")
    void admin_can_access_goods_edit_page() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 무드등", "설명", 0, "/img.png", 10, "CODE-1"));

        mockMvc.perform(get("/seller/goods/" + created.id() + "/edit")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/goods-edit"))
                .andExpect(model().attributeExists("form", "goods"));
    }

    @Test
    @DisplayName("MVC: 판매자는 본인이 등록한 굿즈 수정 화면에 접근할 수 있다")
    void seller_can_access_own_goods_edit_page() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 무드등", "설명", 0, "/img.png", 10, "CODE-1"));

        mockMvc.perform(get("/seller/goods/" + created.id() + "/edit")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/goods-edit"))
                .andExpect(model().attributeExists("form", "goods"));
    }

    @Test
    @DisplayName("MVC: 판매자가 다른 판매자의 굿즈 수정 화면에 접근 시 @PreAuthorize에 의해 403 Forbidden으로 차단된다")
    void seller_cannot_access_other_seller_goods_edit_page() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 무드등", "설명", 0, "/img.png", 10, "CODE-1"));

        mockMvc.perform(get("/seller/goods/" + created.id() + "/edit")
                        .cookie(new Cookie("accessToken", seller2Token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MVC: 판매자는 본인 굿즈의 인증코드 목록(/seller/goods/{id}/codes)만 볼 수 있고 타인은 403 Forbidden 차단된다")
    void view_codes_preauthorize_test() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 무드등", "설명", 0, "/img.png", 10, "CODE-1"));

        // 판매자1은 정상 조회 (200 OK)
        mockMvc.perform(get("/seller/goods/" + created.id() + "/codes")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/goods-codes"));

        // 판매자2는 차단 (403 Forbidden)
        mockMvc.perform(get("/seller/goods/" + created.id() + "/codes")
                        .cookie(new Cookie("accessToken", seller2Token)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("MVC: /goods?filter=my 요청 시 로그인한 판매자가 등록한 굿즈만 필터링되어 반환된다")
    void list_goods_my_filter_test() throws Exception {
        // seller1 굿즈 2개, seller2 굿즈 1개 등록
        goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 굿즈A", "설명", 0, "/imgA.png", 10, "C-1"));
        goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("판매자1 굿즈B", "설명", 0, "/imgB.png", 10, "C-2"));
        goodsService.registerGoods(seller2.getId(),
                new GoodsCreateRequestDto("판매자2 굿즈C", "설명", 0, "/imgC.png", 10, "C-3"));

        // seller1이 filter=my 조회 시 2건만 조회됨
        mockMvc.perform(get("/goods?filter=my")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().isOk())
                .andExpect(view().name("goods/list"))
                .andExpect(model().attribute("currentFilter", "my"))
                .andExpect(result -> {
                    @SuppressWarnings("unchecked")
                    List<Goods> list = (List<Goods>) result.getModelAndView().getModel().get("goodsList");
                    assertThat(list).hasSize(2);
                    assertThat(list).allMatch(g -> g.getSeller().getId().equals(seller1.getId()));
                });

        // filter=all 조회 시 전체 3건 조회됨
        mockMvc.perform(get("/goods?filter=all")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    @SuppressWarnings("unchecked")
                    List<Goods> list = (List<Goods>) result.getModelAndView().getModel().get("goodsList");
                    assertThat(list).hasSize(3);
                });
    }

    @Test
    @DisplayName("MVC: 굿즈 수정 시 이미지 URL의 순서를 변경하거나 특정 이미지를 삭제하면 그대로 DB에 반영된다")
    void update_goods_image_order_and_delete_test() throws Exception {
        // 기존 3장 등록: img1, img2, img3
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("사진순서 굿즈", "설명", 0, "https://s3/img1.jpg,https://s3/img2.jpg,https://s3/img3.jpg", 10, "CODE-IMG-1"));

        // img2 삭제 및 img3를 1번째(대표 사진)로 순서 변경: https://s3/img3.jpg,https://s3/img1.jpg
        mockMvc.perform(post("/seller/goods/" + created.id() + "/edit")
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("name", "사진순서 수정됨")
                        .param("description", "설명 수정")
                        .param("stockQuantity", "10")
                        .param("status", "ACTIVE")
                        .param("imageUrl", "https://s3/img3.jpg,https://s3/img1.jpg"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        Goods updated = goodsRepository.findById(created.id()).orElseThrow();
        assertThat(updated.getImageList()).hasSize(2);
        assertThat(updated.getImageList().get(0)).isEqualTo("https://s3/img3.jpg"); // 대표 사진으로 변경됨
        assertThat(updated.getImageList().get(1)).isEqualTo("https://s3/img1.jpg");
        assertThat(updated.getImageList()).doesNotContain("https://s3/img2.jpg"); // 삭제됨
    }

    @Test
    @DisplayName("MVC: 신규 사진 파일 업로드 시 플레이스홀더를 사용해 기존 이미지보다 앞에 배치하여 대표 사진으로 지정할 수 있다")
    void update_goods_with_new_file_reordered_as_first_test() throws Exception {
        // 기존 1장 등록: https://s3/old.jpg
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("단일사진 굿즈", "설명", 0, "https://s3/old.jpg", 10, "CODE-IMG-2"));

        MockMultipartFile newFile = new MockMultipartFile(
                "imageFiles",
                "new_main.png",
                "image/png",
                "dummy image content".getBytes()
        );

        // 신규 파일을 앞에 배치하는 플레이스홀더 전송: __NEW_FILE_0__,https://s3/old.jpg
        mockMvc.perform(multipart("/seller/goods/" + created.id() + "/edit")
                        .file(newFile)
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("name", "단일사진 굿즈")
                        .param("description", "설명")
                        .param("stockQuantity", "10")
                        .param("status", "ACTIVE")
                        .param("imageUrl", "__NEW_FILE_0__,https://s3/old.jpg"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        Goods updated = goodsRepository.findById(created.id()).orElseThrow();
        assertThat(updated.getImageList()).hasSize(2);
        // 0번째가 업로드된 새 파일 URL이고, 1번째가 기존 old.jpg
        assertThat(updated.getImageList().get(0)).startsWith("/uploads/goods/");
        assertThat(updated.getImageList().get(0)).endsWith(".png");
        assertThat(updated.getImageList().get(1)).isEqualTo("https://s3/old.jpg");
    }

    @Test
    @DisplayName("굿즈 상태를 INACTIVE로 변경 시 일반 사용자에겐 숨겨지고, 관리자 및 판매자 본인에게만 노출된다")
    void goods_inactive_visibility_test() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("숨김 테스트 굿즈", "설명", 0, "https://s3/img.jpg", 10, "CODE-INACTIVE-1"));

        // 판매자가 INACTIVE로 상태 변경
        mockMvc.perform(post("/seller/goods/" + created.id() + "/status")
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("status", "INACTIVE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        Goods goods = goodsRepository.findById(created.id()).orElseThrow();
        assertThat(goods.getStatus()).isEqualTo(GoodsStatus.INACTIVE);

        // 1. 일반 사용자 조회 시 목록에서 숨겨짐
        mockMvc.perform(get("/goods"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("goodsList", org.hamcrest.Matchers.empty()));

        // 2. 관리자 조회 시 목록에 노출됨
        mockMvc.perform(get("/goods")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("goodsList", org.hamcrest.Matchers.hasSize(1)));

        // 3. 해당 굿즈의 판매자 본인 조회 시 목록에 노출됨
        mockMvc.perform(get("/goods")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("goodsList", org.hamcrest.Matchers.hasSize(1)));

        // 4. 타 판매자 조회 시 목록에서 숨겨짐
        mockMvc.perform(get("/goods")
                        .cookie(new Cookie("accessToken", seller2Token)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("goodsList", org.hamcrest.Matchers.empty()));

        // 5. 판매자 본인의 filter=inactive 탭 조회 시 숨김 굿즈가 정상 노출됨
        mockMvc.perform(get("/goods")
                        .param("filter", "inactive")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("goodsList", org.hamcrest.Matchers.hasSize(1)));

        // 6. 타 판매자의 filter=inactive 탭 조회 시 본인의 숨김 굿즈가 없으므로 빈 목록
        mockMvc.perform(get("/goods")
                        .param("filter", "inactive")
                        .cookie(new Cookie("accessToken", seller2Token)))
                .andExpect(status().isOk())
                .andExpect(model().attribute("goodsList", org.hamcrest.Matchers.empty()));
    }

    @Test
    @DisplayName("판매자 본인은 굿즈를 삭제할 수 있고, 타 판매자는 삭제할 수 없다 (403 에러)")
    void goods_delete_authorization_test() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("삭제 테스트 굿즈", "설명", 0, "https://s3/img.jpg", 5, "CODE-DEL-1"));

        // 타 판매자(seller2)가 삭제 시도 시 403 Forbidden
        mockMvc.perform(post("/seller/goods/" + created.id() + "/delete")
                        .cookie(new Cookie("accessToken", seller2Token)))
                .andExpect(status().isForbidden());

        assertThat(goodsRepository.existsById(created.id())).isTrue();

        // 판매자 본인(seller1)이 삭제 시 성공
        mockMvc.perform(post("/seller/goods/" + created.id() + "/delete")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        assertThat(goodsRepository.existsById(created.id())).isFalse();
    }

    @Test
    @DisplayName("관리자는 타인의 굿즈도 삭제할 수 있다")
    void admin_can_delete_any_goods_test() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("관리자 삭제 굿즈", "설명", 0, "https://s3/img.jpg", 5, "CODE-ADMIN-DEL-1"));

        mockMvc.perform(post("/seller/goods/" + created.id() + "/delete")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        assertThat(goodsRepository.existsById(created.id())).isFalse();
    }

    @Test
    @DisplayName("배송 주문이 연계된 굿즈를 삭제해도 외래키 제약조건 오류 없이 안전하게 연결 해제(unlink)되고 굿즈가 삭제된다")
    void deleteGoods_withExistingDeliveryOrders_unlinksSafelyWithoutError() throws Exception {
        Goods goods = goodsRepository.save(Goods.builder()
                .seller(seller1)
                .name("주문연동 삭제테스트 굿즈")
                .description("설명")
                .price(0)
                .stockQuantity(10)
                .status(GoodsStatus.ACTIVE)
                .build());

        // 해당 굿즈로 배송 주문 1건 생성
        DeliveryOrder order = deliveryOrderRepository.save(
                DeliveryOrder.builder()
                        .user(seller1)
                        .goods(goods)
                        .stageCode("ew-unlink-test")
                        .recipientName("수령인")
                        .phone("010-1234-5678")
                        .zipcode("12345")
                        .address("주소")
                        .build()
        );

        // 판매자가 굿즈 삭제 호출 (외래키 제약조건 위반 없이 302 리다이렉트 성공)
        mockMvc.perform(post("/seller/goods/" + goods.getId() + "/delete")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        // 굿즈는 정상 삭제됨
        assertThat(goodsRepository.existsById(goods.getId())).isFalse();

        // 배송 주문은 보존되며, goods_id는 null로 해제되고 기존 굿즈명은 유지됨
        DeliveryOrder updatedOrder =
                deliveryOrderRepository.findById(order.getId()).orElseThrow();
        assertThat(updatedOrder.getGoods()).isNull();
        assertThat(updatedOrder.isGoodsDeleted()).isTrue();
        assertThat(updatedOrder.getEffectiveGoodsName()).isEqualTo("주문연동 삭제테스트 굿즈");

        // 관리자 및 판매자 배송 목록 조회 시에도 해당 주문이 조회됨
        mockMvc.perform(get("/admin/deliveries")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("deliveries"));

        mockMvc.perform(get("/seller/deliveries")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("deliveries"));
    }

    @Test
    @DisplayName("굿즈 수정 시 사진이 0개면 유효성 검증 실패로 수정이 차단된다 (서비스 예외 및 MVC 에러 반환)")
    void updateGoods_withZeroImages_failsValidation() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("사진검증 굿즈", "설명", 0, "https://s3/test.jpg", 10, "CODE-IMG-FAIL"));

        // 1. 서비스 레이어 직접 호출 검증
        GoodsUpdateRequestDto emptyImageDto = new GoodsUpdateRequestDto(
                "수정 시도", "설명", 0, "", 10, GoodsStatus.ACTIVE, null
        );
        assertThatThrownBy(() -> goodsService.updateGoods(created.id(), seller1.getId(), false, emptyImageDto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("굿즈 사진은 최소 1장 이상 등록되어야 합니다.");

        // 2. MVC 컨트롤러 요청 검증 (빈 문자열 전송 시 seller/goods-edit 뷰 반환 및 errorMessage 표시)
        mockMvc.perform(post("/seller/goods/" + created.id() + "/edit")
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("name", "수정 시도")
                        .param("description", "설명")
                        .param("stockQuantity", "10")
                        .param("status", "ACTIVE")
                        .param("imageUrl", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/goods-edit"))
                .andExpect(model().attribute("errorMessage", "굿즈 사진은 최소 1장 이상 등록되어야 합니다."));

        // DB 상의 원본 이미지는 유지됨
        Goods origin = goodsRepository.findById(created.id()).orElseThrow();
        assertThat(origin.getImageUrl()).isEqualTo("https://s3/test.jpg");
    }

    @Test
    @DisplayName("굿즈 목록 조회(/goods) 시 재고가 소진(품절)된 상품은 리스트의 맨 아래로 정렬된다")
    void listGoods_soldOutItemsSortedToBottom() throws Exception {
        // Given: 판매중 상품과 품절/재고소진 상품 생성
        goodsRepository.save(Goods.builder()
                .seller(seller1)
                .name("판매중 상품 1")
                .description("설명")
                .stockQuantity(10)
                .imageUrl("https://s3/test1.jpg")
                .status(GoodsStatus.ACTIVE)
                .build());

        goodsRepository.save(Goods.builder()
                .seller(seller1)
                .name("재고소진 상품")
                .description("설명")
                .stockQuantity(0)
                .imageUrl("https://s3/test2.jpg")
                .status(GoodsStatus.ACTIVE)
                .build());

        goodsRepository.save(Goods.builder()
                .seller(seller1)
                .name("품절상태 상품")
                .description("설명")
                .stockQuantity(5)
                .imageUrl("https://s3/test3.jpg")
                .status(GoodsStatus.SOLD_OUT)
                .build());

        goodsRepository.save(Goods.builder()
                .seller(seller1)
                .name("판매중 상품 2")
                .description("설명")
                .stockQuantity(3)
                .imageUrl("https://s3/test4.jpg")
                .status(GoodsStatus.ACTIVE)
                .build());

        // When & Then
        mockMvc.perform(get("/goods"))
                .andExpect(status().isOk())
                .andExpect(view().name("goods/list"))
                .andExpect(model().attributeExists("goodsList"))
                .andDo(result -> {
                    @SuppressWarnings("unchecked")
                    List<Goods> list = (List<Goods>) result.getModelAndView().getModel().get("goodsList");
                    int lastActiveIdx = -1;
                    int firstSoldOutIdx = Integer.MAX_VALUE;

                    for (int i = 0; i < list.size(); i++) {
                        Goods g = list.get(i);
                        if (g.getStatus() == GoodsStatus.ACTIVE && g.getStockQuantity() > 0) {
                            lastActiveIdx = Math.max(lastActiveIdx, i);
                        } else if (g.getStatus() == GoodsStatus.SOLD_OUT || g.getStockQuantity() <= 0) {
                            firstSoldOutIdx = Math.min(firstSoldOutIdx, i);
                        }
                    }

                    if (lastActiveIdx != -1 && firstSoldOutIdx != Integer.MAX_VALUE) {
                        assertThat(lastActiveIdx).isLessThan(firstSoldOutIdx);
                    }
                });
    }

    @Test
    @DisplayName("MVC: /goods 목록 화면에 리퀴드 글라스 이미지 상세보기 라이트박스 모달 및 썸네일 인덱스 속성이 정상 렌더링된다")
    void goodsList_rendersImageLightboxAndClickableThumbnails() throws Exception {
        goodsRepository.save(Goods.builder()
                .seller(seller1)
                .name("다중이미지 라이트박스 굿즈")
                .description("설명")
                .stockQuantity(10)
                .imageUrl("https://s3/img1.png,https://s3/img2.png,https://s3/img3.png")
                .status(GoodsStatus.ACTIVE)
                .build());

        String html = mockMvc.perform(get("/goods"))
                .andExpect(status().isOk())
                .andExpect(view().name("goods/list"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("id=\"goodsImageLightbox\"");
        assertThat(html).contains("class=\"lightbox-glass-box\"");
        assertThat(html).contains("data-index=\"0\"");
        assertThat(html).contains("data-index=\"1\"");
        assertThat(html).contains("data-index=\"2\"");
        assertThat(html).contains("data-src=\"https://s3/img3.png\"");
        assertThat(html).contains("3D 전시관");
    }

    @Test
    @DisplayName("MVC: 굿즈 등록 시 다양한 확장자(.glb, .fbx, .obj, .usdz)의 3D 오브제를 함께 등록하고 전시관 페이지에서 조회할 수 있다")
    void createGoods_withMultiFormat3dObjects_persistsAndRendersInExhibition() throws Exception {
        MockMultipartFile glbFile = new MockMultipartFile("objectModelFiles", "lamp.glb", "model/gltf-binary", "glb-data".getBytes());
        MockMultipartFile fbxFile = new MockMultipartFile("objectModelFiles", "desk.fbx", "application/octet-stream", "fbx-data".getBytes());
        MockMultipartFile objFile = new MockMultipartFile("objectModelFiles", "chair.obj", "text/plain", "v 0 0 0".getBytes());
        MockMultipartFile usdzFile = new MockMultipartFile("objectModelFiles", "plant.usdz", "model/vnd.usdz+zip", "usdz-data".getBytes());
        MockMultipartFile thumbFile0 = new MockMultipartFile("objectThumbnailFiles", "lamp_thumb.png", "image/png", "png-data".getBytes());

        mockMvc.perform(multipart("/seller/goods")
                        .file(glbFile)
                        .file(fbxFile)
                        .file(objFile)
                        .file(usdzFile)
                        .file(thumbFile0)
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("name", "스테이지 1 룸테라피 패키지")
                        .param("description", "스테이지 1 클리어 리워드")
                        .param("stockQuantity", "20")
                        .param("imageUrl", "https://s3/stage1.png")
                        .param("authCodesInput", "RT-STAGE101")
                        .param("objectIds", "", "", "", "")
                        .param("objectNames", "아늑한 무드등", "원목 책상", "패브릭 의자", "미니 화분")
                        .param("objectDescriptions", "따뜻한 조명의 무드등", "정리정돈용 원목 책상", "편안한 의자", "책상 위 작은 화분")
                        .param("objectModelUrls", "__NEW_MODEL_0__", "__NEW_MODEL_1__", "__NEW_MODEL_2__", "__NEW_MODEL_3__")
                        .param("objectThumbnailUrls", "__NEW_THUMB_0__", "", "", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        Goods savedGoods = goodsRepository.findAll().get(0);
        var savedObjects = goods3dObjectRepository.findByGoodsIdOrderByDisplayOrderAscIdAsc(savedGoods.getId());
        assertThat(savedObjects).hasSize(4);
        assertThat(savedObjects.get(0).getName()).isEqualTo("아늑한 무드등");
        assertThat(savedObjects.get(0).getModelUrl()).endsWith(".glb");
        assertThat(savedObjects.get(0).getThumbnailUrl()).endsWith(".png");
        assertThat(savedObjects.get(1).getName()).isEqualTo("원목 책상");
        assertThat(savedObjects.get(1).getModelUrl()).endsWith(".fbx");
        assertThat(savedObjects.get(2).getName()).isEqualTo("패브릭 의자");
        assertThat(savedObjects.get(2).getModelUrl()).endsWith(".obj");
        assertThat(savedObjects.get(3).getName()).isEqualTo("미니 화분");
        assertThat(savedObjects.get(3).getModelUrl()).endsWith(".usdz");

        // 전시관 페이지(/goods/{id}/exhibition) 렌더링 확인
        String exhibitionHtml = mockMvc.perform(get("/goods/" + savedGoods.getId() + "/exhibition"))
                .andExpect(status().isOk())
                .andExpect(view().name("goods/exhibition"))
                .andExpect(model().attributeExists("goods", "objects3d"))
                .andReturn().getResponse().getContentAsString();

        assertThat(exhibitionHtml).contains("아늑한 무드등");
        assertThat(exhibitionHtml).contains("원목 책상");
        assertThat(exhibitionHtml).contains("패브릭 의자");
        assertThat(exhibitionHtml).contains("미니 화분");
        assertThat(exhibitionHtml).contains("GLB");
        assertThat(exhibitionHtml).contains("FBX");
        assertThat(exhibitionHtml).contains("OBJ");
        assertThat(exhibitionHtml).contains("USDZ");
    }

    @Test
    @DisplayName("MVC: 굿즈 수정 화면에서 기존 3D 오브제를 수정·삭제·추가할 수 있고, 개별 삭제 엔드포인트도 정상 동작한다")
    void updateGoods_canModifyDeleteAndAdd3dObjects_andDeleteSingleObjectDirectly() throws Exception {
        GoodsResponseDto created = goodsService.registerGoods(
                seller1.getId(),
                new GoodsCreateRequestDto("스테이지 2 리워드", "설명", 0, "https://s3/stage2.png", 15, "RT-STG201"),
                List.of(
                        new Goods3dObjectDto(null, "오브제 A", "설명 A", "/uploads/goods-3d/a.glb", 0),
                        new Goods3dObjectDto(null, "오브제 B", "설명 B", "/uploads/goods-3d/b.fbx", 1)
                )
        );

        var initialList = goods3dObjectRepository.findByGoodsIdOrderByDisplayOrderAscIdAsc(created.id());
        assertThat(initialList).hasSize(2);
        Long objAId = initialList.get(0).getId();

        MockMultipartFile newUsdz = new MockMultipartFile("objectModelFiles", "updated_a.usdz", "model/vnd.usdz+zip", "usdz-content".getBytes());

        // 오브제 A는 이름/설명/파일 수정, 오브제 B는 목록에서 제외(삭제), 오브제 C는 신규 추가
        mockMvc.perform(multipart("/seller/goods/" + created.id() + "/edit")
                        .file(newUsdz)
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("name", "스테이지 2 리워드 수정")
                        .param("description", "설명 수정")
                        .param("stockQuantity", "15")
                        .param("status", "ACTIVE")
                        .param("imageUrl", "https://s3/stage2.png")
                        .param("objects3dSubmitted", "true")
                        .param("objectIds", String.valueOf(objAId), "")
                        .param("objectNames", "오브제 A 수정됨", "오브제 C 신규")
                        .param("objectDescriptions", "설명 A 수정됨", "설명 C 신규")
                        .param("objectModelUrls", "__NEW_MODEL_0__", "/uploads/goods-3d/c.obj"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"));

        var updatedList = goods3dObjectRepository.findByGoodsIdOrderByDisplayOrderAscIdAsc(created.id());
        assertThat(updatedList).hasSize(2);
        // 오브제 A는 기존 ID를 유지한 채 이름/설명/모델 URL이 수정됨
        assertThat(updatedList.get(0).getId()).isEqualTo(objAId);
        assertThat(updatedList.get(0).getName()).isEqualTo("오브제 A 수정됨");
        assertThat(updatedList.get(0).getDescription()).isEqualTo("설명 A 수정됨");
        assertThat(updatedList.get(0).getModelUrl()).endsWith(".usdz");

        // 오브제 B는 삭제되고 오브제 C가 2번째로 등록됨
        assertThat(updatedList.get(1).getName()).isEqualTo("오브제 C 신규");
        assertThat(updatedList.get(1).getModelUrl()).isEqualTo("/uploads/goods-3d/c.obj");
        Long objCId = updatedList.get(1).getId();

        // 타 판매자(seller2)가 개별 3D 오브제 삭제 시도 시 403 Forbidden 차단
        mockMvc.perform(post("/seller/goods/" + created.id() + "/objects/" + objCId + "/delete")
                        .cookie(new Cookie("accessToken", seller2Token)))
                .andExpect(status().isForbidden());

        // 판매자 본인(seller1)이 개별 3D 오브제 삭제 호출 시 정상 삭제
        mockMvc.perform(post("/seller/goods/" + created.id() + "/objects/" + objCId + "/delete")
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("redirect", "/goods/" + created.id() + "/exhibition"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods/" + created.id() + "/exhibition"));

        var finalList = goods3dObjectRepository.findByGoodsIdOrderByDisplayOrderAscIdAsc(created.id());
        assertThat(finalList).hasSize(1);
        assertThat(finalList.get(0).getName()).isEqualTo("오브제 A 수정됨");
    }

    @Test
    @DisplayName("MVC: 굿즈별 인증코드 통합/그룹 조회 시 소속 굿즈명이 매핑되며 인증코드 추가 및 개별 삭제가 정상 동작한다")
    void auth_codes_grouped_view_and_add_delete_test() throws Exception {
        GoodsResponseDto goodsA = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("스테이지1 무드등", "설명A", 0, "/a.png", 10, "RT-A01\nRT-A02"));
        GoodsResponseDto goodsB = goodsService.registerGoods(seller1.getId(),
                new GoodsCreateRequestDto("스테이지2 협탁", "설명B", 0, "/b.png", 10, "RT-B01"));
        goodsService.registerGoods(seller2.getId(),
                new GoodsCreateRequestDto("타판매자 굿즈", "설명C", 0, "/c.png", 10, "RT-OTHER"));

        // 1. 판매자1이 통합 인증코드 관리 페이지(/seller/goods/codes) 조회 시 본인 굿즈(A, B)의 코드 3개와 소속 굿즈명이 조회됨
        mockMvc.perform(get("/seller/goods/codes")
                        .cookie(new Cookie("accessToken", seller1Token)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/goods-codes"))
                .andExpect(model().attributeExists("manageableGoods", "allAuthCodeItems", "codesByGoodsId"))
                .andExpect(result -> {
                    @SuppressWarnings("unchecked")
                    List<GoodsAuthCodeItemDto> items =
                            (List<GoodsAuthCodeItemDto>) result.getModelAndView().getModel().get("allAuthCodeItems");
                    assertThat(items).hasSize(3);
                    assertThat(items).extracting(GoodsAuthCodeItemDto::goodsName)
                            .containsExactlyInAnyOrder("스테이지1 무드등", "스테이지1 무드등", "스테이지2 협탁");
                });

        // 2. 타 판매자(seller2)가 goodsA에 인증코드 추가 시도 시 403 Forbidden 차단
        mockMvc.perform(post("/seller/goods/" + goodsA.id() + "/codes")
                        .cookie(new Cookie("accessToken", seller2Token))
                        .param("authCodesInput", "RT-HACK-01"))
                .andExpect(status().isForbidden());

        // 3. 판매자1이 goodsA에 신규 인증코드 2개 추가 (기존 RT-A01 중복 포함 시 신규 2개만 등록)
        mockMvc.perform(post("/seller/goods/" + goodsA.id() + "/codes")
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("authCodesInput", "RT-A01\nRT-A03\nRT-A04")
                        .param("redirect", "/seller/goods/" + goodsA.id() + "/codes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/goods/" + goodsA.id() + "/codes"));

        var codesAfterAdd = goodsService.getAuthCodeItems(goodsA.id(), seller1.getId(), false);
        assertThat(codesAfterAdd).hasSize(4);
        assertThat(codesAfterAdd).extracting(GoodsAuthCodeItemDto::code)
                .containsExactly("RT-A01", "RT-A02", "RT-A03", "RT-A04");

        Long codeToDeleteId = codesAfterAdd.stream()
                .filter(c -> "RT-A02".equals(c.code()))
                .findFirst()
                .orElseThrow()
                .id();

        // 4. 타 판매자(seller2)가 개별 인증코드 삭제 시도 시 403 Forbidden 차단
        mockMvc.perform(post("/seller/goods/" + goodsA.id() + "/codes/" + codeToDeleteId + "/delete")
                        .cookie(new Cookie("accessToken", seller2Token)))
                .andExpect(status().isForbidden());

        // 5. 판매자1이 RT-A02 개별 삭제 수행 시 정상 삭제
        mockMvc.perform(post("/seller/goods/" + goodsA.id() + "/codes/" + codeToDeleteId + "/delete")
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("redirect", "/seller/goods/codes?view=list"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/goods/codes?view=list"));

        var codesAfterDelete = goodsService.getAuthCodeItems(goodsA.id(), seller1.getId(), false);
        assertThat(codesAfterDelete).hasSize(3);
        assertThat(codesAfterDelete).extracting(GoodsAuthCodeItemDto::code)
                .containsExactly("RT-A01", "RT-A03", "RT-A04");
    }

    @Test
    @DisplayName("MVC: 전시관 3D 오브제 동일 출처(Same-Origin) 스트리밍 엔드포인트가 모델 바이너리와 CORS·버퍼링 방지 헤더를 정상 반환한다")
    void streamObject3dModel_SameOriginEndpoint_StreamsBinaryWithHeaders() throws Exception {
        MockMultipartFile glbFile = new MockMultipartFile(
                "objectModelFiles", "exhibition_lamp.glb", "model/gltf-binary", "glb-binary-payload-123".getBytes());

        mockMvc.perform(multipart("/seller/goods")
                        .file(glbFile)
                        .cookie(new Cookie("accessToken", seller1Token))
                        .param("name", "스트리밍 테스트 굿즈")
                        .param("description", "3D 스트리밍 검증")
                        .param("stockQuantity", "10")
                        .param("imageUrl", "https://s3/lamp.png")
                        .param("authCodesInput", "RT-STREAM01")
                        .param("objects3dSubmitted", "true")
                        .param("objectIds", "")
                        .param("objectNames", "아늑한 램프")
                        .param("objectDescriptions", "램프 설명")
                        .param("objectModelUrls", "__NEW_MODEL_0__"))
                .andExpect(status().is3xxRedirection());

        Goods savedGoods = goodsRepository.findAll().stream()
                .filter(g -> "스트리밍 테스트 굿즈".equals(g.getName()))
                .findFirst()
                .orElseThrow();

        var objects = goods3dObjectRepository.findByGoodsIdOrderByDisplayOrderAscIdAsc(savedGoods.getId());
        assertThat(objects).hasSize(1);
        Long objId = objects.get(0).getId();

        mockMvc.perform(get("/goods/" + savedGoods.getId() + "/objects/" + objId + "/model/object.glb"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "model/gltf-binary"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().string("X-Accel-Buffering", "no"))
                .andExpect(content().bytes("glb-binary-payload-123".getBytes()));
    }
}


