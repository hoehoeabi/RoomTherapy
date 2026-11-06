package com.roomtherapy.backend.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * 모든 Thymeleaf 뷰 템플릿에 파일 업로드 중앙 설정 프로퍼티를 "uploadConfig" 모델 속성으로 자동 주입하는 ControllerAdvice
 */
@ControllerAdvice
@RequiredArgsConstructor
public class UploadModelAdvice {

    private final UploadProperties uploadProperties;

    @ModelAttribute("uploadConfig")
    public UploadProperties uploadConfig() {
        return uploadProperties;
    }
}
