package com.roomtherapy.backend.domain.home.controller;

import com.roomtherapy.backend.domain.delivery.service.DeliveryOrderService;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final DeliveryOrderService deliveryOrderService;

    @GetMapping("/")
    public String index(@AuthenticationPrincipal CustomUserDetails userDetails, Model model) {
        if (userDetails == null) {
            return "index";
        }

        model.addAttribute("user", userDetails);
        deliveryOrderService.findLatestOrderSummary(userDetails.getUserId())
                .ifPresent(summary -> model.addAttribute("recentOrderSummary", summary));

        return "index";
    }
}
