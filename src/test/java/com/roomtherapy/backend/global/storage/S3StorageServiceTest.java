package com.roomtherapy.backend.global.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class S3StorageServiceTest {

    @Mock
    private S3Client s3Client;

    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        s3StorageService = new S3StorageService(s3Client);
        ReflectionTestUtils.setField(s3StorageService, "bucketName", "test-bucket");
        ReflectionTestUtils.setField(s3StorageService, "region", "ap-northeast-2");
    }

    @Test
    @DisplayName("S3 단일 파일 업로드 시 S3Client.putObject 호출 및 올바른 S3 URL 반환 검증")
    void uploadFile_success() {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "imageFiles",
                "sample.png",
                "image/png",
                "dummy image content".getBytes()
        );

        // when
        String url = s3StorageService.uploadFile(file, "goods");

        // then
        assertThat(url).isNotNull();
        assertThat(url).startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/goods/");
        assertThat(url).endsWith(".png");

        ArgumentCaptor<PutObjectRequest> requestCaptor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(requestCaptor.capture(), any(RequestBody.class));

        PutObjectRequest captured = requestCaptor.getValue();
        assertThat(captured.bucket()).isEqualTo("test-bucket");
        assertThat(captured.key()).startsWith("goods/");
        assertThat(captured.contentType()).isEqualTo("image/png");
    }

    @Test
    @DisplayName("S3 다중 파일 업로드 시 모든 파일이 각각 S3 URL 목록으로 변환되어 반환")
    void uploadFiles_success() {
        // given
        MockMultipartFile file1 = new MockMultipartFile("f1", "img1.jpg", "image/jpeg", "data1".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("f2", "img2.png", "image/png", "data2".getBytes());

        // when
        List<String> urls = s3StorageService.uploadFiles(List.of(file1, file2), "goods");

        // then
        assertThat(urls).hasSize(2);
        assertThat(urls.get(0)).contains("https://test-bucket.s3.ap-northeast-2.amazonaws.com/goods/");
        assertThat(urls.get(1)).contains("https://test-bucket.s3.ap-northeast-2.amazonaws.com/goods/");
    }
}
