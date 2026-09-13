package tech.liganex.studio.module.generation.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 视频生成配置。
 *
 * <p>密钥无默认值：未注入时对应供应商 {@code configured()==false}，服务层据此在发起任何出站调用
 * **之前**拒绝请求（ADR-0007：敏感项一律经环境变量/启动参数注入，仓库不落真实密钥）。
 */
@Data
@ConfigurationProperties(prefix = "liganex.generation.video")
public class VideoGenerationProperties {

    /** 请求未指定供应商时使用的默认供应商标识。 */
    private String provider = "openai";

    private OpenAi openai = new OpenAi();

    @Data
    public static class OpenAi {

        private String baseUrl;
        private String apiKey;
        /** 模型由项目配置决定，无默认值：未配置时该供应商视为「未配置」。 */
        private String model = "";

        /**
         * 三要素齐全才算就绪：缺任一项都不应发起出站调用。
         *
         * <p>注：出站超时沿用 Spring Boot 共享 HTTP 客户端设置（{@code spring.http.client.*}），
         * 不在此处单列——适配器不自建 requestFactory，以保持 RestClient.Builder 可被测试替换。
         */
        public boolean configured() {
            return notBlank(baseUrl) && notBlank(apiKey) && notBlank(model);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
