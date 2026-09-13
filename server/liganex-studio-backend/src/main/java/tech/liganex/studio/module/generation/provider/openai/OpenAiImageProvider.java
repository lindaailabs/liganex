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
import tech.liganex.studio.module.generation.config.ImageGenerationProperties;
import tech.liganex.studio.module.generation.provider.ImageGenerationCommand;
import tech.liganex.studio.module.generation.provider.ImageGenerationProvider;
import tech.liganex.studio.module.generation.provider.ImageGenerationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * OpenAI 兼容协议的图片生成适配器。
 *
 * <p><b>边界约定</b>：与视频适配器一致——请求路径、字段名与鉴权头只在本类出现。
 * 具体用哪家模型由配置决定，本类不绑定任何厂商或具体模型名；未配置时
 * {@link #configured()} 为 false，{@link #generate} 在构造请求前即拒绝（不产生成本）。
 */
@Slf4j
@Component
public class OpenAiImageProvider implements ImageGenerationProvider {

    public static final String NAME = "openai";

    private static final String GENERATE_PATH = "/v1/images/generations";

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_OBJECT =
            new ParameterizedTypeReference<>() {
            };

    private final ImageGenerationProperties properties;
    private final RestClient.Builder restClientBuilder;

    private volatile RestClient restClient;

    public OpenAiImageProvider(
            ImageGenerationProperties properties,
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
    public ImageGenerationResult generate(ImageGenerationCommand command) {
        requireConfigured();
        if (command == null || command.prompt() == null || command.prompt().isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "图片生成提示词不能为空");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", modelFor(command));
        payload.put("prompt", command.prompt());
        if (notBlank(command.size())) {
            payload.put("size", command.size());
        }
        payload.put("n", 1);

        Map<String, Object> body = call(() -> client().post()
                .uri(GENERATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(JSON_OBJECT));

        String url = textAt(body, "url");
        String b64 = textAt(body, "b64_json");
        if (url == null && b64 == null) {
            log.warn("image generation response missing url/b64_json");
            throw new BizException(ErrorCode.IMAGE_GENERATION_FAILED);
        }
        return new ImageGenerationResult(url, b64, modelFor(command));
    }

    private String modelFor(ImageGenerationCommand command) {
        return notBlank(command.model()) ? command.model() : properties.getOpenai().getModel();
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
            throw new BizException(ErrorCode.IMAGE_PROVIDER_NOT_CONFIGURED);
        }
    }

    private <T> T call(Supplier<T> action) {
        try {
            return action.get();
        } catch (RestClientException ex) {
            log.warn("image generation call failed: {}",
                    SensitiveDataSanitizer.sanitize(ex.getMessage()));
            throw new BizException(ErrorCode.IMAGE_GENERATION_FAILED);
        }
    }

    /** 读取 {@code data[0].url} 或 {@code data[0].b64_json}。 */
    private static String textAt(Map<String, Object> body, String key) {
        if (body == null || !(body.get("data") instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> item)) {
            return null;
        }
        Object value = item.get(key);
        return value instanceof String str && !str.isBlank() ? str : null;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
