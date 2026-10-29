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
    private final com.roomtherapy.backend.global.storage.StorageService storageService;

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
     * 판매자 굿즈 및 인증코드 등록 제출 처리 (다중 이미지 업로드 지원)
     */
    @PostMapping
    public String createGoods(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("form") GoodsCreateRequestDto form,
            BindingResult bindingResult,
            @org.springframework.web.bind.annotation.RequestParam(name = "imageFiles", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> imageFiles,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (userDetails == null) {
            return "redirect:/login";
        }

        // 인증코드 필수 검증 (최소 1개 이상 입력)
        if (form.extractAuthCodes().isEmpty()) {
            bindingResult.rejectValue("authCodesInput", "required", "인증코드는 최소 1개 이상 필수로 등록해야 합니다.");
        }

        if (bindingResult.hasErrors()) {
            String firstError = bindingResult.getAllErrors().isEmpty() ? "입력값을 다시 확인해주세요."
                    : bindingResult.getAllErrors().get(0).getDefaultMessage();
            model.addAttribute("errorMessage", firstError);
            model.addAttribute("user", userDetails);
            return "seller/goods-new";
        }

        try {
            GoodsCreateRequestDto targetForm = form;
            // 다중 이미지 파일이 업로드된 경우 스토리지 저장 후 URL 결합
            if (imageFiles != null && !imageFiles.isEmpty()) {
                java.util.List<String> uploadedUrls = storageService.uploadFiles(imageFiles, "goods");
                if (!uploadedUrls.isEmpty()) {
                    String combinedUrls = String.join(",", uploadedUrls);
                    targetForm = form.withImageUrl(combinedUrls);
                }
            }

            GoodsResponseDto response = goodsService.registerGoods(userDetails.getUserId(), targetForm);
            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("굿즈 '%s'(재고: %d개, 인증코드: %d개)가 성공적으로 등록되었습니다.",
                            response.name(), response.stockQuantity(), response.authCodeCount()));
            return "redirect:/goods";
        } catch (Exception e) {
            log.error("굿즈 등록 실패", e);
            String message = (e.getMessage() != null && !e.getMessage().isBlank()) ? e.getMessage() : "굿즈 등록 처리 중 오류가 발생했습니다.";
            model.addAttribute("errorMessage", message);
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
