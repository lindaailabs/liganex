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

        /** 模型网关地址（配置固定字段 url）。 */
        private String url;
        /** 模型密钥（配置固定字段 key）。 */
        private String key;
        /** 模型名（配置固定字段 name）：如 kling-v1 / doubao-seedance 等任意 OpenAI 兼容模型。无默认值：未配置时该供应商视为「未配置」。 */
        private String name = "";
        /** 提交端点路径。 */
        private String submitPath = "/v1/videos";
        /**
         * 轮询（查询）端点路径；{@code {id}} 会被提交返回的任务标识替换。
         * 默认 {@code /v1/videos/{id}}（Agnes AI 实测路径）。
         */
        private String queryPath = "/v1/videos/{id}";
        /**
         * 查询响应体内「成片地址」的字段路径（点号分隔），用于从轮询响应里取回可播放/下载的 URL。
         * 默认 {@code url}（Agnes AI 实测为响应体顶层 url 字段）；取不到时内部会回退到
         * {@code metadata.url} / {@code data.url}。走独立 /content 端点的供应商可覆盖为对应路径。
         */
        private String resultUrlPath = "url";
        /** 图生视频时，请求体里承载输入图片地址的字段名；默认 {@code image}（Agnes AI 实测）。 */
        private String imageField = "image";

        /**
         * 三要素齐全才算就绪：缺任一项都不应发起出站调用。
         *
         * <p>注：出站超时沿用 Spring Boot 共享 HTTP 客户端设置（{@code spring.http.client.*}），
         * 不在此处单列——适配器不自建 requestFactory，以保持 RestClient.Builder 可被测试替换。
         */
        public boolean configured() {
            return notBlank(url) && notBlank(key) && notBlank(name);
        }

        private static boolean notBlank(String value) {
            return value != null && !value.isBlank();
        }
    }
}
