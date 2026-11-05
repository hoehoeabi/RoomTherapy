package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.entity.Goods;
import com.roomtherapy.backend.domain.goods.entity.GoodsStatus;
import com.roomtherapy.backend.domain.goods.repository.GoodsRepository;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/goods")
@RequiredArgsConstructor
public class GoodsController {

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
            @org.springframework.web.bind.annotation.PathVariable Long goodsId,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        boolean isAdmin = userDetails != null && userDetails.isAdmin();
        Long currentUserId = userDetails != null ? userDetails.getUserId() : null;

        Goods goods = goodsService.getGoodsForExhibition(goodsId, currentUserId, isAdmin);
        List<com.roomtherapy.backend.domain.goods.dto.Goods3dObjectDto> objects3d = goodsService.getGoods3dObjects(goodsId);
        boolean canManage = goods.isManageableBy(currentUserId, isAdmin);

        model.addAttribute("goods", goods);
        model.addAttribute("objects3d", objects3d);
        model.addAttribute("canManage", canManage);
        model.addAttribute("user", userDetails);
        return "goods/exhibition";
    }
}
