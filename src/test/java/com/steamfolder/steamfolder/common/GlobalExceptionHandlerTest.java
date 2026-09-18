package com.steamfolder.steamfolder.common;

import com.steamfolder.steamfolder.common.dto.ApiResponse;
import com.steamfolder.steamfolder.common.exception.CustomException;
import com.steamfolder.steamfolder.common.exception.ErrorCode;
import com.steamfolder.steamfolder.common.exception.GlobalExceptionHandler;
import com.steamfolder.steamfolder.common.monitoring.DiscordAlertService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestExceptionController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.TestExceptionController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DiscordAlertService discordAlertService;

    @RestController
    @Component
    static class TestExceptionController {

        @GetMapping("/test/success")
        public ApiResponse<String> success() {
            return ApiResponse.success("Hello SteamFolder");
        }

        @GetMapping("/test/custom-exception")
        public void customException() {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        @GetMapping("/test/unhandled-exception")
        public void unhandledException() {
            throw new RuntimeException("Unexpected error");
        }
    }

    @Test
    @DisplayName("표준 ApiResponse 성공 응답 검증")
    void testSuccessResponse() throws Exception {
        mockMvc.perform(get("/test/success"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("Hello SteamFolder"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    @DisplayName("CustomException 발생 시 표준 ErrorResponse 및 404 상태코드 검증")
    void testCustomExceptionHandling() throws Exception {
        mockMvc.perform(get("/test/custom-exception"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("U001"))
                .andExpect(jsonPath("$.message").value("존재하지 않는 유저입니다."))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(discordAlertService, never()).notifyUnhandledError(any(), any(), any());
    }

    @Test
    @DisplayName("처리되지 않은 Exception 발생 시 500 상태코드 및 C004 코드 반환 검증")
    void testUnhandledExceptionHandling() throws Exception {
        mockMvc.perform(get("/test/unhandled-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("C004"))
                .andExpect(jsonPath("$.message").value("서버 내부 오류가 발생했습니다."));

        verify(discordAlertService).notifyUnhandledError(
                any(RuntimeException.class),
                eq("GET"),
                eq("/test/unhandled-exception")
        );
    }
}
