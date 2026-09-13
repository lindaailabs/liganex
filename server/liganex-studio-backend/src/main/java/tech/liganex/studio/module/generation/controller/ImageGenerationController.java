package tech.liganex.studio.module.generation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tech.liganex.studio.common.ApiResponse;
import tech.liganex.studio.module.generation.dto.ImageGenerationDtos.ImageGenerationResponse;
import tech.liganex.studio.module.generation.dto.ImageGenerationDtos.SubmitImageGenerationRequest;
import tech.liganex.studio.module.generation.service.ImageGenerationService;

import java.util.List;

/**
 * 图片生成接口（B 端客户业务系统 / AI 创作）。
 *
 * <p>owner 一律取自认证上下文，不接受请求体传入，避免越权。
 */
@RestController
@RequestMapping("/api/v1/image-generations")
@RequiredArgsConstructor
public class ImageGenerationController {

    private final ImageGenerationService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ImageGenerationResponse> submit(
            @AuthenticationPrincipal Long ownerUserId,
            @Valid @RequestBody SubmitImageGenerationRequest request) {
        return ApiResponse.ok(service.submit(ownerUserId, request));
    }

    @GetMapping
    public ApiResponse<List<ImageGenerationResponse>> list(
            @AuthenticationPrincipal Long ownerUserId,
            @RequestParam(required = false) Integer limit) {
        return ApiResponse.ok(service.list(ownerUserId, limit));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<ImageGenerationResponse> get(
            @AuthenticationPrincipal Long ownerUserId,
            @PathVariable Long taskId) {
        return ApiResponse.ok(service.get(ownerUserId, taskId));
    }
}
