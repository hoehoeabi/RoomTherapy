package com.roomtherapy.backend.domain.delivery.dto;

import java.util.List;

public record DeliveryExcelImportResult(
        int totalRows,
        int successCount,
        int failureCount,
        List<String> errorMessages
) {
    public boolean hasErrors() {
        return failureCount > 0;
    }

    public static DeliveryExcelImportResult empty() {
        return new DeliveryExcelImportResult(0, 0, 0, List.of("업로드된 엑셀 파일에 유효한 데이터 행이 없습니다."));
    }
}
