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
    private final com.roomtherapy.backend.domain.goods.repository.GoodsRepository goodsRepository;

    /**
     * 굿즈 배송 신청 폼 화면
     */
    @GetMapping("/delivery")
    public String deliveryForm(
            @org.springframework.web.bind.annotation.RequestParam(name = "goodsId", required = false) Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new DeliveryOrderRequestDto("", "", "", "", "", "", goodsId));
        }

        var goodsList = goodsRepository.findAll();
        model.addAttribute("goodsList", goodsList);

        if (goodsId != null) {
            goodsRepository.findById(goodsId).ifPresent(g -> model.addAttribute("selectedGoods", g));
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
            model.addAttribute("goodsList", goodsRepository.findAll());
            if (form.goodsId() != null) {
                goodsRepository.findById(form.goodsId()).ifPresent(g -> model.addAttribute("selectedGoods", g));
            }
            return "delivery";
        }

        try {
            DeliveryOrderResponseDto response = deliveryOrderService.createDeliveryOrder(userDetails.getUserId(), form);
            return "redirect:/delivery/confirm/" + response.id();
        } catch (Exception e) {
            log.warn("배송 신청 실패: {}", e.getMessage());
            model.addAttribute("user", userDetails);
            model.addAttribute("goodsList", goodsRepository.findAll());
            if (form.goodsId() != null) {
                goodsRepository.findById(form.goodsId()).ifPresent(g -> model.addAttribute("selectedGoods", g));
            }
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

    /**
     * 내가 신청한 배송 목록 조회 페이지
     */
    @GetMapping("/delivery/my")
    public String myDeliveries(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        var myOrders = deliveryOrderService.getMyOrders(userDetails.getUserId());
        model.addAttribute("orders", myOrders);
        model.addAttribute("user", userDetails);
        return "my-deliveries";
    }

    /**
     * 배송 정보 수정 폼 화면
     */
    @GetMapping("/delivery/edit/{orderId}")
    public String editDeliveryForm(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        DeliveryOrderResponseDto order = deliveryOrderService.getOrder(orderId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderUpdateRequestDto(
                    order.recipientName(),
                    order.phone(),
                    order.zipcode(),
                    order.address(),
                    order.detailAddress()
            ));
        }
        model.addAttribute("order", order);
        model.addAttribute("user", userDetails);
        return "delivery-edit";
    }

    /**
     * 배송 정보 수정 처리
     */
    @PostMapping("/delivery/edit/{orderId}")
    public String updateDelivery(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("form") com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderUpdateRequestDto form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            DeliveryOrderResponseDto order = deliveryOrderService.getOrder(orderId);
            model.addAttribute("order", order);
            model.addAttribute("user", userDetails);
            return "delivery-edit";
        }

        try {
            deliveryOrderService.updateDeliveryOrder(userDetails.getUserId(), orderId, form);
            redirectAttributes.addFlashAttribute("successMessage", "배송지 정보가 성공적으로 수정되었습니다.");
            return "redirect:/delivery/my";
        } catch (Exception e) {
            log.warn("배송지 정보 수정 실패: {}", e.getMessage());
            DeliveryOrderResponseDto order = deliveryOrderService.getOrder(orderId);
            model.addAttribute("order", order);
            model.addAttribute("user", userDetails);
            model.addAttribute("errorMessage", e.getMessage());
            return "delivery-edit";
        }
    }
}
