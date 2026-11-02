package com.roomtherapy.backend.domain.delivery.controller;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryExcelExportCondition;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryExcelImportResult;
import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryOrder;
import com.roomtherapy.backend.domain.delivery.entity.DeliveryStatus;
import com.roomtherapy.backend.domain.delivery.service.DeliveryExcelService;
import com.roomtherapy.backend.domain.delivery.service.DeliveryOrderService;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDeliveryController {

    private final DeliveryOrderService deliveryOrderService;
    private final GoodsRepository goodsRepository;
    private final DeliveryExcelService deliveryExcelService;

    /**
     * 관리자용 배송 신청 목록 대시보드 (페이징, 날짜 범위 검색, 수정 필터링)
     */
    @GetMapping("/deliveries")
    public String deliveriesList(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @RequestParam(name = "startDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(name = "endDate", required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate,
            @org.springframework.data.web.PageableDefault(size = 10, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) org.springframework.data.domain.Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        org.springframework.data.domain.Page<DeliveryOrderResponseDto> deliveryPage =
                deliveryOrderService.getAdminOrdersWithPaging(startDate, endDate, filter, pageable);

        model.addAttribute("deliveryPage", deliveryPage);
        model.addAttribute("deliveries", deliveryPage.getContent());
        model.addAttribute("allCount", deliveryOrderService.getAllOrdersForAdmin().size());
        model.addAttribute("modifiedCount", deliveryOrderService.getModifiedOrdersForAdmin().size());
        model.addAttribute("currentFilter", filter);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("user", userDetails);
        model.addAttribute("statuses", DeliveryStatus.values());
        model.addAttribute("goodsList", goodsRepository.findAll());
        return "admin/deliveries";
    }

    /**
     * 관리자용 배송 상태 변경 처리
     */
    @PostMapping("/deliveries/{orderId}/status")
    public String updateStatus(
            @PathVariable Long orderId,
            @RequestParam DeliveryStatus status,
            @RequestParam(name = "redirect", required = false) String redirect) {
        deliveryOrderService.updateOrderStatus(orderId, status);
        return (redirect != null && !redirect.isBlank()) ? "redirect:" + redirect : "redirect:/admin/deliveries";
    }

    /**
     * 관리자용 배송 주문 삭제 처리
     */
    @PostMapping("/deliveries/{orderId}/delete")
    public String deleteOrder(
            @PathVariable Long orderId,
            @RequestParam(name = "redirect", required = false) String redirect,
            RedirectAttributes redirectAttributes) {
        log.info("관리자 배송 주문 삭제 요청: orderId={}", orderId);
        deliveryOrderService.deleteOrder(orderId);
        redirectAttributes.addFlashAttribute("successMessage", "배송 주문 #" + orderId + "이(가) 정상적으로 삭제되었습니다.");
        return (redirect != null && !redirect.isBlank()) ? "redirect:" + redirect : "redirect:/admin/deliveries";
    }

    /**
     * 관리자용 배송 목록 엑셀 다운로드
     */
    @GetMapping("/deliveries/export/excel")
    public ResponseEntity<byte[]> exportExcel(@ModelAttribute DeliveryExcelExportCondition condition) {
        log.info("관리자 엑셀 다운로드 요청: condition={}", condition);
        List<DeliveryOrder> orders = deliveryOrderService.getOrdersForAdminExport(condition);
        byte[] excelBytes = deliveryExcelService.exportDeliveriesToExcel(orders, condition.columns());

        String filename = URLEncoder.encode("배송신청목록_" + LocalDate.now() + ".xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }

    /**
     * 관리자용 일괄 등록 엑셀 양식(템플릿) 다운로드
     */
    @GetMapping("/deliveries/template/excel")
    public ResponseEntity<byte[]> downloadTemplate() {
        log.info("관리자 엑셀 양식 다운로드 요청");
        byte[] templateBytes = deliveryExcelService.generateTemplateWorkbook();

        String filename = URLEncoder.encode("배송신청_일괄등록_양식.xlsx", StandardCharsets.UTF_8).replaceAll("\\+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(templateBytes);
    }

    /**
     * 관리자용 엑셀 일괄 등록 업로드 처리
     */
    @PostMapping("/deliveries/import/excel")
    public String importExcel(@RequestParam("file") MultipartFile file,
                              @AuthenticationPrincipal CustomUserDetails userDetails,
                              RedirectAttributes redirectAttributes) {
        log.info("관리자 엑셀 일괄 등록 시작: fileName={}", file.getOriginalFilename());
        try {
            DeliveryExcelImportResult result = deliveryExcelService.importDeliveriesFromExcel(file, userDetails.getUserId(), true);
            redirectAttributes.addFlashAttribute("importResult", result);
        } catch (Exception e) {
            log.error("엑셀 일괄 등록 중 오류 발생", e);
            redirectAttributes.addFlashAttribute("importError", "엑셀 파일을 처리하는 도중 오류가 발생했습니다: " + e.getMessage());
        }
        return "redirect:/admin/deliveries";
    }
}
