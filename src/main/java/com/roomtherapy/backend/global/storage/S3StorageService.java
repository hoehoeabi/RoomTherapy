package com.roomtherapy.backend.global.storage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * AWS S3 기반 스토리지 구현체 (운영 환경 prod 프로필에서 동작)
 */
@Slf4j
@Profile("prod")
@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    @Value("${cloud.aws.region.static:ap-northeast-2}")
    private String region;

    @Override
    public String uploadFile(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
            String extension = "";
            int extIdx = originalFilename.lastIndexOf('.');
            if (extIdx >= 0) {
                extension = originalFilename.substring(extIdx);
            }

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

        List<String> uploadedUrls = new ArrayList<>();
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String url = uploadFile(file, directory);
                if (url != null) {
                    uploadedUrls.add(url);
                }
            }
        }
        return uploadedUrls;
    }
}
