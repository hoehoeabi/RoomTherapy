package com.roomtherapy.backend.domain.goods;

import com.jayway.jsonpath.JsonPath;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
import com.roomtherapy.backend.domain.goods.dto.UnityCodeIssueResponseDto;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.goods.service.GoodsService;
import com.roomtherapy.backend.domain.goods.validator.RedisGoodsAuthCodeValidator;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class GoodsAuthCodeIntegrationTest {

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

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User seller;
    private User user1;
    private User user2;
    private String sellerToken;
    private String user1Token;
    private String user2Token;

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

        seller = userRepository.save(User.builder()
                .kakaoId("seller_001")
                .nickname("굿즈판매자")
                .email("seller@test.com")
                .role(Role.SELLER)
                .build());

        user1 = userRepository.save(User.builder()
                .kakaoId("user_001")
                .nickname("유저1")
                .email("user1@test.com")
                .role(Role.USER)
                .build());

        user2 = userRepository.save(User.builder()
                .kakaoId("user_002")
                .nickname("유저2")
                .email("user2@test.com")
                .role(Role.USER)
                .build());

        sellerToken = jwtProvider.createAccessToken(seller.getId(), seller.getEmail(), seller.getRole().name());
        user1Token = jwtProvider.createAccessToken(user1.getId(), user1.getEmail(), user1.getRole().name());
        user2Token = jwtProvider.createAccessToken(user2.getId(), user2.getEmail(), user2.getRole().name());
    }

    @Test
    @DisplayName("판매자가 굿즈와 인증코드 묶음(a, b, c)을 함께 등록 성공")
    void registerGoods_withAuthCodes_success() throws Exception {
        mockMvc.perform(post("/seller/goods")
                        .cookie(new Cookie("accessToken", sellerToken))
                        .param("name", "강아지 인형")
                        .param("description", "귀여운 강아지 인형")
                        .param("price", "0")
                        .param("stockQuantity", "10")
                        .param("authCodesInput", "code_dog_a\ncode_dog_b,code_dog_c"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"))
                .andExpect(flash().attributeExists("successMessage"));

        List<Goods> goodsList = goodsRepository.findAll();
        assertThat(goodsList).hasSize(1);
        Goods dogGoods = goodsList.get(0);
        assertThat(dogGoods.getName()).isEqualTo("강아지 인형");
        assertThat(dogGoods.getStockQuantity()).isEqualTo(10);

        // 인증코드 3건 DB 저장 확인
        assertThat(goodsAuthCodeRepository.countByGoodsId(dogGoods.getId())).isEqualTo(3);
    }

    @Test
    @DisplayName("유저가 유니티에서 받은 인증코드(b)로 강아지 굿즈 배송 신청 성공")
    void claimDelivery_withValidCode_success() throws Exception {
        // 1. 강아지 굿즈(코드 a, b, c) 등록
        GoodsResponseDto dogGoods = goodsService.registerGoods(seller.getId(), new GoodsCreateRequestDto(
                "강아지 인형", "귀여운 강아지", 0, "", 5, "code_dog_a,code_dog_b,code_dog_c", null
        ));

        // 2. 유저1이 코드 b로 강아지 굿즈 신청
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", user1Token))
                        .param("goodsId", String.valueOf(dogGoods.id()))
                        .param("stageCode", "code_dog_b")
                        .param("recipientName", "홍길동")
                        .param("phone", "010-1234-5678")
                        .param("zipcode", "12345")
                        .param("address", "서울특별시")
                        .param("detailAddress", "101호"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/delivery/confirm/*"));

        // 주문 생성 및 재고 차감 확인
        assertThat(deliveryOrderRepository.count()).isEqualTo(1);
        Goods updated = goodsRepository.findById(dogGoods.id()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(4);
    }

    @Test
    @DisplayName("고양이 굿즈용 코드(d)로 강아지 굿즈를 신청하려는 경우 적격성 검증 실패로 거절")
    void claimDelivery_withWrongGoodsCode_rejected() throws Exception {
        // 강아지 굿즈 (코드 a, b, c)
        GoodsResponseDto dogGoods = goodsService.registerGoods(seller.getId(), new GoodsCreateRequestDto(
                "강아지 인형", "설명", 0, "", 5, "code_dog_a,code_dog_b,code_dog_c", null
        ));
        // 고양이 굿즈 (코드 d, e, f)
        goodsService.registerGoods(seller.getId(), new GoodsCreateRequestDto(
                "고양이 인형", "설명", 0, "", 5, "code_cat_d,code_cat_e,code_cat_f", null
        ));

        // 유저1이 강아지 굿즈에 고양이 코드(code_cat_d)를 입력하고 신청 시도
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", user1Token))
                        .param("goodsId", String.valueOf(dogGoods.id()))
                        .param("stageCode", "code_cat_d")
                        .param("recipientName", "홍길동")
                        .param("phone", "010-1234-5678")
                        .param("zipcode", "12345")
                        .param("address", "서울특별시"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery"))
                .andExpect(model().attributeExists("errorMessage"));

        assertThat(deliveryOrderRepository.count()).isEqualTo(0);
    }

    @Test
    @DisplayName("동일 유저가 동일 굿즈를 다른 인증코드(a)로 중복 신청 시 1인 1회 제한으로 차단")
    void claimDelivery_duplicateClaimBySameUser_rejected() throws Exception {
        GoodsResponseDto dogGoods = goodsService.registerGoods(seller.getId(), new GoodsCreateRequestDto(
                "강아지 인형", "설명", 0, "", 5, "code_dog_a,code_dog_b", null
        ));

        // 1차 신청 (코드 b로 성공)
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", user1Token))
                        .param("goodsId", String.valueOf(dogGoods.id()))
                        .param("stageCode", "code_dog_b")
                        .param("recipientName", "홍길동")
                        .param("phone", "010-1234-5678")
                        .param("zipcode", "12345")
                        .param("address", "서울특별시"))
                .andExpect(status().is3xxRedirection());

        // 2차 신청 시도 (동일 유저가 코드 a로 재신청 시도 -> 1인 1회 제한으로 차단)
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", user1Token))
                        .param("goodsId", String.valueOf(dogGoods.id()))
                        .param("stageCode", "code_dog_a")
                        .param("recipientName", "홍길동")
                        .param("phone", "010-1234-5678")
                        .param("zipcode", "12345")
                        .param("address", "서울특별시"))
                .andExpect(status().isOk())
                .andExpect(view().name("delivery"))
                .andExpect(model().attributeExists("errorMessage"));

        assertThat(deliveryOrderRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("여러 유저가 동일한 인증코드(b)를 받아도 각자 1번씩 정상 신청 가능")
    void claimDelivery_multipleUsersWithSameCode_successForEach() throws Exception {
        GoodsResponseDto dogGoods = goodsService.registerGoods(seller.getId(), new GoodsCreateRequestDto(
                "강아지 인형", "설명", 0, "", 5, "code_dog_b", null
        ));

        // 유저1 신청 (코드 b)
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", user1Token))
                        .param("goodsId", String.valueOf(dogGoods.id()))
                        .param("stageCode", "code_dog_b")
                        .param("recipientName", "유저일")
                        .param("phone", "010-1111-1111")
                        .param("zipcode", "12345")
                        .param("address", "서울특별시"))
                .andExpect(status().is3xxRedirection());

        // 유저2 신청 (동일한 코드 b이지만 다른 유저이므로 정상 성공)
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", user2Token))
                        .param("goodsId", String.valueOf(dogGoods.id()))
                        .param("stageCode", "code_dog_b")
                        .param("recipientName", "유저이")
                        .param("phone", "010-2222-2222")
                        .param("zipcode", "54321")
                        .param("address", "부산광역시"))
                .andExpect(status().is3xxRedirection());

        assertThat(deliveryOrderRepository.count()).isEqualTo(2);
        Goods updated = goodsRepository.findById(dogGoods.id()).orElseThrow();
        assertThat(updated.getStockQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("유니티 API 1: 굿즈 목록 조회(/api/unity/goods) JSON 반환 검증")
    void unityApi_getGoodsList_success() throws Exception {
        goodsService.registerGoods(seller.getId(), new GoodsCreateRequestDto(
                "유니티 연동 굿즈", "설명", 0, "http://image.png", 20, "code1", null
        ));

        mockMvc.perform(get("/api/unity/goods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("유니티 연동 굿즈"))
                .andExpect(jsonPath("$[0].stockQuantity").value(20));
    }

    @Test
    @DisplayName("유니티 API 2 & Redis: 굿즈 PK로 동적 코드 발급(/issue-code) 후 배송 신청 성공 및 Redis 소진 검증")
    void unityApi_issueDynamicCode_andClaimDelivery_consumesRedis() throws Exception {
        // 굿즈 등록
        GoodsResponseDto goods = goodsService.registerGoods(seller.getId(), new GoodsCreateRequestDto(
                "레디스 동적 굿즈", "설명", 0, "", 10, null, null
        ));

        // 1. 유니티에서 동적 인증코드 발급 요청
        String responseJson = mockMvc.perform(post("/api/unity/goods/" + goods.id() + "/issue-code")
                        .param("durationMinutes", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").exists())
                .andReturn().getResponse().getContentAsString();

        String dynamicCode = JsonPath.read(responseJson, "$.code");

        // Redis에 저장되었는지 확인
        String redisKey = RedisGoodsAuthCodeValidator.REDIS_GOODS_CODE_PREFIX + dynamicCode.toUpperCase();
        assertThat(redisTemplate.hasKey(redisKey)).isTrue();

        // 2. 유저1이 발급받은 동적 코드로 배송 신청 제출
        mockMvc.perform(post("/delivery")
                        .cookie(new Cookie("accessToken", user1Token))
                        .param("goodsId", String.valueOf(goods.id()))
                        .param("stageCode", dynamicCode)
                        .param("recipientName", "홍길동")
                        .param("phone", "010-9999-8888")
                        .param("zipcode", "12345")
                        .param("address", "서울특별시"))
                .andExpect(status().is3xxRedirection());

        // 3. 배송 신청 성공 후 Redis에서 해당 1회용 코드가 즉시 소진(삭제)되었는지 검증
        assertThat(redisTemplate.hasKey(redisKey)).isFalse();
        assertThat(deliveryOrderRepository.count()).isEqualTo(1);
    }
}
