package com.roomtherapy.backend.domain.delivery.controller;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.service.DeliveryOrderService;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryController {

    private final DeliveryOrderService deliveryOrderService;

    /**
     * 관리자용 배송 신청 목록 대시보드 (페이징, 날짜 범위 검색, 수정 필터링)
     */
    @GetMapping("/deliveries")
    public String deliveriesList(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @RequestParam(name = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        org.springframework.data.domain.Page<DeliveryOrderResponseDto> deliveryPage =
                deliveryOrderService.getAdminOrdersWithPaging(startDate, endDate, filter, pageable);

        model.addAttribute("deliveryPage", deliveryPage);
        model.addAttribute("deliveries", deliveryPage.getContent());
        model.addAttribute("allCount", deliveryOrderService.getAllOrdersForAdmin().size());
        model.addAttribute("modifiedCount", deliveryOrderService.getModifiedOrdersForAdmin().size());
        model.addAttribute("currentFilter", filter);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("user", userDetails);
        model.addAttribute("statuses", DeliveryStatus.values());
        return "admin/deliveries";
    }

    /**
     * 관리자용 배송 상태 변경 처리
     */
    @PostMapping("/deliveries/{orderId}/status")
    public String updateStatus(
            @PathVariable Long orderId,
            @RequestParam DeliveryStatus status,
            @RequestParam(name = "redirect", required = false) String redirect) {
        deliveryOrderService.updateOrderStatus(orderId, status);
        return (redirect != null && !redirect.isBlank()) ? "redirect:" + redirect : "redirect:/admin/deliveries";
    }
}
