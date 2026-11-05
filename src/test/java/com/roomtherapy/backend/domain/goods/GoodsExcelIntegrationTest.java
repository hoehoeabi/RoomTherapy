package com.roomtherapy.backend.domain.goods;

import com.roomtherapy.backend.domain.goods.dto.GoodsExcelImportResult;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.repository.GoodsAuthCodeRepository;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.goods.service.GoodsExcelService;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("local")
@SpringBootTest
class GoodsExcelIntegrationTest {

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
    private GoodsExcelService goodsExcelService;

    @Autowired
    private JwtProvider jwtProvider;

    @TestConfiguration
    static class TestConfig {
        @Bean
        public ClientRegistrationRepository clientRegistrationRepository() {
            return Mockito.mock(ClientRegistrationRepository.class);
        }
    }

    private User admin;
    private User seller;
    private String adminToken;
    private String sellerToken;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        goodsAuthCodeRepository.deleteAll();
        goodsRepository.deleteAll();
        userRepository.deleteAll();

        admin = userRepository.save(User.builder()
                .kakaoId("admin_001")
                .nickname("총괄관리자")
                .email("admin@roomtherapy.com")
                .role(Role.ADMIN)
                .build());

        seller = userRepository.save(User.builder()
                .kakaoId("seller_001")
                .nickname("판매자1")
                .email("seller1@roomtherapy.com")
                .role(Role.SELLER)
                .build());

        adminToken = jwtProvider.createAccessToken(admin.getId(), admin.getEmail(), admin.getRole().name());
        sellerToken = jwtProvider.createAccessToken(seller.getId(), seller.getEmail(), seller.getRole().name());
    }

    @Test
    @DisplayName("판매자 및 관리자 굿즈 엑셀 템플릿 다운로드 - 올바른 헤더와 시트명이 포함된 엑셀 파일이 반환된다")
    void downloadExcelTemplate_success() throws Exception {
        // 1. 판매자 엔드포인트
        MvcResult sellerResult = mockMvc.perform(get("/seller/goods/template/excel")
                        .cookie(new Cookie("accessToken", sellerToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] sellerBytes = sellerResult.getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(sellerBytes))) {
            Sheet sheet = wb.getSheet("굿즈_일괄등록_양식");
            assertThat(sheet).isNotNull();
            Row headerRow = sheet.getRow(0);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("굿즈 명칭*");
            assertThat(headerRow.getCell(1).getStringCellValue()).isEqualTo("설명");
            assertThat(headerRow.getCell(2).getStringCellValue()).isEqualTo("가격");
            assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("재고 수량*");
            assertThat(headerRow.getCell(4).getStringCellValue()).contains("사진");
            assertThat(headerRow.getCell(5).getStringCellValue()).contains("인증코드");
            // 헤더 셀 메모(Comment) 말풍선 부착 검증
            assertThat(headerRow.getCell(4).getCellComment()).isNotNull();
            assertThat(headerRow.getCell(4).getCellComment().getString().getString()).contains("다중 사진 등록 안내");
            assertThat(headerRow.getCell(5).getCellComment()).isNotNull();
            assertThat(headerRow.getCell(5).getCellComment().getString().getString()).contains("인증코드 일괄 등록 안내");
        }

        // 2. 관리자 엔드포인트
        MvcResult adminResult = mockMvc.perform(get("/admin/goods/template/excel")
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn();

        byte[] adminBytes = adminResult.getResponse().getContentAsByteArray();
        assertThat(adminBytes).isNotEmpty();
    }

    @Test
    @DisplayName("판매자 엑셀 단독 업로드 - 사진 URL 및 미등록(공백) 굿즈들이 성공적으로 일괄 등록된다")
    void importGoods_fromExcel_success() throws Exception {
        byte[] excelBytes = createSampleGoodsExcel(List.of(
                new String[]{"엑셀등록 무드등", "포근한 조명", "25000", "50", "https://s3/lamp1.png,https://s3/lamp2.png", "RT-LAMP1\nRT-LAMP2"},
                new String[]{"엑셀등록 쿠션", "푹신한 순면 쿠션", "18000", "30", "", ""}
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "goods_bulk.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes
        );

        MvcResult result = mockMvc.perform(multipart("/seller/goods/import/excel")
                        .file(file)
                        .cookie(new Cookie("accessToken", sellerToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"))
                .andExpect(flash().attributeExists("importResult"))
                .andReturn();

        GoodsExcelImportResult importResult = (GoodsExcelImportResult) result.getFlashMap().get("importResult");
        assertThat(importResult).isNotNull();
        assertThat(importResult.totalRows()).isEqualTo(2);
        assertThat(importResult.successCount()).isEqualTo(2);
        assertThat(importResult.failureCount()).isEqualTo(0);

        List<Goods> saved = goodsRepository.findAll();
        assertThat(saved).hasSize(2);

        // 첫 번째 굿즈 검증 (URL 2개 및 인증코드 2개)
        Goods lamp = saved.stream().filter(g -> g.getName().equals("엑셀등록 무드등")).findFirst().orElseThrow();
        assertThat(lamp.getSeller().getId()).isEqualTo(seller.getId());
        assertThat(lamp.getStockQuantity()).isEqualTo(50);
        assertThat(lamp.getPrice()).isEqualTo(25000);
        assertThat(lamp.getImageList()).hasSize(2);
        assertThat(goodsAuthCodeRepository.findByGoodsId(lamp.getId())).hasSize(2);

        // 두 번째 굿즈 검증 (사진 미등록 -> 이미지 0개, 인증코드 0개)
        Goods cushion = saved.stream().filter(g -> g.getName().equals("엑셀등록 쿠션")).findFirst().orElseThrow();
        assertThat(cushion.getSeller().getId()).isEqualTo(seller.getId());
        assertThat(cushion.getImageList()).isEmpty(); // 썸네일 박스 "사진등록 예정"으로 렌더링됨
        assertThat(goodsAuthCodeRepository.findByGoodsId(cushion.getId())).isEmpty();
    }

    @Test
    @DisplayName("판매자 ZIP 압축 파일 업로드 - 동봉된 이미지들이 스토리지에 자동 업로드되고 엑셀 행과 자동 매칭된다")
    void importGoods_fromZip_withImages_success() throws Exception {
        byte[] excelBytes = createSampleGoodsExcel(List.of(
                new String[]{"ZIP 무드등", "압축 일괄등록 테스트", "30000", "10", "mood_a.png, mood_b.png", "RT-ZIP1"},
                new String[]{"외부 URL 굿즈", "외부 링크 테스트", "12000", "5", "https://external.com/photo.jpg", ""}
        ));

        // ZIP 아카이브 빌드: goods.xlsx + mood_a.png + mood_b.png
        ByteArrayOutputStream zipOut = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zipOut)) {
            // 1. 엑셀 파일 추가
            zos.putNextEntry(new ZipEntry("goods.xlsx"));
            zos.write(excelBytes);
            zos.closeEntry();

            // 2. mood_a.png 추가
            zos.putNextEntry(new ZipEntry("images/mood_a.png"));
            zos.write("dummy-png-bytes-a".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            // 3. mood_b.png 추가
            zos.putNextEntry(new ZipEntry("mood_b.png"));
            zos.write("dummy-png-bytes-b".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        MockMultipartFile zipFile = new MockMultipartFile(
                "file", "goods_bulk.zip", "application/zip", zipOut.toByteArray()
        );

        MvcResult result = mockMvc.perform(multipart("/seller/goods/import/excel")
                        .file(zipFile)
                        .cookie(new Cookie("accessToken", sellerToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"))
                .andExpect(flash().attributeExists("importResult"))
                .andReturn();

        GoodsExcelImportResult importResult = (GoodsExcelImportResult) result.getFlashMap().get("importResult");
        assertThat(importResult).isNotNull();
        assertThat(importResult.totalRows()).isEqualTo(2);
        assertThat(importResult.successCount()).isEqualTo(2);
        assertThat(importResult.failureCount()).isEqualTo(0);

        List<Goods> saved = goodsRepository.findAll();
        assertThat(saved).hasSize(2);

        Goods zipGoods = saved.stream().filter(g -> g.getName().equals("ZIP 무드등")).findFirst().orElseThrow();
        assertThat(zipGoods.getImageList()).hasSize(2);
        // LocalStorageService / StorageService에 의해 저장된 URL 검증
        assertThat(zipGoods.getImageList().get(0)).contains("/uploads/goods/");
        assertThat(zipGoods.getImageList().get(1)).contains("/uploads/goods/");

        Goods urlGoods = saved.stream().filter(g -> g.getName().equals("외부 URL 굿즈")).findFirst().orElseThrow();
        assertThat(urlGoods.getImageList()).containsExactly("https://external.com/photo.jpg");
    }

    @Test
    @DisplayName("굿즈 엑셀 일괄 등록 - 유효성 검증 실패(명칭 누락, 음수 재고 등) 행이 상세 에러 목록에 기록된다")
    void importGoods_withValidationErrors_reportsFailures() throws Exception {
        byte[] excelBytes = createSampleGoodsExcel(List.of(
                new String[]{"", "이름 누락 굿즈", "1000", "10", "", ""}, // 1행: 이름 누락 에러
                new String[]{"음수 재고 굿즈", "설명", "1000", "-5", "", ""}, // 2행: 음수 재고 에러
                new String[]{"정상 굿즈", "설명", "15000", "20", "", ""} // 3행: 정상 등록
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "invalid_goods.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes
        );

        MvcResult result = mockMvc.perform(multipart("/seller/goods/import/excel")
                        .file(file)
                        .cookie(new Cookie("accessToken", sellerToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"))
                .andExpect(flash().attributeExists("importResult"))
                .andReturn();

        GoodsExcelImportResult importResult = (GoodsExcelImportResult) result.getFlashMap().get("importResult");
        assertThat(importResult).isNotNull();
        assertThat(importResult.totalRows()).isEqualTo(3);
        assertThat(importResult.successCount()).isEqualTo(1);
        assertThat(importResult.failureCount()).isEqualTo(2);
        assertThat(importResult.hasErrors()).isTrue();
        assertThat(importResult.errorMessages().get(0)).contains("굿즈 명칭은 필수 입력");
        assertThat(importResult.errorMessages().get(1)).contains("재고 수량은 0개 이상");

        // 정상 1건만 등록됨
        List<Goods> saved = goodsRepository.findAll();
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getName()).isEqualTo("정상 굿즈");
    }

    @Test
    @DisplayName("관리자 굿즈 엑셀 일괄 등록 - 관리자 엔드포인트(/admin/goods/import/excel)를 통해 정상 등록된다")
    void adminImport_success() throws Exception {
        byte[] excelBytes = createSampleGoodsExcel(java.util.Collections.singletonList(
                new String[]{"관리자 등록 굿즈", "관리자 일괄등록", "50000", "100", "", "ADMIN-CODE-1"}
        ));

        MockMultipartFile file = new MockMultipartFile(
                "file", "admin_goods.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", excelBytes
        );

        MvcResult result = mockMvc.perform(multipart("/admin/goods/import/excel")
                        .file(file)
                        .cookie(new Cookie("accessToken", adminToken)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/goods"))
                .andExpect(flash().attributeExists("importResult"))
                .andReturn();

        GoodsExcelImportResult importResult = (GoodsExcelImportResult) result.getFlashMap().get("importResult");
        assertThat(importResult).isNotNull();
        assertThat(importResult.successCount()).isEqualTo(1);

        Goods saved = goodsRepository.findAll().get(0);
        assertThat(saved.getName()).isEqualTo("관리자 등록 굿즈");
        assertThat(saved.getSeller().getId()).isEqualTo(admin.getId());
    }

    private byte[] createSampleGoodsExcel(List<String[]> rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("굿즈_일괄등록_양식");

            // 0행: 헤더
            Row headerRow = sheet.createRow(0);
            String[] headers = {"굿즈 명칭*", "설명", "가격", "재고 수량*", "사진 (URL 또는 ZIP 내 파일명)", "인증코드 목록"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }

            // 1행: 힌트 행
            Row hintRow = sheet.createRow(1);
            hintRow.createCell(0).setCellValue("필수 입력");
            hintRow.createCell(1).setCellValue("설명");
            hintRow.createCell(2).setCellValue("0");
            hintRow.createCell(3).setCellValue("0");

            // 2행부터 데이터
            for (int r = 0; r < rows.size(); r++) {
                Row dataRow = sheet.createRow(r + 2);
                String[] rowData = rows.get(r);
                for (int c = 0; c < rowData.length; c++) {
                    dataRow.createCell(c).setCellValue(rowData[c]);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
