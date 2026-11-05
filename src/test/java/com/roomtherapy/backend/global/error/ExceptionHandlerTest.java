package com.roomtherapy.backend.global.error;

import com.roomtherapy.backend.global.common.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.ModelAndView;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class ExceptionHandlerTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    @WithMockUser(roles = "ADMIN")
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
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/seller/goods");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(70 * 1024 * 1024);

        ModelAndView mav = handler.handleMaxUploadSizeExceededException(exception, request, response);

        assertThat(mav.getViewName()).isEqualTo("error");
        assertThat(mav.getModel().get("status")).isEqualTo(413);
        assertThat(mav.getModel().get("code")).isEqualTo("FILE_413_01");
        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    @DisplayName("파일 업로드 한도 초과 시 ApiExceptionHandler에서 413 상태코드와 ApiResponse JSON 반환")
    void handleMaxUploadSizeExceededException_api() {
        ApiExceptionHandler handler = new ApiExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/goods/import");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(70 * 1024 * 1024);

        ApiResponse<Void> apiResponse = handler.handleMaxUploadSizeExceededException(exception, request, response);

        assertThat(apiResponse.getStatus()).isEqualTo(413);
        assertThat(apiResponse.getCode()).isEqualTo("FILE_413_01");
        assertThat(response.getStatus()).isEqualTo(413);
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자가 관리자/판매자 페이지 접근 시(403) 시큐리티 필터에서 에러 속성과 함께 /error 포워딩")
    void securityFilter_accessDenied_forwardsToErrorPage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/admin/deliveries"))
                .andExpect(status().isForbidden())
                .andExpect(forwardedUrl("/error"))
                .andExpect(request().attribute("code", "AUTH_403_01"))
                .andExpect(request().attribute("message", containsString("접근 권한이 없습니다")));
    }

    @Test
    @DisplayName("CustomErrorController에 403 에러 전달 시 error.html 뷰 및 친절한 안내 메시지 렌더링")
    void customErrorController_403_rendersFriendlyPage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/error")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr("code", "AUTH_403_01")
                        .requestAttr("message", "접근 권한이 없습니다. 해당 기능은 판매자 또는 관리자 전용입니다."))
                .andExpect(status().isForbidden())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 403))
                .andExpect(model().attribute("code", "AUTH_403_01"))
                .andExpect(content().string(containsString("접근 권한이 없습니다")))
                .andExpect(content().string(containsString("판매자 또는 관리자 전용")));
    }

    @Test
    @DisplayName("비로그인 사용자가 웹 보호 페이지 접근 시 카카오 로그인 페이지로 302 리다이렉트")
    void securityFilter_unauthenticated_redirectsToKakaoOAuth() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/mypage"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/oauth2/authorization/kakao")));
    }

    @Test
    @DisplayName("CustomErrorController에 401 에러 전달 시 친절한 안내 메시지와 카카오 로그인 버튼 노출")
    void customErrorController_401_rendersFriendlyPageWithKakaoButton() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/error")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 401))
                .andExpect(status().isUnauthorized())
                .andExpect(view().name("error"))
                .andExpect(model().attribute("status", 401))
                .andExpect(model().attribute("code", "AUTH_401"))
                .andExpect(content().string(containsString("로그인이 필요한 서비스입니다")))
                .andExpect(content().string(containsString("카카오 로그인")));
    }

    @Test
    @DisplayName("비로그인 사용자가 API 요청 시 일관된 401 JSON ApiResponse 반환")
    void securityFilter_unauthenticatedApi_returnsJson() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/mypage")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("AUTH_401"))
                .andExpect(jsonPath("$.message").value(containsString("로그인이 필요한 서비스입니다")));
    }

    @Test
    @WithMockUser(roles = "USER")
    @DisplayName("일반 사용자가 API 권한 부족 요청 시(403) 일관된 JSON ApiResponse 반환")
    void securityFilter_apiAccessDenied_returnsJson() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockMvc.perform(get("/seller/goods")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("AUTH_403_01"))
                .andExpect(jsonPath("$.message").value(containsString("접근 권한이 없습니다")));
    }
}
