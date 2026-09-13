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

    private static final String SUBMIT_PATH = "/v1/videos";
    private static final String QUERY_PATH = "/v1/videos/{id}";
    /** 取回成片字节的端点（需鉴权），作为资产定位符对外暴露。 */
    private static final String CONTENT_PATH = "/v1/videos/{id}/content";

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
        if (command.durationSeconds() != null) {
            payload.put("seconds", String.valueOf(command.durationSeconds()));
        }
        if (notBlank(command.size())) {
            payload.put("size", command.size());
        }
        if (notBlank(command.imageUrl())) {
            payload.put("input_reference", command.imageUrl());
        }

        Map<String, Object> body = call(() -> client().post()
                .uri(SUBMIT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JSON_OBJECT));

        String providerTaskId = text(body, "id");
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
                .uri(QUERY_PATH, providerTaskId)
                .retrieve()
                .body(JSON_OBJECT));

        VideoTaskStatus status = statusOf(text(body, "status"));
        return switch (status) {
            case SUCCEEDED -> VideoTaskSnapshot.succeeded(contentUrl(providerTaskId));
            case FAILED -> VideoTaskSnapshot.failed(firstNonBlank(
                    text(body, "error", "message"),
                    text(body, "error", "code"),
                    "视频生成失败"));
            // 供应商侧的进度信息对本层无意义，业务层只需知道「还没到终态」
            case PENDING, RUNNING -> VideoTaskSnapshot.of(status);
        };
    }

    private String modelFor(VideoGenerationCommand command) {
        return notBlank(command.model()) ? command.model() : properties.getOpenai().getModel();
    }

    /**
     * 成片取回地址（绝对 URL）。
     *
     * <p>注：该端点需带鉴权头，浏览器不能直接播放。当前切片把它作为「资产定位符」暴露，
     * 由后端代理取回字节是后续任务——届时前端契约不变，只把 src 换成本服务的代理端点。
     */
    private String contentUrl(String providerTaskId) {
        String base = properties.getOpenai().getBaseUrl();
        String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        return normalizedBase + CONTENT_PATH.replace("{id}", providerTaskId);
    }

    private RestClient client() {
        if (restClient == null) {
            synchronized (this) {
                if (restClient == null) {
                    restClient = restClientBuilder
                            .baseUrl(properties.getOpenai().getBaseUrl())
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
