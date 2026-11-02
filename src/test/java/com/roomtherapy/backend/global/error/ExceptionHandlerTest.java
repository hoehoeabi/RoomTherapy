package com.roomtherapy.backend.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ExceptionHandlerTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    @org.springframework.security.test.context.support.WithMockUser(roles = "ADMIN")
    @DisplayName("HTML 요청 중 BusinessException 발생 시 error.html 뷰 및 메시지/상태코드 정상 반환")
    void htmlRequest_businessException_rendersErrorPage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        // 존재하지 않는 주문 조회 시도 -> ORDER_NOT_FOUND (404)
        mockMvc.perform(get("/delivery/confirm/999999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 404))
                .andExpect(model().attribute("code", "ORDER_404_01"))
                .andExpect(model().attribute("message", ErrorCode.ORDER_NOT_FOUND.getMessage()))
                .andExpect(content().string(containsString("존재하지 않는 배송 주문입니다.")))
                .andExpect(content().string(containsString("이전으로")))
                .andExpect(content().string(containsString("홈으로")));
    }

    @Test
    @DisplayName("API 요청(/api/**) 중 BusinessException 발생 시 ApiResponse JSON 에러 규격 반환")
    void apiRequest_businessException_returnsJson() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // 존재하지 않는 굿즈 ID로 유니티 인증코드 발급 요청 -> GOODS_NOT_FOUND (404 JSON ApiResponse)
        mockMvc.perform(post("/api/unity/goods/999999/issue-code")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("GOODS_404_01"))
                .andExpect(jsonPath("$.message").value(ErrorCode.GOODS_NOT_FOUND.getMessage()));
    }

    @Test
    @DisplayName("파일 업로드 한도 초과 시 WebExceptionHandler에서 413 상태코드와 error.html 뷰 반환")
    void handleMaxUploadSizeExceededException_web() {
        WebExceptionHandler handler = new WebExceptionHandler();
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRequestURI("/seller/goods");
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.web.multipart.MaxUploadSizeExceededException exception = new org.springframework.web.multipart.MaxUploadSizeExceededException(70 * 1024 * 1024);

        org.springframework.web.servlet.ModelAndView mav = handler.handleMaxUploadSizeExceededException(exception, request, response);

        org.assertj.core.api.Assertions.assertThat(mav.getViewName()).isEqualTo("error");
        org.assertj.core.api.Assertions.assertThat(mav.getModel().get("status")).isEqualTo(413);
        org.assertj.core.api.Assertions.assertThat(mav.getModel().get("code")).isEqualTo("FILE_413_01");
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    @DisplayName("파일 업로드 한도 초과 시 ApiExceptionHandler에서 413 상태코드와 ApiResponse JSON 반환")
    void handleMaxUploadSizeExceededException_api() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setRequestURI("/api/goods/import");
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.web.multipart.MaxUploadSizeExceededException exception = new org.springframework.web.multipart.MaxUploadSizeExceededException(70 * 1024 * 1024);

        com.roomtherapy.backend.global.common.ApiResponse<Void> apiResponse = handler.handleMaxUploadSizeExceededException(exception, request, response);

        org.assertj.core.api.Assertions.assertThat(apiResponse.getStatus()).isEqualTo(413);
        org.assertj.core.api.Assertions.assertThat(apiResponse.getCode()).isEqualTo("FILE_413_01");
        org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(413);
    }
}
