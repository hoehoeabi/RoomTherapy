package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.dto.GoodsCreateRequestDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsResponseDto;
import com.roomtherapy.backend.domain.goods.dto.GoodsUpdateRequestDto;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.service.GoodsService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/seller/goods")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
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

        // 굿즈 사진 필수 검증 (사진 파일 업로드 또는 이미지 URL 입력 필수)
        boolean hasUploadedFiles = imageFiles != null && imageFiles.stream().anyMatch(f -> f != null && !f.isEmpty());
        boolean hasImageUrl = form.imageUrl() != null && !form.imageUrl().trim().isBlank();
        if (!hasUploadedFiles && !hasImageUrl) {
            bindingResult.rejectValue("imageUrl", "required", "굿즈 사진을 최소 1장 이상 등록(파일 업로드 또는 이미지 URL)해야 합니다.");
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
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal.userId)")
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

    /**
     * 굿즈 수정 폼 화면 (판매자 본인 또는 관리자 접근 가능)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal.userId)")
    @GetMapping("/{goodsId}/edit")
    public String editGoodsForm(
            @PathVariable Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        Goods goods = goodsService.getGoodsForManagement(goodsId, userDetails.getUserId(), userDetails.isAdmin());
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", GoodsUpdateRequestDto.from(goods));
        }
        model.addAttribute("goods", goods);
        model.addAttribute("user", userDetails);
        return "seller/goods-edit";
    }

    /**
     * 굿즈 수정 제출 처리 (다중 이미지 추가 업로드 지원)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal.userId)")
    @PostMapping("/{goodsId}/edit")
    public String updateGoods(
            @PathVariable Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("form") GoodsUpdateRequestDto form,
            BindingResult bindingResult,
            @org.springframework.web.bind.annotation.RequestParam(name = "imageFiles", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> imageFiles,
            RedirectAttributes redirectAttributes,
            Model model) {

        if (userDetails == null) {
            return "redirect:/login";
        }

        if (bindingResult.hasErrors()) {
            String firstError = bindingResult.getAllErrors().isEmpty() ? "입력값을 다시 확인해주세요."
                    : bindingResult.getAllErrors().get(0).getDefaultMessage();
            model.addAttribute("errorMessage", firstError);
            Goods goods = goodsService.getGoodsForManagement(goodsId, userDetails.getUserId(), userDetails.isAdmin());
            model.addAttribute("goods", goods);
            model.addAttribute("user", userDetails);
            return "seller/goods-edit";
        }

        try {
            GoodsUpdateRequestDto targetForm = form;
            // 새 이미지 파일들이 업로드된 경우 기존 URL 뒤에 결합
            if (imageFiles != null && !imageFiles.isEmpty()) {
                java.util.List<String> uploadedUrls = storageService.uploadFiles(imageFiles, "goods");
                if (!uploadedUrls.isEmpty()) {
                    String combinedUrls;
                    if (form.imageUrl() != null && !form.imageUrl().trim().isBlank()) {
                        combinedUrls = form.imageUrl().trim() + "," + String.join(",", uploadedUrls);
                    } else {
                        combinedUrls = String.join(",", uploadedUrls);
                    }
                    targetForm = form.withImageUrl(combinedUrls);
                }
            }

            goodsService.updateGoods(goodsId, userDetails.getUserId(), userDetails.isAdmin(), targetForm);
            redirectAttributes.addFlashAttribute("successMessage", "굿즈 정보가 성공적으로 수정되었습니다.");
            return "redirect:/goods";
        } catch (Exception e) {
            log.error("굿즈 수정 실패", e);
            model.addAttribute("errorMessage", e.getMessage() != null ? e.getMessage() : "굿즈 수정 중 오류가 발생했습니다.");
            Goods goods = goodsService.getGoodsForManagement(goodsId, userDetails.getUserId(), userDetails.isAdmin());
            model.addAttribute("goods", goods);
            model.addAttribute("user", userDetails);
            return "seller/goods-edit";
        }
    }

    /**
     * 굿즈 단독 재고 수량 변경 처리 (목록 등에서 간편 변경)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal.userId)")
    @PostMapping("/{goodsId}/stock")
    public String updateStock(
            @PathVariable Long goodsId,
            @org.springframework.web.bind.annotation.RequestParam("stockQuantity") int stockQuantity,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        goodsService.updateGoodsStock(goodsId, userDetails.getUserId(), userDetails.isAdmin(), stockQuantity);
        redirectAttributes.addFlashAttribute("successMessage", "재고 수량이 " + stockQuantity + "개로 변경되었습니다.");
        return "redirect:/goods";
    }

    /**
     * 굿즈 삭제 처리 (판매자 본인 또는 관리자 접근 가능)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal.userId)")
    @PostMapping("/{goodsId}/delete")
    public String deleteGoods(
            @PathVariable Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        goodsService.deleteGoods(goodsId, userDetails.getUserId(), userDetails.isAdmin());
        redirectAttributes.addFlashAttribute("successMessage", "굿즈가 성공적으로 삭제되었습니다.");
        return "redirect:/goods";
    }
}
