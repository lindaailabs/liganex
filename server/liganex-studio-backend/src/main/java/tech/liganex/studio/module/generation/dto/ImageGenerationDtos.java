package tech.liganex.studio.module.generation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tech.liganex.studio.module.generation.entity.ImageGenerationTask;

import java.time.Instant;

public final class ImageGenerationDtos {

    private ImageGenerationDtos() {
    }

    /**
     * 提交图片生成。
     *
     * <p>{@code provider} 与 {@code model} 可省略（回退到配置的默认值），
     * 使前端画布不必知道后端配了哪家供应商、哪个模型。
     */
    public record SubmitImageGenerationRequest(
            @NotBlank @Size(max = 4000) String prompt,
            @Size(max = 32) String provider,
            @Size(max = 64) String model,
            @Size(max = 32) String size) {
    }

    /**
     * 任务快照。
     *
     * <p>{@code terminal} 直接下发，前端据此停止轮询。
     */
    public record ImageGenerationResponse(
            Long id,
            String provider,
            String model,
            String status,
            boolean terminal,
            String prompt,
            String imageUrl,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {

        public static ImageGenerationResponse from(ImageGenerationTask task) {
            String status = task.getStatus();
            boolean terminal = "SUCCEEDED".equals(status) || "FAILED".equals(status);
            return new ImageGenerationResponse(
                    task.getId(),
                    task.getProvider(),
                    task.getModel(),
                    status,
                    terminal,
                    task.getPrompt(),
                    task.getImageUrl(),
                    task.getErrorSummary(),
                    task.getCreatedAt(),
                    task.getUpdatedAt(),
                    task.getCompletedAt());
        }
    }
}
