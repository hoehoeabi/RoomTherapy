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
public class AdminDeliveryController {

    private final DeliveryOrderService deliveryOrderService;

    /**
     * 관리자용 배송 신청 목록 대시보드 (전체 또는 수정된 신청 목록 필터링)
     */
    @GetMapping("/deliveries")
    public String deliveriesList(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        List<DeliveryOrderResponseDto> allDeliveries = deliveryOrderService.getAllOrdersForAdmin();
        List<DeliveryOrderResponseDto> modifiedDeliveries = deliveryOrderService.getModifiedOrdersForAdmin();

        List<DeliveryOrderResponseDto> displayDeliveries = "modified".equalsIgnoreCase(filter) 
                ? modifiedDeliveries 
                : allDeliveries;

        model.addAttribute("deliveries", displayDeliveries);
        model.addAttribute("allCount", allDeliveries.size());
        model.addAttribute("modifiedCount", modifiedDeliveries.size());
        model.addAttribute("currentFilter", filter);
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
            @RequestParam DeliveryStatus status) {
        deliveryOrderService.updateOrderStatus(orderId, status);
        return "redirect:/admin/deliveries";
    }
}
