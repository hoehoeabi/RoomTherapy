package com.roomtherapy.backend.global.storage;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AWS S3 기반 스토리지 구현체 (운영 환경 prod 프로필에서 동작)
 */
@Slf4j
@Profile("prod")
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private static final java.util.Set<String> ALLOWED_EXTENSIONS = java.util.Set.of(
            ".png", ".jpg", ".jpeg", ".webp", ".gif",
            ".glb", ".gltf", ".fbx", ".obj", ".usdz"
    );

    private static final int MAX_PARALLEL_UPLOADS = 6;
    private static final ExecutorService UPLOAD_EXECUTOR = Executors.newFixedThreadPool(MAX_PARALLEL_UPLOADS, r -> {
        Thread t = new Thread(r, "s3-parallel-upload");
        t.setDaemon(true);
        return t;
    });

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    /**
     * 서버 기동 시 S3 버킷에 3D 오브제(Three.js fetch/XHR) 로드를 위한 CORS 규칙 자동 적용 시도.
     * IAM 권한(s3:PutBucketCors)이 없더라도 기동이 중단되지 않도록 안전하게 예외를 처리합니다.
     */
    @PostConstruct
    public void ensureBucketCorsConfigured() {
        if (bucketName == null || bucketName.isBlank()) {
            return;
        }
        try {
            CORSRule corsRule = CORSRule.builder()
                    .allowedOrigins("https://www.roomtherapy.kr", "https://roomtherapy.kr", "http://localhost:8080", "*")
                    .allowedMethods("GET", "HEAD")
                    .allowedHeaders("*")
                    .exposeHeaders("Content-Length", "Content-Type", "ETag")
                    .maxAgeSeconds(86400)
                    .build();

            CORSConfiguration corsConfig = CORSConfiguration.builder()
                    .corsRules(corsRule)
                    .build();

            s3Client.putBucketCors(PutBucketCorsRequest.builder()
                    .bucket(bucketName)
                    .corsConfiguration(corsConfig)
                    .build());
            log.info("AWS S3 버킷({}) CORS 정책 자동 설정 완료", bucketName);
        } catch (Exception e) {
            log.info("AWS S3 버킷({}) CORS 자동 설정 건너뜀 (백엔드 동일 출처 스트리밍 프록시로 동작): {}", bucketName, e.getMessage());
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
        String extension = "";
        int extIdx = originalFilename.lastIndexOf('.');
        if (extIdx >= 0) {
            extension = originalFilename.substring(extIdx).toLowerCase();
        }
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("허용되지 않는 파일 확장자입니다: " + extension);
        }

        try {
            String savedFilename = UUID.randomUUID().toString().replace("-", "") + extension;
            String key = (directory != null && !directory.isBlank())
                    ? directory.trim() + "/" + savedFilename
                    : savedFilename;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("AWS S3 파일 업로드 성공: {} -> s3://{}/{}", originalFilename, bucketName, key);

            // S3 퍼블릭 접근 정적 URL 생성
            return String.format("https://%s.s3.%s.amazonaws.com/%s", bucketName, region, key);
        } catch (IOException e) {
            log.error("S3 파일 업로드 중 입출력 오류 발생", e);
            throw new RuntimeException("S3 파일 업로드에 실패했습니다.", e);
        } catch (Exception e) {
            log.error("S3 파일 업로드 통신 실패", e);
            throw new RuntimeException("S3 스토리지 연동 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> uploadFiles(List<MultipartFile> files, String directory) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        List<MultipartFile> validFiles = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                validFiles.add(file);
            }
        }

        if (validFiles.isEmpty()) {
            return List.of();
        }

        // 단일 파일인 경우 현재 스레드에서 즉시 업로드
        if (validFiles.size() == 1) {
            String singleUrl = uploadFile(validFiles.get(0), directory);
            return singleUrl != null ? List.of(singleUrl) : List.of();
        }

        // 다중 파일(사진 + 다수의 3D 오브제)인 경우 병렬 업로드 수행 (원본 순서 100% 보장)
        List<CompletableFuture<String>> futures = validFiles.stream()
                .map(file -> CompletableFuture.supplyAsync(() -> uploadFile(file, directory), UPLOAD_EXECUTOR))
                .toList();

        try {
            return futures.stream()
                    .map(CompletableFuture::join)
                    .filter(Objects::nonNull)
                    .toList();
        } catch (CompletionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("S3 다중 파일 병렬 업로드 중 오류가 발생했습니다.", cause);
        }
    }
}
