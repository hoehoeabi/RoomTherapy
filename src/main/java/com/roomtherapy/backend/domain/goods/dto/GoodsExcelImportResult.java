package com.roomtherapy.backend.domain.goods.dto;

import java.util.List;

public record GoodsExcelImportResult(
        int totalRows,
        int successCount,
        int failureCount,
        List<String> errorMessages
) {
    public boolean hasErrors() {
        return failureCount > 0;
    }

    public static GoodsExcelImportResult empty(String message) {
        return new GoodsExcelImportResult(0, 0, 0, List.of(message));
    }
}
