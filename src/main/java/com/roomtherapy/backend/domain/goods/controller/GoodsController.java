package com.roomtherapy.backend.domain.goods.controller;

import com.roomtherapy.backend.domain.goods.entity.Goods;
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

    /**
     * 굿즈 리워드 상품 목록 페이지 (전체 또는 내가 등록한 굿즈 필터링 지원)
     */
    @GetMapping
    public String listGoods(
            @RequestParam(name = "filter", defaultValue = "all") String filter,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {
        List<Goods> goodsList;
        if ("my".equals(filter) && userDetails != null) {
            goodsList = goodsRepository.findAllBySellerId(userDetails.getUserId());
        } else {
            goodsList = goodsRepository.findAll();
        }
        model.addAttribute("goodsList", goodsList);
        model.addAttribute("currentFilter", filter);
        model.addAttribute("user", userDetails);
        return "goods/list";
    }
}
