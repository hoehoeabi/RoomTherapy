package com.roomtherapy.backend.domain.delivery.controller;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderRequestDto;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderUpdateRequestDto;
import com.roomtherapy.backend.domain.delivery.service.DeliveryOrderService;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Optional;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryOrderService deliveryOrderService;
    private final GoodsRepository goodsRepository;
    private final UserRepository userRepository;

    /**
     * 굿즈 배송 신청 폼 화면 (마이페이지 기본 배송지 자동 완성 연동)
     */
    @GetMapping("/delivery")
    public String deliveryForm(
            @RequestParam(name = "goodsId", required = false) Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        if (!model.containsAttribute("form")) {
            // 유저의 기본 배송지 정보가 있으면 폼에 자동 입력
            var userOpt = userDetails != null ? userRepository.findById(userDetails.getUserId()) : Optional.<User>empty();
            String defaultRecipient = userOpt.map(User::getRecipientName).orElse("");
            String defaultPhone = userOpt.map(User::getPhone).orElse("");
            String defaultZipcode = userOpt.map(User::getZipcode).orElse("");
            String defaultAddress = userOpt.map(User::getAddress).orElse("");
            String defaultDetailAddress = userOpt.map(User::getDetailAddress).orElse("");

            model.addAttribute("form", new DeliveryOrderRequestDto(
                    "", defaultRecipient, defaultPhone, defaultZipcode, defaultAddress, defaultDetailAddress, goodsId, false
            ));
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
     * 굿즈 배송 신청 완료 및 확인 페이지 (본인 주문 또는 관리자만 조회 가능)
     */
    @PreAuthorize("hasRole('ADMIN') or @deliveryOrderService.isOrderOwner(#orderId, principal.userId)")
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
     * 내가 신청한 배송 목록 조회 (마이페이지의 배송 내역 탭으로 통합 리다이렉트)
     */
    @GetMapping("/delivery/my")
    public String myDeliveries() {
        return "redirect:/mypage?tab=orders";
    }

    /**
     * 배송 정보 수정 폼 화면 (본인 주문 또는 관리자만 접근 가능)
     */
    @PreAuthorize("hasRole('ADMIN') or @deliveryOrderService.isOrderOwner(#orderId, principal.userId)")
    @GetMapping("/delivery/edit/{orderId}")
    public String editDeliveryForm(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        DeliveryOrderResponseDto order = deliveryOrderService.getOrder(orderId);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new DeliveryOrderUpdateRequestDto(
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
     * 배송 정보 수정 처리 (본인 주문 또는 관리자만 수정 가능)
     */
    @PreAuthorize("hasRole('ADMIN') or @deliveryOrderService.isOrderOwner(#orderId, principal.userId)")
    @PostMapping("/delivery/edit/{orderId}")
    public String updateDelivery(
            @PathVariable Long orderId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("form") DeliveryOrderUpdateRequestDto form,
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
            return "redirect:/mypage?tab=orders";
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
