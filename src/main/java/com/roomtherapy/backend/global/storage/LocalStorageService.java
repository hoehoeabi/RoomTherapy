package com.roomtherapy.backend.global.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class LocalStorageService implements StorageService {

    private final Path rootLocation = Paths.get("uploads").toAbsolutePath().normalize();

    public LocalStorageService() {
        try {
            Files.createDirectories(rootLocation);
        } catch (IOException e) {
            log.error("업로드 기본 디렉터리 생성 실패: {}", rootLocation, e);
        }
    }

    @Override
    public String uploadFile(MultipartFile file, String directory) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        try {
            Path targetDir = rootLocation.resolve(directory != null ? directory : "").normalize();
            Files.createDirectories(targetDir);

            String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.bin";
            String extension = "";
            int extIdx = originalFilename.lastIndexOf('.');
            if (extIdx >= 0) {
                extension = originalFilename.substring(extIdx);
            }

            String savedFilename = UUID.randomUUID().toString().replace("-", "") + extension;
            Path destinationFile = targetDir.resolve(savedFilename).normalize();

            Files.copy(file.getInputStream(), destinationFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("로컬 파일 업로드 성공: {} -> {}", originalFilename, destinationFile);

            String subPath = (directory != null && !directory.isBlank()) ? directory + "/" : "";
            return "/uploads/" + subPath + savedFilename;
        } catch (IOException e) {
            log.error("파일 저장 중 입출력 오류 발생", e);
            throw new RuntimeException("파일 업로드에 실패했습니다.", e);
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
