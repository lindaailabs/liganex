package tech.liganex.studio.module.generation.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.auth.security.JwtTokenProvider;
import tech.liganex.studio.module.generation.dto.VideoGenerationDtos.VideoGenerationResponse;
import tech.liganex.studio.module.generation.service.VideoGenerationService;
import tech.liganex.studio.support.AuthenticationPrincipalTestConfig;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(AuthenticationPrincipalTestConfig.class)
@WebMvcTest(VideoGenerationController.class)
class VideoGenerationControllerTest {

    private static final Long OWNER = 42L;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private VideoGenerationService service;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(OWNER, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void submitUsesAuthenticatedPrincipalAsOwner() throws Exception {
        when(service.submit(eq(OWNER), any())).thenReturn(new VideoGenerationResponse(
                7L, "openai", "sora-2", "PENDING", false, "一只猫", null, null,
                Instant.now(), Instant.now(), null));

        mvc.perform(post("/api/v1/video-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"一只猫\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.data.terminal").value(false));

        verify(service).submit(eq(OWNER), any());
    }

    @Test
    void blankPromptIsRejectedByValidation() throws Exception {
        mvc.perform(post("/api/v1/video-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.BAD_REQUEST.code()));
    }

    /** 未配置供应商必须呈现为 503（服务未就绪），而非 500，使用户能区分「没配」和「坏了」。 */
    @Test
    void unconfiguredProviderIsReportedAsServiceUnavailable() throws Exception {
        when(service.submit(eq(OWNER), any()))
                .thenThrow(new BizException(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED));

        mvc.perform(post("/api/v1/video-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"一只猫\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED.code()));
    }

    @Test
    void unknownProviderIsReportedAsBadRequest() throws Exception {
        when(service.submit(eq(OWNER), any()))
                .thenThrow(new BizException(ErrorCode.VIDEO_PROVIDER_UNSUPPORTED));

        mvc.perform(post("/api/v1/video-generations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"一只猫\",\"provider\":\"gemini\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(ErrorCode.VIDEO_PROVIDER_UNSUPPORTED.code()));
    }

    @Test
    void upstreamFailureIsReportedAsBadGateway() throws Exception {
        when(service.get(eq(OWNER), eq(7L)))
                .thenThrow(new BizException(ErrorCode.VIDEO_GENERATION_FAILED));

        mvc.perform(get("/api/v1/video-generations/7"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value(ErrorCode.VIDEO_GENERATION_FAILED.code()));
    }

    @Test
    void foreignTaskLooksLikeMissingTask() throws Exception {
        when(service.get(OWNER, 99L)).thenThrow(new BizException(ErrorCode.VIDEO_TASK_NOT_FOUND));

        mvc.perform(get("/api/v1/video-generations/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.VIDEO_TASK_NOT_FOUND.code()));
    }
}
