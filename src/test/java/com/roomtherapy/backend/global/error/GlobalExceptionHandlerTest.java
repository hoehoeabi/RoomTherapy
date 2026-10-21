package com.roomtherapy.backend.global.error;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class GlobalExceptionHandlerTest {

    @Autowired
    private WebApplicationContext context;

    @Test
    @DisplayName("HTML 요청 중 BusinessException 발생 시 error.html 뷰 및 메시지/상태코드 정상 반환")
    void htmlRequest_businessException_rendersErrorPage() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

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
    @DisplayName("API 요청(/api/**) 중 BusinessException 발생 시 JSON 에러 규격 반환")
    void apiRequest_businessException_returnsJson() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

        // 존재하지 않는 굿즈 ID로 유니티 인증코드 발급 요청 -> GOODS_NOT_FOUND (404 JSON)
        mockMvc.perform(get("/api/unity/goods/999999/issue-code")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }
}
