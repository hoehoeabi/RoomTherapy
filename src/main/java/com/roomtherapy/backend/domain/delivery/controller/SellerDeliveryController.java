package com.roomtherapy.backend.domain.delivery.controller;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.service.DeliveryOrderService;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@RequestMapping("/seller")
@RequiredArgsConstructor
public class SellerDeliveryController {

    private final DeliveryOrderService deliveryOrderService;

    /**
     * 판매자용 본인 굿즈 배송 신청 목록 대시보드
     */
    @GetMapping("/deliveries")
    public String sellerDeliveries(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        Long sellerId = userDetails.getUserId();
        List<DeliveryOrderResponseDto> allDeliveries = deliveryOrderService.getOrdersForSeller(sellerId);
        List<DeliveryOrderResponseDto> modifiedDeliveries = deliveryOrderService.getModifiedOrdersForSeller(sellerId);

        List<DeliveryOrderResponseDto> displayDeliveries = "modified".equalsIgnoreCase(filter)
                ? modifiedDeliveries
                : allDeliveries;

        model.addAttribute("deliveries", displayDeliveries);
        model.addAttribute("allCount", allDeliveries.size());
        model.addAttribute("modifiedCount", modifiedDeliveries.size());
        model.addAttribute("currentFilter", filter);
        model.addAttribute("user", userDetails);
        model.addAttribute("statuses", DeliveryStatus.values());
        return "seller/deliveries";
    }

    /**
     * 판매자용 배송 상태 변경 처리
     */
    @PostMapping("/deliveries/{orderId}/status")
    public String updateStatus(
            @PathVariable Long orderId,
            @RequestParam DeliveryStatus status,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        deliveryOrderService.updateOrderStatusBySeller(userDetails.getUserId(), orderId, status);
        return "redirect:/seller/deliveries";
    }
}
