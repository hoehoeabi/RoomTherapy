package com.roomtherapy.backend.domain.delivery.controller;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderRequestDto;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.service.DeliveryOrderService;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryOrderService deliveryOrderService;

    /**
     * 굿즈 배송 신청 폼 화면
     */
    @GetMapping("/delivery")
    public String deliveryForm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new DeliveryOrderRequestDto("", "", "", "", "", "", null));
        }
        model.addAttribute("user", userDetails);
        return "delivery";
    }

    /**
     * 굿즈 배송 신청 제출 처리
     */
    @PostMapping("/delivery")
    public String submitDelivery(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("form") DeliveryOrderRequestDto form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", userDetails);
            return "delivery";
        }

        try {
            DeliveryOrderResponseDto response = deliveryOrderService.createDeliveryOrder(userDetails.getUserId(), form);
            return "redirect:/delivery/confirm/" + response.id();
        } catch (Exception e) {
            log.warn("배송 신청 실패: {}", e.getMessage());
            model.addAttribute("user", userDetails);
            model.addAttribute("errorMessage", e.getMessage());
            return "delivery";
        }
    }

    /**
     * 굿즈 배송 신청 완료 및 확인 페이지
     */
    @GetMapping("/delivery/confirm/{orderId}")
    public String confirmPage(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        DeliveryOrderResponseDto order = deliveryOrderService.getOrder(orderId);
        model.addAttribute("order", order);
        model.addAttribute("user", userDetails);
        return "delivery-confirm";
    }
}
