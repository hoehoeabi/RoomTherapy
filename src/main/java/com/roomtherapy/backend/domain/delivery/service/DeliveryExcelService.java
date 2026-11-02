package com.roomtherapy.backend.domain.delivery.service;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryExcelImportResult;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.repository.DeliveryOrderRepository;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryExcelService {

    private final DeliveryOrderRepository deliveryOrderRepository;
    private final GoodsRepository goodsRepository;
    private final UserRepository userRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // 컬럼 정의 (키, 기본 헤더명)
    public static final Map<String, String> COLUMN_MAP = new LinkedHashMap<>();
    static {
        COLUMN_MAP.put("id", "주문 ID");
        COLUMN_MAP.put("createdAt", "신청일시");
        COLUMN_MAP.put("updatedAt", "수정일시");
        COLUMN_MAP.put("userNickname", "신청 계정");
        COLUMN_MAP.put("goodsName", "신청 상품");
        COLUMN_MAP.put("stageCode", "인증코드");
        COLUMN_MAP.put("recipientName", "수령인");
        COLUMN_MAP.put("phone", "연락처");
        COLUMN_MAP.put("zipcode", "우편번호");
        COLUMN_MAP.put("address", "배송지 주소");
        COLUMN_MAP.put("status", "배송 상태");
        COLUMN_MAP.put("feedback", "요청/피드백");
    }

    /**
     * 배송 주문 목록을 지정된 컬럼에 맞추어 엑셀(.xlsx) 바이트 배열로 생성
     */
    public byte[] exportDeliveriesToExcel(List<DeliveryOrder> orders, List<String> selectedColumns) {
        List<String> columns = (selectedColumns == null || selectedColumns.isEmpty())
                ? new ArrayList<>(COLUMN_MAP.keySet())
                : selectedColumns.stream().filter(COLUMN_MAP::containsKey).toList();

        if (columns.isEmpty()) {
            columns = new ArrayList<>(COLUMN_MAP.keySet());
        }

        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("배송신청목록");

            // 스타일 설정
            CellStyle headerStyle = createHeaderStyle(workbook, new byte[]{(byte) 79, (byte) 70, (byte) 229}); // Indigo #4F46E5
            CellStyle centerStyle = createDataStyle(workbook, HorizontalAlignment.CENTER);
            CellStyle leftStyle = createDataStyle(workbook, HorizontalAlignment.LEFT);

            // 헤더 행 생성
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < columns.size(); i++) {
                String colKey = columns.get(i);
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(COLUMN_MAP.get(colKey));
                cell.setCellStyle(headerStyle);
            }

            // 데이터 행 생성
            int rowIdx = 1;
            for (DeliveryOrder order : orders) {
                Row row = sheet.createRow(rowIdx++);
                row.setHeightInPoints(22);

                for (int colIdx = 0; colIdx < columns.size(); colIdx++) {
                    String colKey = columns.get(colIdx);
                    Cell cell = row.createCell(colIdx);
                    String val = extractCellValue(order, colKey);
                    cell.setCellValue(val != null ? val : "");

                    if ("address".equals(colKey) || "feedback".equals(colKey)) {
                        cell.setCellStyle(leftStyle);
                    } else {
                        cell.setCellStyle(centerStyle);
                    }
                }
            }

            // 컬럼 너비 자동 조정 (최소 12글자, 최대 50글자 폭)
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
                int currentWidth = sheet.getColumnWidth(i);
                int minWidth = 12 * 256;
                int maxWidth = 50 * 256;
                if (currentWidth < minWidth) {
                    sheet.setColumnWidth(i, minWidth);
                } else if (currentWidth > maxWidth) {
                    sheet.setColumnWidth(i, maxWidth);
                }
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("엑셀 파일 생성 중 오류 발생", e);
            throw new RuntimeException("엑셀 파일 생성 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 엑셀 일괄 등록용 표준 템플릿 파일 생성
     */
    public byte[] generateTemplateWorkbook() {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("배송주문_일괄등록_양식");

            CellStyle headerStyle = createHeaderStyle(workbook, new byte[]{(byte) 30, (byte) 41, (byte) 59}); // Slate #1E293B
            CellStyle hintStyle = createHintStyle(workbook);
            CellStyle sampleStyle = createDataStyle(workbook, HorizontalAlignment.CENTER);
            CellStyle sampleLeftStyle = createDataStyle(workbook, HorizontalAlignment.LEFT);

            String[] headers = {
                    "상품명(또는 상품ID)*", "수령인*", "연락처*", "우편번호*", "기본주소*",
                    "상세주소", "인증코드(선택)", "요청사항(선택)", "배송상태(선택)"
            };

            String[] hints = {
                    "등록된 굿즈명 또는 굿즈 번호 입력", "받는 사람 이름", "휴대폰 번호 (하이픈 포함 가능)", "5자리 우편번호", "도로명 또는 지번 주소",
                    "동/호수 등 상세 주소", "미입력 시 EXCEL 자동 발급", "배송 기사님 요청사항", "접수 완료 / 배송 준비중 / 발송 완료"
            };

            String[] sampleData = {
                    "룸테라피 아늑한 무드등", "홍길동", "010-1234-5678", "13529", "경기 성남시 분당구 판교역로 166",
                    "카카오 판교오피스 5층", "RT-EXCEL01", "부재 시 경비실에 맡겨주세요", "접수 완료"
            };

            // 1행: 헤더
            Row headerRow = sheet.createRow(0);
            headerRow.setHeightInPoints(26);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 2행: 입력 안내 힌트
            Row hintRow = sheet.createRow(1);
            hintRow.setHeightInPoints(20);
            for (int i = 0; i < hints.length; i++) {
                Cell cell = hintRow.createCell(i);
                cell.setCellValue(hints[i]);
                cell.setCellStyle(hintStyle);
            }

            // 3행: 예시 데이터
            Row sampleRow = sheet.createRow(2);
            sampleRow.setHeightInPoints(22);
            for (int i = 0; i < sampleData.length; i++) {
                Cell cell = sampleRow.createCell(i);
                cell.setCellValue(sampleData[i]);
                if (i == 4 || i == 5 || i == 7) {
                    cell.setCellStyle(sampleLeftStyle);
                } else {
                    cell.setCellStyle(sampleStyle);
                }
            }

            // 틀 고정
            sheet.createFreezePane(0, 1);

            // 열 너비 설정
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.max(sheet.getColumnWidth(i) + 1200, 16 * 256));
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            log.error("엑셀 템플릿 생성 실패", e);
            throw new RuntimeException("엑셀 템플릿 생성 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 엑셀 파일로부터 배송 주문 목록 일괄 파싱 및 DB 저장 (Bulk Import)
     */
    @Transactional
    public DeliveryExcelImportResult importDeliveriesFromExcel(MultipartFile file, Long uploaderUserId, boolean isAdmin) {
        if (file == null || file.isEmpty()) {
            return new DeliveryExcelImportResult(0, 0, 0, List.of("업로드된 파일이 비어 있습니다."));
        }

        User uploader = userRepository.findById(uploaderUserId)
                .orElseThrow(() -> new IllegalArgumentException("업로더 사용자 정보를 찾을 수 없습니다."));

        int totalRows = 0;
        int successCount = 0;
        List<String> errors = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = sheet.getPhysicalNumberOfRows();
            if (rowCount <= 1) {
                return DeliveryExcelImportResult.empty();
            }

            // 0행 헤더, 1행 힌트(있을 경우)를 건너뛰기 위해 시작 행 탐색
            int startRow = 1;
            Row checkRow = sheet.getRow(1);
            if (checkRow != null) {
                String firstCellVal = getCellStringValue(checkRow.getCell(0));
                if (firstCellVal.contains("입력") || firstCellVal.contains("굿즈 번호") || firstCellVal.contains("예시")) {
                    startRow = 2; // 힌트 행 건너뜀
                }
            }

            for (int r = startRow; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null || isRowEmpty(row)) {
                    continue;
                }

                totalRows++;
                int displayRowNum = r + 1; // 엑셀 사용자용 1-indexed

                try {
                    String goodsVal = getCellStringValue(row.getCell(0));
                    String recipientName = getCellStringValue(row.getCell(1));
                    String phone = getCellStringValue(row.getCell(2));
                    String zipcode = getCellStringValue(row.getCell(3));
                    String address = getCellStringValue(row.getCell(4));
                    String detailAddress = getCellStringValue(row.getCell(5));
                    String stageCode = getCellStringValue(row.getCell(6));
                    String feedback = getCellStringValue(row.getCell(7));
                    String statusStr = getCellStringValue(row.getCell(8));

                    // 필수값 검증
                    if (recipientName.isBlank()) {
                        errors.add(String.format("[%d행] 수령인 이름이 누락되었습니다.", displayRowNum));
                        continue;
                    }
                    if (zipcode.isBlank()) {
                        errors.add(String.format("[%d행] 우편번호가 누락되었습니다.", displayRowNum));
                        continue;
                    }
                    if (address.isBlank()) {
                        errors.add(String.format("[%d행] 기본 배송지 주소가 누락되었습니다.", displayRowNum));
                        continue;
                    }

                    // 굿즈 매칭
                    Goods goods = findGoodsByValue(goodsVal);
                    if (goods == null && !goodsVal.isBlank()) {
                        errors.add(String.format("[%d행] 일치하는 상품을 찾을 수 없습니다: '%s'", displayRowNum, goodsVal));
                        continue;
                    }

                    // 판매자 권한 검증: 관리자가 아니면 본인이 등록한 굿즈만 등록 가능
                    if (!isAdmin && goods != null) {
                        if (goods.getSeller() == null || !goods.getSeller().getId().equals(uploaderUserId)) {
                            errors.add(String.format("[%d행] 본인이 등록한 상품의 주문만 등록할 수 있습니다: '%s'", displayRowNum, goods.getName()));
                            continue;
                        }
                    }

                    // 인증코드 미기재 시 배치 고유 코드 발급
                    if (stageCode.isBlank()) {
                        stageCode = "EXCEL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    }

                    // 배송 상태 파싱
                    DeliveryStatus status = parseDeliveryStatus(statusStr);

                    // 배송 주문 생성 및 저장
                    DeliveryOrder deliveryOrder = DeliveryOrder.builder()
                            .user(uploader)
                            .goods(goods)
                            .stageCode(stageCode)
                            .recipientName(recipientName)
                            .phone(phone)
                            .zipcode(zipcode)
                            .address(address)
                            .detailAddress(detailAddress)
                            .feedback(feedback)
                            .status(status)
                            .build();

                    deliveryOrderRepository.save(deliveryOrder);
                    successCount++;
                } catch (Exception e) {
                    log.error("엑셀 {}행 파싱 실패", displayRowNum, e);
                    errors.add(String.format("[%d행] 데이터 처리 중 오류: %s", displayRowNum, e.getMessage()));
                }
            }

            return new DeliveryExcelImportResult(totalRows, successCount, errors.size(), errors);
        } catch (Exception e) {
            log.error("엑셀 파일 업로드 처리 실패", e);
            return new DeliveryExcelImportResult(0, 0, 1, List.of("엑셀 파일을 읽는 중 오류가 발생했습니다: " + e.getMessage()));
        }
    }

    private Goods findGoodsByValue(String val) {
        if (val == null || val.isBlank()) {
            return null;
        }
        val = val.trim();
        // 1. 숫자인 경우 ID로 검색
        try {
            Long id = Long.parseLong(val);
            Optional<Goods> goodsOpt = goodsRepository.findById(id);
            if (goodsOpt.isPresent()) return goodsOpt.get();
        } catch (NumberFormatException ignored) {}

        // 2. 이름 완전 일치 검색
        Optional<Goods> byName = goodsRepository.findFirstByName(val);
        if (byName.isPresent()) return byName.get();

        // 3. 이름 부분 일치 검색 (전체 목록 순회)
        for (Goods g : goodsRepository.findAll()) {
            if (g.getName() != null && g.getName().trim().equalsIgnoreCase(val)) {
                return g;
            }
        }
        for (Goods g : goodsRepository.findAll()) {
            if (g.getName() != null && g.getName().contains(val)) {
                return g;
            }
        }
        return null;
    }

    private DeliveryStatus parseDeliveryStatus(String str) {
        if (str == null || str.isBlank()) {
            return DeliveryStatus.ORDERED;
        }
        str = str.trim();
        for (DeliveryStatus s : DeliveryStatus.values()) {
            if (s.name().equalsIgnoreCase(str) || s.getDescription().equalsIgnoreCase(str)) {
                return s;
            }
        }
        if (str.contains("준비")) return DeliveryStatus.PREPARING;
        if (str.contains("발송")) return DeliveryStatus.SHIPPED;
        if (str.contains("완료")) return DeliveryStatus.DELIVERED;
        if (str.contains("취소")) return DeliveryStatus.CANCELLED;
        return DeliveryStatus.ORDERED;
    }

    private String extractCellValue(DeliveryOrder order, String colKey) {
        return switch (colKey) {
            case "id" -> "#" + order.getId();
            case "createdAt" -> order.getCreatedAt() != null ? order.getCreatedAt().format(DATE_FORMATTER) : "";
            case "updatedAt" -> order.getUpdatedAt() != null ? order.getUpdatedAt().format(DATE_FORMATTER) : "";
            case "userNickname" -> order.getUser() != null ? order.getUser().getNickname() : "";
            case "goodsName" -> order.getGoods() != null ? order.getGoods().getName() : "굿즈 미지정";
            case "stageCode" -> order.getStageCode() != null ? order.getStageCode() : "";
            case "recipientName" -> order.getRecipientName();
            case "phone" -> order.getPhone() != null ? order.getPhone() : "";
            case "zipcode" -> order.getZipcode();
            case "address" -> (order.getAddress() != null ? order.getAddress() : "")
                    + (order.getDetailAddress() != null && !order.getDetailAddress().isBlank() ? " " + order.getDetailAddress() : "");
            case "status" -> order.getStatus() != null ? order.getStatus().getDescription() : "";
            case "feedback" -> order.getFeedback() != null ? order.getFeedback() : "";
            default -> "";
        };
    }

    private CellStyle createHeaderStyle(XSSFWorkbook wb, byte[] rgb) {
        XSSFCellStyle style = wb.createCellStyle();
        XSSFFont font = wb.createFont();
        font.setFontName("맑은 고딕");
        font.setFontHeightInPoints((short) 11);
        font.setBold(true);
        font.setColor(new XSSFColor(new byte[]{(byte) 255, (byte) 255, (byte) 255}, null));
        style.setFont(font);

        style.setFillForegroundColor(new XSSFColor(rgb, null));
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
        font.setColor(new XSSFColor(new byte[]{(byte) 100, (byte) 116, (byte) 139}, null)); // Slate 500
        style.setFont(font);

        style.setFillForegroundColor(new XSSFColor(new byte[]{(byte) 241, (byte) 245, (byte) 249}, null)); // Slate 100
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
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
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getLocalDateTimeCellValue().format(DATE_FORMATTER);
                } else {
                    double num = cell.getNumericCellValue();
                    if (num == Math.floor(num)) {
                        yield String.valueOf((long) num);
                    } else {
                        yield String.valueOf(num);
                    }
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
}
