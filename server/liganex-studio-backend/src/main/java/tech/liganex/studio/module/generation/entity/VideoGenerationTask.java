package tech.liganex.studio.module.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 视频生成任务（表 {@code video_generation_task}）。
 *
 * <p>{@code status} 存统一状态机的字面量；供应商自有状态不落库（在适配器内归一化）。
 * 仅 {@code providerTaskId} 是与供应商耦合的字段，用于回源轮询。
 */
@Data
@TableName("video_generation_task")
public class VideoGenerationTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String provider;
    private String model;
    private String status;
    private String prompt;
    private String sourceImageUrl;
    private Integer durationSeconds;
    private String size;
    private String providerTaskId;
    private String videoUrl;
    private String errorSummary;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
