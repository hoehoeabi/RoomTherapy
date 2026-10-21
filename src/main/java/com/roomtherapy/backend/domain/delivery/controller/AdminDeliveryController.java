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
     * 관리자용 배송 신청 목록 대시보드
     */
    @GetMapping("/deliveries")
    public String deliveriesList(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        List<DeliveryOrderResponseDto> deliveries = deliveryOrderService.getAllOrdersForAdmin();
        model.addAttribute("deliveries", deliveries);
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
