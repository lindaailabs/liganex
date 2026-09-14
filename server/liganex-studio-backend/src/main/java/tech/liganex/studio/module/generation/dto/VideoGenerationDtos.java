package tech.liganex.studio.module.generation.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import tech.liganex.studio.module.generation.entity.VideoGenerationTask;
import tech.liganex.studio.module.generation.provider.VideoTaskStatus;

import java.time.Instant;

public final class VideoGenerationDtos {

    private VideoGenerationDtos() {
    }

    /**
     * 提交视频生成。
     *
     * <p>{@code provider} 与 {@code model} 可省略（回退到配置的默认值），
     * 使前端画布不必知道后端配了哪家供应商。
     */
    public record SubmitVideoGenerationRequest(
            @NotBlank @Size(max = 4000) String prompt,
            @Size(max = 32) String provider,
            @Size(max = 64) String model,
            @Min(1) @Max(60) Integer durationSeconds,
            @Size(max = 32) String size,
            @Size(max = 2048) String imageUrl) {
    }

    /**
     * 任务快照。
     *
     * <p>{@code terminal} 直接下发，前端据此停止轮询，无需自行维护终态集合副本。
     */
    public record VideoGenerationResponse(
            Long id,
            String provider,
            String model,
            String status,
            boolean terminal,
            String prompt,
            String videoUrl,
            String errorMessage,
            Instant createdAt,
            Instant updatedAt,
            Instant completedAt) {

        public static VideoGenerationResponse from(VideoGenerationTask task) {
            VideoTaskStatus status = VideoTaskStatus.valueOf(task.getStatus());
            return new VideoGenerationResponse(
                    task.getId(),
                    task.getProvider(),
                    task.getModel(),
                    status.name(),
                    status.terminal(),
                    task.getPrompt(),
                    task.getVideoUrl(),
                    task.getErrorSummary(),
                    task.getCreatedAt(),
                    task.getUpdatedAt(),
                    task.getCompletedAt());
        }
    }
}
