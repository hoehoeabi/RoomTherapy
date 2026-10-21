package com.roomtherapy.backend.domain.user.controller;

import com.roomtherapy.backend.domain.delivery.dto.DeliveryOrderResponseDto;
import com.roomtherapy.backend.domain.delivery.service.DeliveryOrderService;
import com.roomtherapy.backend.domain.user.dto.UserDeliveryInfoUpdateRequestDto;
import com.roomtherapy.backend.domain.user.dto.UserNicknameUpdateRequestDto;
import com.roomtherapy.backend.domain.user.dto.UserProfileResponseDto;
import com.roomtherapy.backend.domain.user.entity.User;
import com.roomtherapy.backend.domain.user.repository.UserRepository;
import com.roomtherapy.backend.global.security.user.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/mypage")
@RequiredArgsConstructor
public class MyPageController {

    private final UserRepository userRepository;
    private final DeliveryOrderService deliveryOrderService;

    /**
     * 마이페이지 메인 (프로필/기본 배송지 관리 + 내 굿즈 신청 내역 통합 조회)
     */
    @GetMapping
    public String myPage(
            @RequestParam(name = "tab", defaultValue = "profile") String tab,
            @PageableDefault(size = 5, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Model model) {

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        UserProfileResponseDto profile = UserProfileResponseDto.from(user);
        Page<DeliveryOrderResponseDto> myOrders = deliveryOrderService.getMyOrdersWithPaging(user.getId(), pageable);

        if (!model.containsAttribute("nicknameForm")) {
            model.addAttribute("nicknameForm", new UserNicknameUpdateRequestDto(user.getNickname()));
        }
        if (!model.containsAttribute("deliveryForm")) {
            model.addAttribute("deliveryForm", new UserDeliveryInfoUpdateRequestDto(
                    user.getRecipientName() != null ? user.getRecipientName() : "",
                    user.getPhone() != null ? user.getPhone() : "",
                    user.getZipcode() != null ? user.getZipcode() : "",
                    user.getAddress() != null ? user.getAddress() : "",
                    user.getDetailAddress() != null ? user.getDetailAddress() : ""
            ));
        }

        model.addAttribute("profile", profile);
        model.addAttribute("userProfile", profile);
        model.addAttribute("myOrders", myOrders);
        model.addAttribute("orders", myOrders);
        model.addAttribute("deliveryInfoForm", model.getAttribute("deliveryForm"));
        model.addAttribute("currentTab", tab);
        model.addAttribute("user", userDetails);
        return "mypage";
    }

    /**
     * 닉네임 수정 처리
     */
    @PostMapping("/nickname")
    @Transactional
    public String updateNickname(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("nicknameForm") UserNicknameUpdateRequestDto form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.nicknameForm", bindingResult);
            redirectAttributes.addFlashAttribute("nicknameForm", form);
            return "redirect:/mypage?tab=profile";
        }

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        user.updateNickname(form.nickname());
        redirectAttributes.addFlashAttribute("successMessage", "닉네임이 성공적으로 변경되었습니다.");
        log.info("사용자 닉네임 변경 완료 - UserId: {}, NewNickname: {}", user.getId(), user.getNickname());
        return "redirect:/mypage?tab=profile";
    }

    /**
     * 기본 배송지 정보 등록 및 수정 처리
     */
    @PostMapping("/delivery-info")
    @Transactional
    public String updateDeliveryInfo(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @ModelAttribute("deliveryForm") UserDeliveryInfoUpdateRequestDto form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.deliveryForm", bindingResult);
            redirectAttributes.addFlashAttribute("deliveryForm", form);
            return "redirect:/mypage?tab=profile";
        }

        User user = userRepository.findById(userDetails.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 회원입니다."));

        user.updateDefaultDeliveryInfo(
                form.recipientName(),
                form.phone(),
                form.zipcode(),
                form.address(),
                form.detailAddress()
        );

        redirectAttributes.addFlashAttribute("successMessage", "기본 배송지가 성공적으로 저장되었습니다.");
        log.info("사용자 기본 배송지 변경 완료 - UserId: {}", user.getId());
        return "redirect:/mypage?tab=profile";
    }
}
