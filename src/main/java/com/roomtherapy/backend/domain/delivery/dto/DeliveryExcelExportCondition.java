package com.roomtherapy.backend.domain.delivery.dto;

import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public record DeliveryExcelExportCondition(
        Long goodsId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        LocalDate endDate,
        List<DayOfWeek> daysOfWeek,
        DeliveryStatus status,
        String filter, // "all", "modified", "feedback", "bundle"
        String sortBy, // "latest"(신청일시순), "bundle"(합배송 묶음순)
        List<String> columns // 선택한 컬럼 키 목록
) {
}
