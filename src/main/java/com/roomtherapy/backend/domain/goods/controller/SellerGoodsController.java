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
    private final com.roomtherapy.backend.domain.goods.service.GoodsExcelService goodsExcelService;

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
     * 판매자 굿즈 및 인증코드 등록 제출 처리 (다중 이미지 및 3D 오브제 업로드 지원)
     */
    @PostMapping
    public String createGoods(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("form") GoodsCreateRequestDto form,
            BindingResult bindingResult,
            @org.springframework.web.bind.annotation.RequestParam(name = "imageFiles", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> imageFiles,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectIds", required = false) java.util.List<String> objectIds,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectNames", required = false) java.util.List<String> objectNames,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectDescriptions", required = false) java.util.List<String> objectDescriptions,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectModelUrls", required = false) java.util.List<String> objectModelUrls,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectModelFiles", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> objectModelFiles,
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
                java.util.List<org.springframework.web.multipart.MultipartFile> validFiles = imageFiles.stream()
                        .filter(f -> f != null && !f.isEmpty())
                        .toList();
                if (!validFiles.isEmpty()) {
                    java.util.List<String> uploadedUrls = storageService.uploadFiles(validFiles, "goods");
                    String combinedUrls = resolveCombinedImageUrls(form.imageUrl(), uploadedUrls);
                    targetForm = form.withImageUrl(combinedUrls);
                } else {
                    targetForm = form.withImageUrl(cleanImageUrls(form.imageUrl()));
                }
            } else {
                targetForm = form.withImageUrl(cleanImageUrls(form.imageUrl()));
            }

            java.util.List<com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto> objects3d =
                    resolveGoods3dObjects(objectIds, objectNames, objectDescriptions, objectModelUrls, objectModelFiles);

            GoodsResponseDto response = goodsService.registerGoods(userDetails.getUserId(), targetForm, objects3d);
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
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal)")
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
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal)")
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
        model.addAttribute("objects3d", goodsService.getGoods3dObjects(goodsId));
        model.addAttribute("user", userDetails);
        return "seller/goods-edit";
    }

    /**
     * 굿즈 수정 제출 처리 (다중 이미지 및 3D 오브제 추가/수정/삭제 지원)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal)")
    @PostMapping("/{goodsId}/edit")
    public String updateGoods(
            @PathVariable Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("form") GoodsUpdateRequestDto form,
            BindingResult bindingResult,
            @org.springframework.web.bind.annotation.RequestParam(name = "imageFiles", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> imageFiles,
            @org.springframework.web.bind.annotation.RequestParam(name = "objects3dSubmitted", required = false) Boolean objects3dSubmitted,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectIds", required = false) java.util.List<String> objectIds,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectNames", required = false) java.util.List<String> objectNames,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectDescriptions", required = false) java.util.List<String> objectDescriptions,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectModelUrls", required = false) java.util.List<String> objectModelUrls,
            @org.springframework.web.bind.annotation.RequestParam(name = "objectModelFiles", required = false) java.util.List<org.springframework.web.multipart.MultipartFile> objectModelFiles,
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
            model.addAttribute("objects3d", goodsService.getGoods3dObjects(goodsId));
            model.addAttribute("user", userDetails);
            return "seller/goods-edit";
        }

        try {
            GoodsUpdateRequestDto targetForm = form;
            if (imageFiles != null && !imageFiles.isEmpty()) {
                java.util.List<org.springframework.web.multipart.MultipartFile> validFiles = imageFiles.stream()
                        .filter(f -> f != null && !f.isEmpty())
                        .toList();
                if (!validFiles.isEmpty()) {
                    java.util.List<String> uploadedUrls = storageService.uploadFiles(validFiles, "goods");
                    String combinedUrls = resolveCombinedImageUrls(form.imageUrl(), uploadedUrls);
                    targetForm = form.withImageUrl(combinedUrls);
                } else {
                    targetForm = form.withImageUrl(cleanImageUrls(form.imageUrl()));
                }
            } else {
                targetForm = form.withImageUrl(cleanImageUrls(form.imageUrl()));
            }

            if (targetForm.imageUrl() == null || targetForm.imageUrl().trim().isEmpty()) {
                model.addAttribute("errorMessage", "굿즈 사진은 최소 1장 이상 등록되어야 합니다.");
                Goods goods = goodsService.getGoodsForManagement(goodsId, userDetails.getUserId(), userDetails.isAdmin());
                model.addAttribute("goods", goods);
                model.addAttribute("objects3d", goodsService.getGoods3dObjects(goodsId));
                model.addAttribute("user", userDetails);
                return "seller/goods-edit";
            }

            boolean has3dParams = Boolean.TRUE.equals(objects3dSubmitted)
                    || objectNames != null || objectModelUrls != null || objectModelFiles != null;
            java.util.List<com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto> objects3d = has3dParams
                    ? resolveGoods3dObjects(objectIds, objectNames, objectDescriptions, objectModelUrls, objectModelFiles)
                    : null;

            goodsService.updateGoods(goodsId, userDetails.getUserId(), userDetails.isAdmin(), targetForm, objects3d);
            redirectAttributes.addFlashAttribute("successMessage", "굿즈 정보가 성공적으로 수정되었습니다.");
            return "redirect:/goods";
        } catch (Exception e) {
            log.error("굿즈 수정 실패", e);
            model.addAttribute("errorMessage", e.getMessage() != null ? e.getMessage() : "굿즈 수정 중 오류가 발생했습니다.");
            Goods goods = goodsService.getGoodsForManagement(goodsId, userDetails.getUserId(), userDetails.isAdmin());
            model.addAttribute("goods", goods);
            model.addAttribute("objects3d", goodsService.getGoods3dObjects(goodsId));
            model.addAttribute("user", userDetails);
            return "seller/goods-edit";
        }
    }

    /**
     * 개별 3D 오브제 삭제 처리 (판매자 본인 또는 관리자)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal)")
    @PostMapping("/{goodsId}/objects/{objectId}/delete")
    public String deleteGoods3dObject(
            @PathVariable Long goodsId,
            @PathVariable Long objectId,
            @org.springframework.web.bind.annotation.RequestParam(name = "redirect", required = false) String redirect,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        try {
            goodsService.deleteGoods3dObject(goodsId, objectId, userDetails.getUserId(), userDetails.isAdmin());
            redirectAttributes.addFlashAttribute("successMessage", "선택한 3D 오브제가 삭제되었습니다.");
        } catch (Exception e) {
            log.error("3D 오브제 개별 삭제 실패", e);
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage() != null ? e.getMessage() : "3D 오브제 삭제 중 오류가 발생했습니다.");
        }
        return "redirect:" + resolveSafeRedirect(redirect, "/seller/goods/" + goodsId + "/edit");
    }

    /**
     * 굿즈 단독 재고 수량 변경 처리 (목록 등에서 간편 변경)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal)")
    @PostMapping("/{goodsId}/stock")
    public String updateStock(
            @PathVariable Long goodsId,
            @org.springframework.web.bind.annotation.RequestParam("stockQuantity") int stockQuantity,
            @org.springframework.web.bind.annotation.RequestParam(name = "redirect", required = false) String redirect,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        goodsService.updateGoodsStock(goodsId, userDetails.getUserId(), userDetails.isAdmin(), stockQuantity);
        redirectAttributes.addFlashAttribute("successMessage", "재고 수량이 " + stockQuantity + "개로 변경되었습니다.");
        return "redirect:" + resolveSafeRedirect(redirect, "/goods");
    }

    /**
     * 굿즈 상태 변경 처리 (판매중 / 품절 / 숨김)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal)")
    @PostMapping("/{goodsId}/status")
    public String updateStatus(
            @PathVariable Long goodsId,
            @org.springframework.web.bind.annotation.RequestParam("status") com.roomtherapy.backend.domain.goods.entity.GoodsStatus status,
            @org.springframework.web.bind.annotation.RequestParam(name = "redirect", required = false) String redirect,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        goodsService.updateGoodsStatus(goodsId, userDetails.getUserId(), userDetails.isAdmin(), status);
        redirectAttributes.addFlashAttribute("successMessage", "굿즈 상태가 '" + status.getDescription() + "'(으)로 변경되었습니다.");
        return "redirect:" + resolveSafeRedirect(redirect, "/goods");
    }

    /**
     * 굿즈 삭제 처리 (판매자 본인 또는 관리자 접근 가능)
     */
    @PreAuthorize("hasRole('ADMIN') or @goodsService.isOwner(#goodsId, principal)")
    @PostMapping("/{goodsId}/delete")
    public String deleteGoods(
            @PathVariable Long goodsId,
            @org.springframework.web.bind.annotation.RequestParam(name = "redirect", required = false) String redirect,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }
        goodsService.deleteGoods(goodsId, userDetails.getUserId(), userDetails.isAdmin());
        redirectAttributes.addFlashAttribute("successMessage", "굿즈가 성공적으로 삭제되었습니다.");
        return "redirect:" + resolveSafeRedirect(redirect, "/goods");
    }

    private String resolveSafeRedirect(String redirect, String defaultPath) {
        if (redirect != null && redirect.startsWith("/") && !redirect.startsWith("//") && !redirect.startsWith("/\\")) {
            return redirect;
        }
        return defaultPath;
    }

    /**
     * 기존 이미지 URL 및 신규 업로드된 이미지 파일 URL들을 정렬 순서 및 플레이스홀더에 맞게 결합
     */
    private String resolveCombinedImageUrls(String imageUrl, java.util.List<String> uploadedUrls) {
        if (uploadedUrls == null || uploadedUrls.isEmpty()) {
            return cleanImageUrls(imageUrl);
        }

        if (imageUrl == null || imageUrl.trim().isBlank()) {
            return String.join(",", uploadedUrls);
        }

        String resolved = imageUrl.trim();
        java.util.Set<Integer> replacedIndices = new java.util.HashSet<>();

        for (int i = 0; i < uploadedUrls.size(); i++) {
            String placeholder = "__NEW_FILE_" + i + "__";
            if (resolved.contains(placeholder)) {
                resolved = resolved.replace(placeholder, uploadedUrls.get(i));
                replacedIndices.add(i);
            }
        }

        java.util.List<String> unplacedUrls = new java.util.ArrayList<>();
        for (int i = 0; i < uploadedUrls.size(); i++) {
            if (!replacedIndices.contains(i)) {
                unplacedUrls.add(uploadedUrls.get(i));
            }
        }

        if (!unplacedUrls.isEmpty()) {
            if (!resolved.isBlank()) {
                resolved = resolved + "," + String.join(",", unplacedUrls);
            } else {
                resolved = String.join(",", unplacedUrls);
            }
        }

        return cleanImageUrls(resolved);
    }

    private String cleanImageUrls(String urls) {
        if (urls == null || urls.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(urls.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("__NEW_FILE_"))
                .collect(java.util.stream.Collectors.joining(","));
    }

    /**
     * 폼에서 전달된 3D 오브제(ID, 이름, 설명, 모델 URL/플레이스홀더)와 업로드된 3D 모델 파일을 결합하여 DTO 리스트 생성
     */
    private java.util.List<com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto> resolveGoods3dObjects(
            java.util.List<String> objectIds,
            java.util.List<String> objectNames,
            java.util.List<String> objectDescriptions,
            java.util.List<String> objectModelUrls,
            java.util.List<org.springframework.web.multipart.MultipartFile> objectModelFiles) {

        java.util.List<String> uploadedModelUrls = java.util.List.of();
        if (objectModelFiles != null && !objectModelFiles.isEmpty()) {
            java.util.List<org.springframework.web.multipart.MultipartFile> validModelFiles = objectModelFiles.stream()
                    .filter(f -> f != null && !f.isEmpty())
                    .toList();
            if (!validModelFiles.isEmpty()) {
                uploadedModelUrls = storageService.uploadFiles(validModelFiles, "goods-3d");
            }
        }

        int count = 0;
        if (objectNames != null) count = Math.max(count, objectNames.size());
        if (objectModelUrls != null) count = Math.max(count, objectModelUrls.size());
        if (objectDescriptions != null) count = Math.max(count, objectDescriptions.size());
        if (objectIds != null) count = Math.max(count, objectIds.size());

        java.util.List<com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto> result = new java.util.ArrayList<>();
        int fallbackFileIdx = 0;

        for (int i = 0; i < count; i++) {
            Long id = null;
            if (objectIds != null && i < objectIds.size() && objectIds.get(i) != null && !objectIds.get(i).isBlank()) {
                try {
                    id = Long.parseLong(objectIds.get(i).trim());
                } catch (NumberFormatException ignored) {
                }
            }
            String name = (objectNames != null && i < objectNames.size() && objectNames.get(i) != null)
                    ? objectNames.get(i).trim() : "";
            String desc = (objectDescriptions != null && i < objectDescriptions.size() && objectDescriptions.get(i) != null)
                    ? objectDescriptions.get(i).trim() : "";
            String rawModelUrl = (objectModelUrls != null && i < objectModelUrls.size() && objectModelUrls.get(i) != null)
                    ? objectModelUrls.get(i).trim() : "";

            if (rawModelUrl.startsWith("__NEW_MODEL_") && rawModelUrl.endsWith("__")) {
                String idxStr = rawModelUrl.substring("__NEW_MODEL_".length(), rawModelUrl.length() - 2);
                try {
                    int fileIdx = Integer.parseInt(idxStr);
                    if (fileIdx >= 0 && fileIdx < uploadedModelUrls.size()) {
                        rawModelUrl = uploadedModelUrls.get(fileIdx);
                    }
                } catch (NumberFormatException ignored) {
                }
            } else if (rawModelUrl.isEmpty() && fallbackFileIdx < uploadedModelUrls.size()) {
                rawModelUrl = uploadedModelUrls.get(fallbackFileIdx++);
            }

            if (name.isEmpty() && rawModelUrl.isEmpty()) {
                continue;
            }
            result.add(new com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto(id, name, desc, rawModelUrl, result.size()));
        }

        return result;
    }

    /**
     * 굿즈 일괄 등록용 엑셀 표준 템플릿 다운로드
     */
    @GetMapping("/template/excel")
    public org.springframework.http.ResponseEntity<byte[]> downloadTemplate() {
        log.info("굿즈 일괄 등록 엑셀 양식 다운로드 요청");
        byte[] templateBytes = goodsExcelService.generateTemplateWorkbook();

        String filename = java.net.URLEncoder.encode("굿즈_일괄등록_양식.xlsx", java.nio.charset.StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(templateBytes);
    }

    /**
     * 굿즈 일괄 등록 처리 (.xlsx 또는 .zip 업로드)
     */
    @PostMapping("/import/excel")
    public String importGoodsExcel(
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        log.info("굿즈 엑셀/ZIP 일괄 등록 시작: userId={}, isAdmin={}, fileName={}",
                userDetails.getUserId(), userDetails.isAdmin(), file != null ? file.getOriginalFilename() : "null");

        try {
            com.roomtherapy.backend.domain.goods.dto.GoodsExcelImportResult result =
                    goodsExcelService.importGoods(file, userDetails.getUserId(), userDetails.isAdmin());
            redirectAttributes.addFlashAttribute("importResult", result);
            if (!result.hasErrors() && result.successCount() > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("총 %d개의 굿즈가 성공적으로 일괄 등록되었습니다.", result.successCount()));
            }
        } catch (Exception e) {
            log.error("굿즈 일괄 등록 처리 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("errorMessage", "굿즈 일괄 등록 도중 오류가 발생했습니다: " + e.getMessage());
        }

        return "redirect:/goods";
    }
}
