package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.dto.GoodsExcelImportResult;
import com.roomtherapy.backend.domain.goods.service.GoodsExcelService;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Slf4j
@Controller
@RequestMapping("/admin/goods")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminGoodsController {

    private final GoodsExcelService goodsExcelService;

    /**
     * 관리자용 굿즈 일괄 등록 엑셀 템플릿 다운로드
     */
    @GetMapping("/template/excel")
    public ResponseEntity<byte[]> downloadTemplate() {
        log.info("관리자 굿즈 일괄 등록 엑셀 양식 다운로드 요청");
        byte[] templateBytes = goodsExcelService.generateTemplateWorkbook();

        String filename = URLEncoder.encode("굿즈_일괄등록_양식.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(templateBytes);
    }

    /**
     * 관리자용 굿즈 일괄 등록 처리 (.xlsx 또는 .zip 업로드)
     */
    @PostMapping("/import/excel")
    public String importGoodsExcel(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        if (userDetails == null) {
            return "redirect:/login";
        }

        log.info("관리자 굿즈 엑셀/ZIP 일괄 등록 시작: adminId={}, fileName={}",
                userDetails.getUserId(), file != null ? file.getOriginalFilename() : "null");

        try {
            GoodsExcelImportResult result = goodsExcelService.importGoods(file, userDetails.getUserId(), true);
            redirectAttributes.addFlashAttribute("importResult", result);
            if (!result.hasErrors() && result.successCount() > 0) {
                redirectAttributes.addFlashAttribute("successMessage",
                        String.format("총 %d개의 굿즈가 성공적으로 일괄 등록되었습니다.", result.successCount()));
            }
        } catch (Exception e) {
            log.error("관리자 굿즈 일괄 등록 처리 중 예외 발생", e);
            redirectAttributes.addFlashAttribute("errorMessage", "굿즈 일괄 등록 도중 오류가 발생했습니다: " + e.getMessage());
        }

        return "redirect:/goods";
    }
}
