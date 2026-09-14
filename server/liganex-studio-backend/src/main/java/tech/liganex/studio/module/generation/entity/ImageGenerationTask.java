package tech.liganex.studio.module.generation.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 图片生成任务（表 {@code image_generation_task}）。
 *
 * <p>图片生成走 OpenAI 兼容协议、同步返回；状态机仍为统一四态，便于复用前端轮询契约。
 */
@Data
@TableName("image_generation_task")
public class ImageGenerationTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerUserId;
    private String provider;
    private String model;
    private String status;
    private String prompt;
    private String size;
    /** 生成结果：可访问地址或 Base64 字节（二者择一）。 */
    private String imageUrl;
    private String imageB64;
    private String errorSummary;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant completedAt;
}
