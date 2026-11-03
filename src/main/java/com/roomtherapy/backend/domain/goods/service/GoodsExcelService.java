package com.roomtherapy.backend.domain.goods.service;

import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsExcelImportResult;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsExcelService {

    private final GoodsService goodsService;
    private final StorageService storageService;
    private final UserRepository userRepository;

    /**
     * 굿즈 일괄 등록용 표준 엑셀 템플릿(.xlsx) 생성
     */
    public byte[] generateTemplateWorkbook() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("굿즈_일괄등록_양식");

            CellStyle headerStyle = createHeaderStyle(workbook, new byte[]{(byte) 255, (byte) 75, (byte) 78}); // Signature Red #FF4B4E
            CellStyle hintStyle = createHintStyle(workbook);
            CellStyle sampleStyle = createDataStyle(workbook, HorizontalAlignment.CENTER);
            CellStyle sampleLeftStyle = createDataStyle(workbook, HorizontalAlignment.LEFT);

            String[] headers = {
                    "굿즈 명칭*", "설명", "가격", "재고 수량*",
                    "사진 (URL 또는 ZIP 내 파일명) [다중 가능]", "인증코드 목록 [쉼표(,) 다중 가능]"
            };

            String[] hints = {
                    "필수 입력 (예: 룸테라피 아늑한 무드등)",
                    "굿즈 상세 설명 (선택)",
                    "0 이상의 숫자 (기본값: 0)",
                    "필수 입력 / 0 이상의 정수 (0 입력 시 품절)",
                    "💡 쉼표(,)로 구분하여 여러 장 등록 가능 (첫 번째가 대표 썸네일 / ZIP 업로드 시 동봉 파일명 입력 / 빈칸 시 '사진등록 예정')",
                    "💡 쉼표(,) 또는 줄바꿈(Alt+Enter)으로 여러 개 일괄 등록 가능 (예: RT-A1, RT-B2, RT-C3)"
            };

            String[][] sampleData = {
                    {
                            "룸테라피 감성 무드등",
                            "아늑하고 따뜻한 빛을 내는 침실용 무드등입니다.",
                            "25000",
                            "50",
                            "mood_01.png, mood_02.png",
                            "RT-MOOD01, RT-MOOD02, RT-MOOD03"
                    },
                    {
                            "룸테라피 소프트 쿠션",
                            "포근한 휴식을 선사하는 순면 쿠션",
                            "18000",
                            "30",
                            "",
                            ""
                    }
            };

            // 1행: 헤더
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 헤더 셀에 상세 가이드 메모(Comment 말풍선 툴팁) 부착
            Drawing<?> drawing = sheet.createDrawingPatriarch();
            CreationHelper factory = workbook.getCreationHelper();

            addCellComment(factory, drawing, headerRow.getCell(4),
                    "💡 [다중 사진 등록 안내]",
                    "• 쉼표(,)로 구분하여 여러 사진을 한 번에 등록할 수 있습니다.\n" +
                    "• 첫 번째 사진이 대표 썸네일로 지정됩니다.\n" +
                    "• ZIP 파일 업로드 시 동봉된 이미지 파일명을 적어주세요. (예: mood1.png, mood2.png)\n" +
                    "• 외부 웹 URL도 지원합니다. (예: https://.../img1.png, https://.../img2.png)\n" +
                    "• 비워둘 경우 자동으로 '사진등록 예정'으로 등록됩니다.");

            addCellComment(factory, drawing, headerRow.getCell(5),
                    "💡 [인증코드 일괄 등록 안내]",
                    "• 쉼표(,) 또는 줄바꿈(Alt+Enter)으로 여러 개의 인증코드를 한 셀에 입력할 수 있습니다.\n" +
                    "• 예시: RT-A1B2C3, RT-D4E5F6, RT-G7H8J9\n" +
                    "• 입력된 개수만큼 해당 굿즈에 클리어 인증코드가 즉시 연동 발급됩니다.\n" +
                    "• 비워둘 경우 인증코드 없이 등록됩니다.");

            // 2행: 안내 힌트
            Row hintRow = sheet.createRow(1);
            hintRow.setHeightInPoints(20);
            for (int i = 0; i < hints.length; i++) {
                Cell cell = hintRow.createCell(i);
                cell.setCellValue(hints[i]);
                cell.setCellStyle(hintStyle);
            }

            // 3~4행: 예시 데이터
            for (int r = 0; r < sampleData.length; r++) {
                Row sampleRow = sheet.createRow(r + 2);
                sampleRow.setHeightInPoints(22);
                for (int c = 0; c < sampleData[r].length; c++) {
                    Cell cell = sampleRow.createCell(c);
                    cell.setCellValue(sampleData[r][c]);
                    if (c == 0 || c == 1 || c == 4 || c == 5) {
                        cell.setCellStyle(sampleLeftStyle);
                    } else {
                        cell.setCellStyle(sampleStyle);
                    }
                }
            }

            // 틀 고정 (헤더 & 힌트 고정)
            sheet.createFreezePane(0, 2);

            // 열 너비 자동 맞춤
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1500, 18 * 256));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("굿즈 엑셀 템플릿 생성 실패", e);
            throw new RuntimeException("굿즈 엑셀 템플릿 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 굿즈 일괄 등록 메인 진입점 (단일 .xlsx 엑셀 또는 .zip 압축 파일 자동 판별)
     */
    @Transactional
    public GoodsExcelImportResult importGoods(MultipartFile file, Long sellerId, boolean isAdmin) {
        if (file == null || file.isEmpty()) {
            return GoodsExcelImportResult.empty("업로드된 파일이 비어 있습니다.");
        }

        if (sellerId != null && !userRepository.existsById(sellerId)) {
            return GoodsExcelImportResult.empty("유효하지 않은 판매자 계정입니다.");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        if (originalFilename.endsWith(".zip")) {
            return importFromZip(file, sellerId);
        } else if (originalFilename.endsWith(".xlsx") || originalFilename.endsWith(".xls")) {
            try (InputStream is = file.getInputStream()) {
                return importFromExcel(is, sellerId, Collections.emptyMap());
            } catch (Exception e) {
                log.error("엑셀 파일 읽기 실패", e);
                return GoodsExcelImportResult.empty("엑셀 파일 파싱 중 오류가 발생했습니다: " + e.getMessage());
            }
        } else {
            return GoodsExcelImportResult.empty("지원하지 않는 파일 형식입니다. .xlsx 또는 .zip 파일만 업로드 가능합니다.");
        }
    }

    private static final int MAX_ZIP_ENTRIES = 200;
    private static final long MAX_ZIP_ENTRY_BYTES = 20L * 1024 * 1024; // 개별 파일 최대 20MB
    private static final long MAX_ZIP_TOTAL_BYTES = 100L * 1024 * 1024; // 전체 압축 해제 최대 100MB

    /**
     * ZIP 압축 파일 처리: .xlsx 파일 추출 및 동봉된 이미지 일괄 스토리지 업로드 후 매핑
     */
    private GoodsExcelImportResult importFromZip(MultipartFile zipFile, Long sellerId) {
        byte[] excelBytes = null;
        Map<String, byte[]> imageFiles = new LinkedHashMap<>();
        int entryCount = 0;
        long totalUncompressedBytes = 0L;

        try (ZipInputStream zis = new ZipInputStream(zipFile.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    log.warn("ZIP 압축 파일 내 엔트리 개수 초과: {}", entryCount);
                    return GoodsExcelImportResult.empty("ZIP 압축 파일 내 파일 개수가 허용 한도(" + MAX_ZIP_ENTRIES + "개)를 초과했습니다.");
                }

                String entryName = entry.getName();
                String simpleName = new File(entryName).getName();

                // 맥 OS 특수 압축 파일 및 숨김 파일 제외
                if (entryName.contains("__MACOSX") || entryName.contains(".DS_Store") || simpleName.startsWith(".")) {
                    continue;
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                long entryUncompressedBytes = 0L;
                while ((len = zis.read(buffer)) != -1) {
                    entryUncompressedBytes += len;
                    totalUncompressedBytes += len;
                    if (entryUncompressedBytes > MAX_ZIP_ENTRY_BYTES || totalUncompressedBytes > MAX_ZIP_TOTAL_BYTES) {
                        log.warn("ZIP 압축 해제 용량 한도 초과 (Zip Bomb 방어) - Entry: {}, EntryBytes: {}, TotalBytes: {}",
                                simpleName, entryUncompressedBytes, totalUncompressedBytes);
                        return GoodsExcelImportResult.empty("ZIP 압축 해제 용량이 허용 한도(최대 100MB)를 초과했습니다.");
                    }
                    baos.write(buffer, 0, len);
                }
                byte[] entryBytes = baos.toByteArray();

                String lowerName = simpleName.toLowerCase();
                if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                    excelBytes = entryBytes;
                } else if (isImageExtension(lowerName)) {
                    imageFiles.put(simpleName, entryBytes);
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            log.error("ZIP 파일 압축 해제 중 오류 발생", e);
            return GoodsExcelImportResult.empty("ZIP 압축 해제 중 오류가 발생했습니다: " + e.getMessage());
        }

        if (excelBytes == null) {
            return GoodsExcelImportResult.empty("ZIP 압축 파일 내에 .xlsx 엑셀 파일이 포함되어 있지 않습니다.");
        }

        // 압축 파일 내 이미지들을 StorageService에 일괄 업로드하여 파일명 -> S3 URL 매핑 생성
        Map<String, String> uploadedUrlMap = new HashMap<>();
        if (!imageFiles.isEmpty()) {
            List<MultipartFile> multipartFiles = imageFiles.entrySet().stream()
                    .map(e -> (MultipartFile) new ByteArrayMultipartFile(e.getKey(), e.getKey(), guessContentType(e.getKey()), e.getValue()))
                    .toList();
            try {
                List<String> uploadedUrls = storageService.uploadFiles(multipartFiles, "goods");
                for (int i = 0; i < multipartFiles.size(); i++) {
                    String originalName = multipartFiles.get(i).getOriginalFilename();
                    if (originalName != null) {
                        uploadedUrlMap.put(originalName.toLowerCase(), uploadedUrls.get(i));
                    }
                }
                log.info("ZIP 파일 내 이미지 {}장 스토리지 일괄 업로드 성공", uploadedUrls.size());
            } catch (Exception e) {
                log.error("ZIP 파일 내 이미지 업로드 중 오류", e);
                return GoodsExcelImportResult.empty("이미지 파일 스토리지 업로드 중 오류가 발생했습니다: " + e.getMessage());
            }
        }

        // 추출된 엑셀 데이터를 이미지 매핑 맵과 함께 처리
        return importFromExcel(new ByteArrayInputStream(excelBytes), sellerId, uploadedUrlMap);
    }

    /**
     * 엑셀 스트림 파싱 및 굿즈/인증코드/스톡 동기화 일괄 등록
     */
    private GoodsExcelImportResult importFromExcel(InputStream inputStream, Long sellerId, Map<String, String> uploadedUrlMap) {
        int totalRows = 0;
        int successCount = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getPhysicalNumberOfRows() <= 1) {
                return GoodsExcelImportResult.empty("엑셀 파일에 등록할 데이터 행이 없습니다.");
            }

            // 0행 헤더, 1행 힌트(있을 경우) 자동 건너뛰기
            int startRow = 1;
            Row checkRow = sheet.getRow(1);
            if (checkRow != null) {
                String firstCell = getCellStringValue(checkRow.getCell(0));
                if (firstCell.contains("필수 입력") || firstCell.contains("예:") || firstCell.contains("설명")) {
                    startRow = 2;
                }
            }

            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                int displayRow = r + 1;

                try {
                    // 0: 굿즈 명칭*
                    String name = getCellStringValue(row.getCell(0));
                    if (name.isBlank()) {
                        errors.add(String.format("[%d행] 굿즈 명칭은 필수 입력 항목입니다.", displayRow));
                        continue;
                    }

                    // 1: 설명
                    String description = getCellStringValue(row.getCell(1));

                    // 2: 가격
                    int price = 0;
                    String priceStr = getCellStringValue(row.getCell(2));
                    if (!priceStr.isBlank()) {
                        try {
                            price = Integer.parseInt(priceStr.replace(",", ""));
                            if (price < 0) {
                                errors.add(String.format("[%d행] 가격은 0원 이상이어야 합니다.", displayRow));
                                continue;
                            }
                        } catch (NumberFormatException e) {
                            errors.add(String.format("[%d행] 올바른 가격 숫자를 입력해주세요. (입력값: %s)", displayRow, priceStr));
                            continue;
                        }
                    }

                    // 3: 재고 수량*
                    int stockQuantity;
                    String stockStr = getCellStringValue(row.getCell(3));
                    if (stockStr.isBlank()) {
                        errors.add(String.format("[%d행] 재고 수량은 필수 입력 항목입니다.", displayRow));
                        continue;
                    }
                    try {
                        stockQuantity = Integer.parseInt(stockStr.replace(",", ""));
                        if (stockQuantity < 0) {
                            errors.add(String.format("[%d행] 재고 수량은 0개 이상이어야 합니다.", displayRow));
                            continue;
                        }
                    } catch (NumberFormatException e) {
                        errors.add(String.format("[%d행] 올바른 재고 숫자를 입력해주세요. (입력값: %s)", displayRow, stockStr));
                        continue;
                    }

                    // 4: 사진 (URL 또는 ZIP 내 파일명)
                    String photoCellVal = getCellStringValue(row.getCell(4));
                    String resolvedImageUrl = resolvePhotoUrls(photoCellVal, uploadedUrlMap);

                    // 5: 인증코드 목록
                    String authCodes = getCellStringValue(row.getCell(5));

                    // 굿즈 등록 호출
                    GoodsCreateRequestDto requestDto = new GoodsCreateRequestDto(
                            name,
                            description,
                            price,
                            resolvedImageUrl,
                            stockQuantity,
                            authCodes
                    );

                    goodsService.registerGoods(sellerId, requestDto);
                    successCount++;
                } catch (Exception ex) {
                    log.error("[{}행] 굿즈 일괄 등록 실패", displayRow, ex);
                    errors.add(String.format("[%d행] 처리 중 오류: %s", displayRow, ex.getMessage()));
                }
            }

        } catch (Exception e) {
            log.error("엑셀 시트 파싱 실패", e);
            return GoodsExcelImportResult.empty("엑셀 파싱 중 오류가 발생했습니다: " + e.getMessage());
        }

        if (totalRows == 0) {
            return GoodsExcelImportResult.empty("엑셀 파일에 유효한 데이터 행이 존재하지 않습니다.");
        }

        return new GoodsExcelImportResult(totalRows, successCount, errors.size(), errors);
    }

    /**
     * 사진 컬럼 문자열을 실제 저장될 URL 문자열로 변환 (외부 URL 그대로 유지, ZIP 파일명은 업로드된 URL로 치환)
     */
    private String resolvePhotoUrls(String rawValue, Map<String, String> uploadedUrlMap) {
        if (rawValue == null || rawValue.isBlank()) {
            return "";
        }

        String[] tokens = rawValue.split("[,\\n]+");
        List<String> resolved = new ArrayList<>();

        for (String t : tokens) {
            String token = t.trim();
            if (token.isEmpty()) continue;

            // 이미 웹 URL이거나 절대경로인 경우
            if (token.startsWith("http://") || token.startsWith("https://") || token.startsWith("/")) {
                resolved.add(token);
            } else {
                // ZIP 파일에서 업로드된 파일명 매칭
                String lower = token.toLowerCase();
                if (uploadedUrlMap.containsKey(lower)) {
                    resolved.add(uploadedUrlMap.get(lower));
                } else {
                    // ZIP 파일 내에 없는 경우 파일명 그대로 남겨두지 않고 경고 로그
                    log.warn("ZIP 파일 내에서 사진 파일 '{}'을 찾을 수 없습니다.", token);
                }
            }
        }

        return String.join(",", resolved);
    }

    private boolean isImageExtension(String filename) {
        return filename.endsWith(".png") || filename.endsWith(".jpg")
                || filename.endsWith(".jpeg") || filename.endsWith(".webp")
                || filename.endsWith(".gif");
    }

    private String guessContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif")) return "image/gif";
        return "application/octet-stream";
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb, byte[] rgbColor) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("맑은 고딕");
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);

        style.setFillForegroundColor(new XSSFColor(rgbColor, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private CellStyle createHintStyle(XSSFWorkbook wb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints((short) 9);
        font.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
        font.setItalic(true);
        style.setFont(font);

        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 248, (byte) 250, (byte) 252}, null));
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.LEFT);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private CellStyle createDataStyle(XSSFWorkbook wb, HorizontalAlignment align) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints((short) 10);
        style.setFont(font);
        style.setAlignment(align);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        setBorders(style);
        return style;
    }

    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private boolean isRowEmpty(Row row) {
        for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
            Cell cell = row.getCell(c);
            if (cell != null && cell.getCellType() != CellType.BLANK && !getCellStringValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double num = cell.getNumericCellValue();
                if (num == Math.floor(num)) {
                    yield String.valueOf((long) num);
                } else {
                    yield String.valueOf(num);
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try {
                    yield cell.getStringCellValue().trim();
                } catch (Exception e) {
                    yield String.valueOf(cell.getNumericCellValue());
                }
            }
            default -> "";
        };
    }

    private void addCellComment(CreationHelper factory, Drawing<?> drawing, Cell cell, String title, String body) {
        ClientAnchor anchor = factory.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(cell.getColumnIndex() + 4);
        anchor.setRow1(cell.getRowIndex());
        anchor.setRow2(cell.getRowIndex() + 5);

        Comment comment = drawing.createCellComment(anchor);
        comment.setString(factory.createRichTextString(title + "\n" + body));
        cell.setCellComment(comment);
    }

    /**
     * 바이트 배열로부터 MultipartFile을 흉내내는 인메모리 래퍼 클래스
     */
    private static class ByteArrayMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public ByteArrayMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content != null ? content : new byte[0];
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(File dest) throws java.io.IOException {
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                fos.write(content);
            }
        }
    }
}
