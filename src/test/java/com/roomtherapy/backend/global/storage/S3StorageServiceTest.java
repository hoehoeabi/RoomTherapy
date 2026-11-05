package com.roomtherapy.backend.global.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3StorageServiceTest {

    private S3Client s3Client;
    private S3StorageService s3StorageService;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        s3StorageService = new S3StorageService(s3Client);
        ReflectionTestUtils.setField(s3StorageService, "bucketName", "roomtherapy-test-bucket");
        ReflectionTestUtils.setField(s3StorageService, "region", "ap-northeast-2");
    }

    @Test
    @DisplayName("다중 파일(3D 오브제 및 이미지) S3 병렬 업로드 시 완료 시점과 무관하게 입력 순서가 100% 보존된다")
    void uploadFiles_ParallelExecution_PreservesExactInputOrder() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            PutObjectRequest req = invocation.getArgument(0);
            // 첫 번째(.glb) 파일은 가장 늦게 끝나도록 지연시켜도 결과 리스트 순서가 유지되는지 검증
            if (req.key().endsWith(".glb")) {
                Thread.sleep(80);
            } else if (req.key().endsWith(".fbx")) {
                Thread.sleep(40);
            }
            return PutObjectResponse.builder().build();
        });

        MultipartFile firstSlowGlb = new MockMultipartFile(
                "objectModelFiles", "stage1_bed.glb", "model/gltf-binary", "glb-data".getBytes(StandardCharsets.UTF_8));
        MultipartFile secondMidFbx = new MockMultipartFile(
                "objectModelFiles", "stage1_lamp.fbx", "application/octet-stream", "fbx-data".getBytes(StandardCharsets.UTF_8));
        MultipartFile thirdFastObj = new MockMultipartFile(
                "objectModelFiles", "stage1_desk.obj", "text/plain", "obj-data".getBytes(StandardCharsets.UTF_8));

        List<String> urls = s3StorageService.uploadFiles(
                List.of(firstSlowGlb, secondMidFbx, thirdFastObj),
                "goods-3d"
        );

        assertThat(urls).hasSize(3);
        assertThat(urls.get(0)).startsWith("https://roomtherapy-test-bucket.s3.ap-northeast-2.amazonaws.com/goods-3d/").endsWith(".glb");
        assertThat(urls.get(1)).startsWith("https://roomtherapy-test-bucket.s3.ap-northeast-2.amazonaws.com/goods-3d/").endsWith(".fbx");
        assertThat(urls.get(2)).startsWith("https://roomtherapy-test-bucket.s3.ap-northeast-2.amazonaws.com/goods-3d/").endsWith(".obj");
        verify(s3Client, times(3)).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("다중 파일 S3 병렬 업로드 중 허용되지 않는 확장자가 포함되면 예외가 전파된다")
    void uploadFiles_InvalidExtensionInParallel_ThrowsIllegalArgumentException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());

        MultipartFile validPng = new MockMultipartFile(
                "imageFiles", "thumb.png", "image/png", "png-data".getBytes(StandardCharsets.UTF_8));
        MultipartFile maliciousExe = new MockMultipartFile(
                "imageFiles", "malware.exe", "application/octet-stream", "exe-data".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> s3StorageService.uploadFiles(List.of(validPng, maliciousExe), "goods"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 파일 확장자입니다");
    }
}
