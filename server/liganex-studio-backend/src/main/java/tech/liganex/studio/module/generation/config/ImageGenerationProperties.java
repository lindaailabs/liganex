package tech.liganex.studio.module.generation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图片生成配置。
 *
 * <p>base-url / api-key / model 一律经环境变量或启动参数注入（ADR-0007：仓库不落真实密钥），
 * 且代码不绑定任何一家厂商或具体模型——具体用哪家模型完全由项目配置（application.yml）决定。
 */
@Data
@ConfigurationProperties(prefix = "liganex.generation.image")
public class ImageGenerationProperties {

    /** 请求未指定供应商时使用的默认供应商标识（OpenAI 兼容协议）。 */
    private String provider = "openai";

    private OpenAi openai = new OpenAi();

    @Data
    public static class OpenAi {

        private String baseUrl;
        private String apiKey;
        /** 模型由项目配置决定，无默认值：未配置时该供应商视为「未配置」。 */
        private String model = "";
        /**
         * OpenAI 兼容端点路径；默认 {@code /v1/images/generations}。对接第三方 OpenAI 兼容
         * 网关、路径不一致时，在配置里覆盖即可，代码无需改动。
         */
        private String generatePath = "/v1/images/generations";

        public boolean configured() {
            return notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
