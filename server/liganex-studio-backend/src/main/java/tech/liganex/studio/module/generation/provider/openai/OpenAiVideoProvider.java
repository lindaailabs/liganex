package tech.liganex.studio.module.generation.provider.openai;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.common.SensitiveDataSanitizer;
import tech.liganex.studio.module.generation.config.VideoGenerationProperties;
import tech.liganex.studio.module.generation.provider.VideoGenerationCommand;
import tech.liganex.studio.module.generation.provider.VideoGenerationProvider;
import tech.liganex.studio.module.generation.provider.VideoSubmitResult;
import tech.liganex.studio.module.generation.provider.VideoTaskSnapshot;
import tech.liganex.studio.module.generation.provider.VideoTaskStatus;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * OpenAI 兼容协议的视频生成适配器。
 *
 * <p><b>边界约定</b>：本类是该供应商的唯一边界——请求路径、请求体字段名、响应字段名与状态字面量
 * 全部只在这里出现。具体用哪家模型由配置决定，本类不绑定任何厂商或具体模型名。
 *
 * <p><b>凭证</b>：未配置时 {@link #configured()} 为 false，且 {@link #submit} /
 * {@link #query} 会自行拒绝，保证任何调用路径都不会在无凭证时发出请求（不产生成本）。
 */
@Slf4j
@Component
public class OpenAiVideoProvider implements VideoGenerationProvider {

    public static final String NAME = "openai";

    // 端点路径与字段映射取自配置（默认按 Agnes AI 形态），对接其他第三方 OpenAI 兼容网关时
    // 在 application.yml 的 openai 段覆盖 query-path / result-url-path / image-field / size-as-width-height 即可。
    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final VideoGenerationProperties properties;
    private final RestClient.Builder restClientBuilder;

    private volatile RestClient restClient;

    public OpenAiVideoProvider(
            VideoGenerationProperties properties,
            RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean configured() {
        return properties.getOpenai().configured();
    }

    @Override
    public VideoSubmitResult submit(VideoGenerationCommand command) {
        requireConfigured();
        if (command == null || command.prompt() == null || command.prompt().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "视频生成提示词不能为空");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelFor(command));
        payload.put("prompt", command.prompt());
        // 时长（seconds）在 Agnes 等网关由 num_frames/frame_rate 表达，此处暂不透传，避免未知字段导致 400
        if (notBlank(command.imageUrl())) {
            payload.put(properties.getOpenai().getImageField(), command.imageUrl());
        }
        if (notBlank(command.size())) {
            if (properties.getOpenai().isSizeAsWidthHeight()) {
                int[] wh = parseSize(command.size());
                if (wh != null) {
                    payload.put("width", wh[0]);
                    payload.put("height", wh[1]);
                } else {
                    payload.put("size", command.size());
                }
            } else {
                payload.put("size", command.size());
            }
        }

        Map<String, Object> body = call(() -> client().post()
                .uri(properties.getOpenai().getSubmitPath())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JSON_OBJECT));

        String providerTaskId = firstNonBlank(text(body, "video_id"), text(body, "id"), text(body, "task_id"));
        if (providerTaskId == null) {
            log.warn("video submit response missing task id");
            throw new BizException(ErrorCode.VIDEO_GENERATION_FAILED);
        }
        return new VideoSubmitResult(providerTaskId, modelFor(command), statusOf(text(body, "status")));
    }

    @Override
    public VideoTaskSnapshot query(String providerTaskId) {
        requireConfigured();
        if (!notBlank(providerTaskId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "供应商任务标识不能为空");
        }

        Map<String, Object> body = call(() -> client().get()
                .uri(properties.getOpenai().getQueryPath(), providerTaskId)
                .retrieve()
                .body(JSON_OBJECT));

        VideoTaskStatus status = statusOf(text(body, "status"));
        return switch (status) {
            case SUCCEEDED -> VideoTaskSnapshot.succeeded(resultUrl(body));
            case FAILED -> VideoTaskSnapshot.failed(firstNonBlank(
                    text(body, "error", "message"),
                    text(body, "error", "code"),
                    "视频生成失败"));
            // 供应商侧的进度信息对本层无意义，业务层只需知道「还没到终态」
            case PENDING, RUNNING -> VideoTaskSnapshot.of(status);
        };
    }

    private String modelFor(VideoGenerationCommand command) {
        return notBlank(command.model()) ? command.model() : properties.getOpenai().getName();
    }

    /**
     * 从轮询响应里取出成片地址。
     *
     * <p>地址来自供应商响应体内的字段（由 {@code resultUrlPath} 配置，默认 {@code metadata.url}），
     * 不经过独立的内容端点。该地址通常为公开/短时有效的输出 URL（如 Agnes 的
     * platform-outputs.agnes-ai.space），浏览器可直接播放；若个别网关要求鉴权，则应由后端代理
     * 取回字节（后续任务，前端契约不变）。
     */
    private String resultUrl(Map<String, Object> body) {
        String path = properties.getOpenai().getResultUrlPath();
        if (path == null || path.isBlank()) {
            return null;
        }
        Object cursor = body;
        for (String part : path.split("\\.")) {
            if (cursor instanceof Map<?, ?> map) {
                cursor = map.get(part);
            } else {
                cursor = null;
                break;
            }
        }
        return cursor instanceof String str && !str.isBlank() ? str : null;
    }

    /** 解析 "WxH" 为 [width, height]（忽略非数字或格式不符）。 */
    private static int[] parseSize(String size) {
        if (size == null) {
            return null;
        }
        String[] parts = size.split("[xX]");
        if (parts.length != 2) {
            return null;
        }
        try {
            return new int[]{Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim())};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private RestClient client() {
        if (restClient == null) {
            synchronized (this) {
                if (restClient == null) {
                    restClient = restClientBuilder
                            .baseUrl(properties.getOpenai().getUrl())
                            .requestHeader("Authorization", "Bearer " + properties.getOpenai().getKey())
                            .build();
                }
            }
        }
        return restClient;
    }

    private void requireConfigured() {
        if (!configured()) {
            // 关键：在构造请求之前就失败，保证无凭证时零出站调用
            throw new BizException(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED);
        }
    }

    /**
     * 统一收敛出站异常：对外只给不含供应商原始报文的错误码，
     * 原始报文经脱敏后仅进日志（可能含 key 片段或内部拓扑）。
     */
    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientException ex) {
            log.warn("video generation call failed: {}", SensitiveDataSanitizer.sanitize(ex.getMessage()));
            throw new BizException(ErrorCode.VIDEO_GENERATION_FAILED);
        }
    }

    /** 供应商状态字面量 → 统一状态机；无法识别时明确失败，避免静默进入无限轮询。 */
    private static VideoTaskStatus statusOf(String rawStatus) {
        String normalized = rawStatus == null ? "" : rawStatus.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "queued", "pending", "submitted" -> VideoTaskStatus.PENDING;
            case "in_progress", "running", "processing" -> VideoTaskStatus.RUNNING;
            case "completed", "succeeded", "success" -> VideoTaskStatus.SUCCEEDED;
            case "failed", "error", "cancelled", "canceled" -> VideoTaskStatus.FAILED;
            default -> {
                log.warn("video provider returned unrecognized task status");
                throw new BizException(ErrorCode.VIDEO_GENERATION_FAILED);
            }
        };
    }

    private static String text(Map<String, Object> body, String key) {
        if (body == null) {
            return null;
        }
        Object value = body.get(key);
        return value instanceof String str && !str.isBlank() ? str : null;
    }

    /** 读取嵌套对象字段，如 {@code error.message}。 */
    private static String text(Map<String, Object> body, String parent, String key) {
        if (body == null || !(body.get(parent) instanceof Map<?, ?> nested)) {
            return null;
        }
        Object value = nested.get(key);
        return value instanceof String str && !str.isBlank() ? str : null;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (notBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
