package com.roomtherapy.backend.domain.delivery;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryExcelImportResult;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.delivery.service.DeliveryExcelService;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.user.entity.Role;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.jwt.JwtProvider;
import jakarta.servlet.http.Cookie;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class DeliveryExcelIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GoodsRepository goodsRepository;

    @Autowired
    private DeliveryOrderRepository deliveryOrderRepository;

    @Autowired
    private DeliveryExcelService deliveryExcelService;

    @Autowired
    private JwtProvider jwtProvider;

    private User adminUser;
    private User sellerUser;
    private User otherSellerUser;

    private String adminToken;
    private String sellerToken;

    private Goods sellerGoods;
    private Goods otherGoods;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        deliveryOrderRepository.deleteAll();
        goodsRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .kakaoId("admin_kakao")
                .nickname("총괄관리자")
                .email("admin@roomtherapy.com")
                .role(Role.ADMIN)
                .build());

        sellerUser = userRepository.save(User.builder()
                .kakaoId("seller_kakao")
                .nickname("판매자1")
                .email("seller1@roomtherapy.com")
                .role(Role.SELLER)
                .build());

        otherSellerUser = userRepository.save(User.builder()
                .kakaoId("other_seller_kakao")
                .nickname("판매자2")
                .email("seller2@roomtherapy.com")
                .role(Role.SELLER)
                .build());

        adminToken = jwtProvider.createAccessToken(adminUser.getId(), adminUser.getEmail(), adminUser.getRole().name());
        sellerToken = jwtProvider.createAccessToken(sellerUser.getId(), sellerUser.getEmail(), sellerUser.getRole().name());

        sellerGoods = goodsRepository.save(Goods.builder()
                .name("모던 무드등")
                .description("아늑한 무드등")
                .stockQuantity(100)
                .seller(sellerUser)
                .status(GoodsStatus.ACTIVE)
                .imageUrl("/img/mood.png")
                .build());

        otherGoods = goodsRepository.save(Goods.builder()
                .name("디퓨저 세트")
                .description("향기로운 디퓨저")
                .stockQuantity(50)
                .seller(otherSellerUser)
                .status(GoodsStatus.ACTIVE)
                .imageUrl("/img/diffuser.png")
                .build());
    }

    @Test
    @DisplayName("엑셀 양식(템플릿) 다운로드 시 표준 헤더를 포함한 엑셀 파일이 정상 반환된다")
    void downloadExcelTemplate_success() throws Exception {
        MvcResult result = mockMvc.perform(get("/admin/deliveries/template/excel")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertThat(bytes).isNotEmpty();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet("배송주문_일괄등록_양식");
            assertThat(sheet).isNotNull();
            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("상품명(또는 상품ID)*");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("수령인*");
            assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("연락처*");
            assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("우편번호*");
            assertThat(headerRow.getCell(4).getStringCellValue()).isEqualTo("기본주소*");
        }
    }

    @Test
    @DisplayName("관리자 배송 목록 엑셀 다운로드 - 컬럼 선택 및 필터링 동작 검증")
    void exportDeliveries_withColumnSelectionAndFilters() throws Exception {
        // 배송 주문 생성
        DeliveryOrder order1 = deliveryOrderRepository.save(DeliveryOrder.builder()
                .goods(sellerGoods)
                .user(sellerUser)
                .stageCode("AUTH-CODE-1")
                .recipientName("수령인A")
                .phone("010-1111-2222")
                .zipcode("11111")
                .address("서울시 강남구")
                .detailAddress("101호")
                .status(DeliveryStatus.ORDERED)
                .build());

        DeliveryOrder order2 = deliveryOrderRepository.save(DeliveryOrder.builder()
                .goods(otherGoods)
                .user(otherSellerUser)
                .stageCode("AUTH-CODE-2")
                .recipientName("수령인B")
                .phone("010-3333-4444")
                .zipcode("22222")
                .address("서울시 서초구")
                .detailAddress("202호")
                .status(DeliveryStatus.SHIPPED)
                .build());

        // 1. 전체 다운로드
        MvcResult resultAll = mockMvc.perform(get("/admin/deliveries/export/excel")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(resultAll.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheet("배송신청목록");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3); // 1 header + 2 data rows
        }

        // 2. 굿즈 필터 (sellerGoods만) + 커스텀 컬럼 (goodsName, recipientName, status)
        MvcResult resultFiltered = mockMvc.perform(get("/admin/deliveries/export/excel")
                        .cookie(new Cookie("accessToken", adminToken))
                        .param("goodsId", sellerGoods.getId().toString())
                        .param("columns", "goodsName", "recipientName", "status"))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(resultFiltered.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheet("배송신청목록");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2); // 1 header + 1 data row
            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getPhysicalNumberOfCells()).isEqualTo(3);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("신청 상품");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("수령인");
            assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("배송 상태");

            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("모던 무드등");
            assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("수령인A");
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("접수 완료");
        }

        // 3. 요일 필터 검증 (오늘 요일 매칭 시 포함, 다른 요일 필터링 시 제외)
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        DayOfWeek otherDay = today.equals(DayOfWeek.SUNDAY) ? DayOfWeek.MONDAY : DayOfWeek.of(today.getValue() + 1);

        MvcResult resultToday = mockMvc.perform(get("/admin/deliveries/export/excel")
                        .cookie(new Cookie("accessToken", adminToken))
                        .param("daysOfWeek", today.name()))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(resultToday.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheet("배송신청목록");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(3); // 오늘 생성된 주문 2건 모두 포함
        }

        MvcResult resultOtherDay = mockMvc.perform(get("/admin/deliveries/export/excel")
                        .cookie(new Cookie("accessToken", adminToken))
                        .param("daysOfWeek", otherDay.name()))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(resultOtherDay.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheet("배송신청목록");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(1); // 헤더만 존재, 데이터 0건
        }
    }

    @Test
    @DisplayName("판매자 배송 목록 엑셀 다운로드 - 본인 굿즈 데이터만 격리되어 다운로드된다")
    void sellerExport_isolatedToSellerGoodsOnly() throws Exception {
        deliveryOrderRepository.save(DeliveryOrder.builder()
                .goods(sellerGoods)
                .user(sellerUser)
                .stageCode("AUTH-SELLER")
                .recipientName("내 구매자")
                .phone("010-1111-2222")
                .zipcode("11111")
                .address("서울시 송파구")
                .status(DeliveryStatus.ORDERED)
                .build());

        deliveryOrderRepository.save(DeliveryOrder.builder()
                .goods(otherGoods)
                .user(otherSellerUser)
                .stageCode("AUTH-OTHER")
                .recipientName("타인 구매자")
                .phone("010-9999-8888")
                .zipcode("22222")
                .address("부산시 해운대구")
                .status(DeliveryStatus.ORDERED)
                .build());

        MvcResult result = mockMvc.perform(get("/seller/deliveries/export/excel")
                        .cookie(new Cookie("accessToken", sellerToken)))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheet("배송신청목록");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2); // 1 header + 1 seller's order only
            Row dataRow = sheet.getRow(1);
            boolean foundSellerGoods = false;
            for (int i = 0; i < sheet.getRow(0).getLastCellNum(); i++) {
                if ("신청 상품".equals(sheet.getRow(0).getCell(i).getStringCellValue())) {
                    assertThat(dataRow.getCell(i).getStringCellValue()).isEqualTo("모던 무드등");
                    foundSellerGoods = true;
                }
            }
            assertThat(foundSellerGoods).isTrue();
        }
    }

    @Test
    @DisplayName("관리자 배송 목록 엑셀 다운로드 - 합배송 묶음순(?sortBy=bundle) 정렬 시 최초 신청일 오름차순으로 신청자별 묶여서 다운로드된다")
    void exportDeliveries_withBundleSorting() throws Exception {
        // Given: 유저1(홍길동), 유저2(임꺽정)
        User userA = sellerUser; // 홍길동 역할
        User userB = otherSellerUser; // 임꺽정 역할

        DeliveryOrder orderA1 = deliveryOrderRepository.save(DeliveryOrder.builder()
                .goods(sellerGoods)
                .user(userA)
                .stageCode("A-CODE-1")
                .recipientName("홍길동")
                .phone("010-1111-1111")
                .zipcode("11111")
                .address("서울시 강남구")
                .status(DeliveryStatus.ORDERED)
                .build());
        setCreatedAt(orderA1, java.time.LocalDateTime.now().minusDays(7));

        DeliveryOrder orderA2 = deliveryOrderRepository.save(DeliveryOrder.builder()
                .goods(sellerGoods)
                .user(userA)
                .stageCode("A-CODE-2")
                .recipientName("홍길동")
                .phone("010-1111-1111")
                .zipcode("11111")
                .address("서울시 강남구")
                .status(DeliveryStatus.PREPARING)
                .build());
        setCreatedAt(orderA2, java.time.LocalDateTime.now().minusDays(1));

        DeliveryOrder orderB1 = deliveryOrderRepository.save(DeliveryOrder.builder()
                .goods(otherGoods)
                .user(userB)
                .stageCode("B-CODE-1")
                .recipientName("임꺽정")
                .phone("010-2222-2222")
                .zipcode("22222")
                .address("서울시 서초구")
                .status(DeliveryStatus.ORDERED)
                .build());
        setCreatedAt(orderB1, java.time.LocalDateTime.now().minusDays(5));

        DeliveryOrder orderB2 = deliveryOrderRepository.save(DeliveryOrder.builder()
                .goods(otherGoods)
                .user(userB)
                .stageCode("B-CODE-2")
                .recipientName("임꺽정")
                .phone("010-2222-2222")
                .zipcode("22222")
                .address("서울시 서초구")
                .status(DeliveryStatus.ORDERED)
                .build());
        setCreatedAt(orderB2, java.time.LocalDateTime.now().minusDays(3));

        // When: ?filter=bundle & ?sortBy=bundle
        MvcResult result = mockMvc.perform(get("/admin/deliveries/export/excel")
                        .cookie(new Cookie("accessToken", adminToken))
                        .param("filter", "bundle")
                        .param("sortBy", "bundle")
                        .param("columns", "id", "recipientName"))
                .andExpect(status().isOk())
                .andReturn();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
            Sheet sheet = wb.getSheet("배송신청목록");
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(5); // 1 header + 4 data rows

            // 행1: orderA1 (#orderA1.id, 홍길동)
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("#" + orderA1.getId());
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("홍길동");

            // 행2: orderA2 (#orderA2.id, 홍길동)
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("#" + orderA2.getId());
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("홍길동");

            // 행3: orderB1 (#orderB1.id, 임꺽정)
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("#" + orderB1.getId());
            assertThat(sheet.getRow(3).getCell(1).getStringCellValue()).isEqualTo("임꺽정");

            // 행4: orderB2 (#orderB2.id, 임꺽정)
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue()).isEqualTo("#" + orderB2.getId());
            assertThat(sheet.getRow(4).getCell(1).getStringCellValue()).isEqualTo("임꺽정");
        }
    }

    private void setCreatedAt(DeliveryOrder order, java.time.LocalDateTime time) {
        try {
            java.lang.reflect.Field field = com.roomtherapy.backend.global.common.entity.BaseTimeEntity.class.getDeclaredField("createdAt");
            field.setAccessible(true);
            field.set(order, time);
            deliveryOrderRepository.save(order);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("관리자 엑셀 일괄 등록 - 업로드된 엑셀 파일의 주문 정보가 DB에 저장된다")
    void adminImport_success() throws Exception {
        byte[] excelBytes = createSampleImportExcel(List.of(
                new String[]{"모던 무드등", "주문자1", "010-1234-5678", "12345", "서울시 강남구", "101호", "", "문 앞 배송", "PREPARING"},
                new String[]{sellerGoods.getId().toString(), "주문자2", "010-9876-5432", "54321", "서울시 서초구", "202호", "MANUAL-CODE-99", "경비실", "ORDERED"}
        ));

        MockMultipartFile file = new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        mockMvc.perform(multipart("/admin/deliveries/import/excel")
                        .file(file)
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/deliveries"))
                .andExpect(flash().attributeExists("importResult"));

        List<DeliveryOrder> savedOrders = deliveryOrderRepository.findAll();
        assertThat(savedOrders).hasSize(2);
        assertThat(savedOrders).anyMatch(o -> o.getRecipientName().equals("주문자1") && o.getStatus() == DeliveryStatus.PREPARING);
        assertThat(savedOrders).anyMatch(o -> o.getRecipientName().equals("주문자2") && "MANUAL-CODE-99".equals(o.getStageCode()));
    }

    @Test
    @DisplayName("판매자 엑셀 일괄 등록 - 타 판매자 굿즈 등록 시도 시 실패 처리된다")
    void sellerImport_otherSellerGoodsFails() throws Exception {
        byte[] excelBytes = createSampleImportExcel(List.of(
                new String[]{"모던 무드등", "내고객", "010-1111-2222", "11111", "서울시 마포구", "101호", "", "", "ORDERED"},
                new String[]{"디퓨저 세트", "타사고객", "010-3333-4444", "22222", "부산시 해운대구", "202호", "", "", "ORDERED"}
        ));

        MockMultipartFile file = new MockMultipartFile("file", "orders.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes);

        MvcResult result = mockMvc.perform(multipart("/seller/deliveries/import/excel")
                        .file(file)
                        .cookie(new Cookie("accessToken", sellerToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/deliveries"))
                .andReturn();

        DeliveryExcelImportResult importResult = (DeliveryExcelImportResult) result.getFlashMap().get("importResult");
        assertThat(importResult).isNotNull();
        assertThat(importResult.totalRows()).isEqualTo(2);
        assertThat(importResult.successCount()).isEqualTo(1);
        assertThat(importResult.failureCount()).isEqualTo(1);
        assertThat(importResult.errorMessages().get(0)).contains("본인이 등록한 상품의 주문만 등록할 수 있습니다");

        List<DeliveryOrder> savedOrders = deliveryOrderRepository.findAll();
        assertThat(savedOrders).hasSize(1);
        assertThat(savedOrders.get(0).getRecipientName()).isEqualTo("내고객");
    }

    private byte[] createSampleImportExcel(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("배송주문_일괄등록_양식");

            // Header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "상품명(또는 상품ID)*", "수령인*", "연락처*", "우편번호*",
                    "기본주소*", "상세주소", "인증코드(선택)", "게임 피드백(선택)", "배송상태(선택)"
            };
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // Data rows
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                String[] rowData = rows.get(r);
                for (int c = 0; c < rowData.length; c++) {
                    row.createCell(c).setCellValue(rowData[c]);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
