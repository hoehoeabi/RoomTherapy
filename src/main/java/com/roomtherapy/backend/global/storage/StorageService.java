package com.roomtherapy.backend.global.storage;

import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface StorageService {

    /**
     * 단일 파일 업로드 및 접근 가능한 URL 반환
     */
    String uploadFile(MultipartFile file, String directory);

    /**
     * 다중 파일 일괄 업로드 및 URL 목록 반환
     */
    List<String> uploadFiles(List<MultipartFile> files, String directory);
}
