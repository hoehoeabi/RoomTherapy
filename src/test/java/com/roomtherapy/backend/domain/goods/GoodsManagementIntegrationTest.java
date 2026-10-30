package com.roomtherapy.backend.domain.goods;

import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsUpdateRequestDto;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import org.springframework.mock.web.MockMultipartFile;

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
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private GoodsService goodsService;

    @Autowired
    private JwtProvider jwtProvider;

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.oauth2.client.registration.ClientRegistrationRepository clientRegistrationRepository() {
            return org.mockito.Mockito.mock(org.springframework.security.oauth2.client.registration.ClientRegistrationRepository.class);
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
}
