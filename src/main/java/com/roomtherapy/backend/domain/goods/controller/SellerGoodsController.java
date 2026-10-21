package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
import com.roomtherapy.backend.domain.goods.service.GoodsService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/seller/goods")
@RequiredArgsConstructor
public class SellerGoodsController {

    private final GoodsService goodsService;

    /**
     * 판매자 굿즈 및 인증코드 등록 폼 화면
     */
    @GetMapping("/new")
    public String newGoodsForm(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new GoodsCreateRequestDto(
                    "", "", 0, "", 10, "", null
            ));
        }
        model.addAttribute("user", userDetails);
        return "seller/goods-new";
    }

    /**
     * 판매자 굿즈 및 인증코드 등록 제출 처리
     */
    @PostMapping
    public String createGoods(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("form") GoodsCreateRequestDto form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (bindingResult.hasErrors()) {
            model.addAttribute("user", userDetails);
            return "seller/goods-new";
        }

        try {
            GoodsResponseDto response = goodsService.registerGoods(userDetails.getUserId(), form);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("굿즈 '%s'(재고: %d개, 인증코드: %d개)가 성공적으로 등록되었습니다.",
                            response.name(), response.stockQuantity(), response.authCodeCount()));
            return "redirect:/goods";
        } catch (Exception e) {
            log.warn("굿즈 등록 실패: {}", e.getMessage());
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("user", userDetails);
            return "seller/goods-new";
        }
    }

    /**
     * 특정 굿즈의 등록된 인증코드 목록 확인
     */
    @GetMapping("/{goodsId}/codes")
    public String viewAuthCodes(
            @PathVariable Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        GoodsResponseDto goods = goodsService.getGoodsWithCodes(goodsId);
        model.addAttribute("goods", goods);
        model.addAttribute("user", userDetails);
        return "seller/goods-codes";
    }
}
