package com.roomtherapy.backend.global.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 애플리케이션 파일 업로드 용량 및 임시 디렉터리 중앙 설정 프로퍼티
 * (Single Source of Truth: application.yml에서 일괄 관리)
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /**
     * 굿즈 사진 1장당 최대 업로드 허용 용량 (MB 단위, 기본값: 70MB)
     */
    private int maxImageMb = 70;

    /**
     * 3D 오브제 모델 파일 1개당 최대 업로드 허용 용량 (MB 단위, 기본값: 300MB)
     */
    private int maxObjectMb = 300;

    /**
     * 1회 요청 전체(Multipart Body) 최대 업로드 허용 한도 (MB 단위, 기본값: 3000MB / 3GB)
     */
    private int maxTotalMb = 3000;

    /**
     * 대용량 파일 업로드 시 톰캣이 임시 버퍼링할 디렉터리 경로
     * (기본값: 시스템 기본 임시 폴더, 운영 환경: /var/tmp)
     */
    private String tempDir;
}
