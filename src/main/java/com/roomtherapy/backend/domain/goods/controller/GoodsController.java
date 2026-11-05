package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto;
import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Slf4j
@Controller
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

    private static final HttpClient MODEL_PROXY_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final GoodsRepository goodsRepository;
    private final com.roomtherapy.backend.domain.goods.service.GoodsService goodsService;

    /**
     * 굿즈 리워드 상품 목록 페이지 (전체 또는 내가 등록한 굿즈 필터링 지원)
     * 숨김(INACTIVE) 상품은 일반 유저에게 노출되지 않으며, 관리자 또는 해당 상품 등록자에게만 표시됩니다.
     */
    @GetMapping
    public String listGoods(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        boolean isAdmin = userDetails != null && userDetails.isAdmin();
        boolean isSeller = userDetails != null && userDetails.isSeller();
        Long currentUserId = userDetails != null ? userDetails.getUserId() : null;

        List<Goods> goodsList;
        if ("inactive".equals(filter) && userDetails != null && (isAdmin || isSeller)) {
            if (isAdmin) {
                goodsList = goodsRepository.findAll().stream()
                        .filter(g -> g.getStatus() == GoodsStatus.INACTIVE)
                        .toList();
            } else {
                goodsList = goodsRepository.findAllBySellerId(userDetails.getUserId()).stream()
                        .filter(g -> g.getStatus() == GoodsStatus.INACTIVE)
                        .toList();
            }
        } else if ("my".equals(filter) && userDetails != null && (isAdmin || isSeller)) {
            goodsList = goodsRepository.findAllBySellerId(userDetails.getUserId());
        } else {
            goodsList = goodsRepository.findAll().stream()
                    .filter(g -> g.getStatus() != GoodsStatus.INACTIVE || isAdmin || (isSeller && currentUserId != null && g.isOwnedBy(currentUserId)))
                    .toList();
        }

        java.util.Comparator<Goods> goodsComparator = java.util.Comparator
                .comparing((Goods g) -> {
                    if (g.getStatus() == GoodsStatus.INACTIVE) return 2;
                    if (g.getStatus() == GoodsStatus.SOLD_OUT || g.getStockQuantity() <= 0) return 1;
                    return 0;
                })
                .thenComparing(Goods::getId, java.util.Comparator.reverseOrder());

        goodsList = goodsList.stream()
                .sorted(goodsComparator)
                .toList();

        model.addAttribute("goodsList", goodsList);
        model.addAttribute("currentFilter", filter);
        model.addAttribute("user", userDetails);
        model.addAttribute("statuses", GoodsStatus.values());
        return "goods/list";
    }

    /**
     * 스테이지(굿즈)별 스페이셜 3D 오브제 전시관 페이지
     */
    @GetMapping("/{goodsId}/exhibition")
    public String viewExhibition(
            @PathVariable Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        boolean isAdmin = userDetails != null && userDetails.isAdmin();
        Long currentUserId = userDetails != null ? userDetails.getUserId() : null;

        Goods goods = goodsService.getGoodsForExhibition(goodsId, currentUserId, isAdmin);
        List<Goods3dObjectDto> objects3d = goodsService.getGoods3dObjects(goodsId);
        boolean canManage = goods.isManageableBy(currentUserId, isAdmin);

        model.addAttribute("goods", goods);
        model.addAttribute("objects3d", objects3d);
        model.addAttribute("canManage", canManage);
        model.addAttribute("user", userDetails);
        return "goods/exhibition";
    }

    /**
     * 3D 오브제 모델 동일 출처(Same-Origin) 스트리밍 엔드포인트.
     * 운영 환경에서 AWS S3 도메인(Cross-Origin)에 저장된 .glb/.fbx/.obj/.usdz 모델을 Three.js fetch()로 불러올 때
     * 발생하는 브라우저 CORS 차단 문제를 원천 해결합니다.
     */
    @GetMapping("/{goodsId}/objects/{objectId}/model/{filename:.+}")
    public void streamObject3dModel(
            @PathVariable Long goodsId,
            @PathVariable Long objectId,
            @PathVariable String filename,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            HttpServletResponse response) throws IOException {
        boolean isAdmin = userDetails != null && userDetails.isAdmin();
        Long currentUserId = userDetails != null ? userDetails.getUserId() : null;

        // 전시관 열람 권한 확인 (숨김 굿즈 접근 통제 동일 적용)
        goodsService.getGoodsForExhibition(goodsId, currentUserId, isAdmin);

        Goods3dObjectDto targetObj = goodsService.getGoods3dObjects(goodsId).stream()
                .filter(o -> Objects.equals(o.id(), objectId))
                .findFirst()
                .orElse(null);

        if (targetObj == null || targetObj.modelUrl() == null || targetObj.modelUrl().isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "3D 오브제 모델을 찾을 수 없습니다.");
            return;
        }

        String modelUrl = targetObj.modelUrl().trim();
        String contentType = resolveModelContentType(modelUrl);

        response.setContentType(contentType);
        response.setHeader("Cache-Control", "public, max-age=86400");
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("X-Accel-Buffering", "no");

        if (modelUrl.startsWith("http://") || modelUrl.startsWith("https://")) {
            try {
                HttpRequest upstreamRequest = HttpRequest.newBuilder(URI.create(modelUrl))
                        .timeout(Duration.ofMinutes(2))
                        .GET()
                        .build();

                HttpResponse<InputStream> upstreamResponse = MODEL_PROXY_HTTP_CLIENT.send(
                        upstreamRequest, HttpResponse.BodyHandlers.ofInputStream());

                if (upstreamResponse.statusCode() < 200 || upstreamResponse.statusCode() >= 300) {
                    log.warn("S3 3D 모델 원격 조회 실패 (status={}): {}", upstreamResponse.statusCode(), modelUrl);
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "원격 스토리지에서 3D 모델을 불러올 수 없습니다.");
                    return;
                }

                upstreamResponse.headers().firstValueAsLong("content-length")
                        .ifPresent(response::setContentLengthLong);

                try (InputStream in = upstreamResponse.body()) {
                    in.transferTo(response.getOutputStream());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "3D 모델 스트리밍이 중단되었습니다.");
            } catch (Exception e) {
                log.error("3D 모델 스트리밍 프록시 오류: {}", modelUrl, e);
                if (!response.isCommitted()) {
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "3D 모델 스트리밍 중 오류가 발생했습니다.");
                }
            }
            return;
        }

        if (modelUrl.startsWith("/uploads/")) {
            Path rootLocation = Paths.get("uploads").toAbsolutePath().normalize();
            String relativeSubPath = modelUrl.substring("/uploads/".length());
            Path filePath = rootLocation.resolve(relativeSubPath).normalize();
            if (!filePath.startsWith(rootLocation) || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "로컬 3D 모델 파일을 찾을 수 없습니다.");
                return;
            }
            response.setContentLengthLong(Files.size(filePath));
            Files.copy(filePath, response.getOutputStream());
            return;
        }

        response.sendRedirect(modelUrl);
    }

    private String resolveModelContentType(String modelUrl) {
        String clean = modelUrl.split("\\?")[0].toLowerCase();
        if (clean.endsWith(".glb")) {
            return "model/gltf-binary";
        } else if (clean.endsWith(".gltf")) {
            return "model/gltf+json";
        } else if (clean.endsWith(".usdz")) {
            return "model/vnd.usdz+zip";
        } else if (clean.endsWith(".obj")) {
            return "text/plain; charset=UTF-8";
        }
        return "application/octet-stream";
    }
}
