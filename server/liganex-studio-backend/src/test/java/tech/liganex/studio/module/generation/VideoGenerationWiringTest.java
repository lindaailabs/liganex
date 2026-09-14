package tech.liganex.studio.module.generation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import tech.liganex.studio.module.generation.config.VideoGenerationProperties;
import tech.liganex.studio.module.generation.provider.openai.OpenAiSoraVideoProvider;

/**
 * 装配冒烟测试：{@link OpenAiSoraVideoProvider} 以构造器注入 {@link RestClient.Builder}，
 * 而 Spring Boot 4 把 RestClient 自动配置拆进了独立的 spring-boot-starter-restclient
 * （与 Flyway 同理）。仅靠 spring-boot-starter-web 不会有该 bean，应用会在启动时
 * 以 NoSuchBeanDefinitionException 失败。
 *
 * <p>该缺陷不会被常规单测发现——测试里都手工 {@code RestClient.builder()} 自建实例，
 * 绕过了容器装配。故此处用最小上下文把这条装配链固化下来：一旦 starter 依赖被误删，
 * 该用例立即失败，而不是等到运行时才炸。
 */
class VideoGenerationWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RestClientAutoConfiguration.class))
            .withBean(VideoGenerationProperties.class)
            .withBean(OpenAiSoraVideoProvider.class);

    @Test
    void restClientBuilderIsAutoConfiguredSoProviderCanBeWired() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RestClient.Builder.class)).isNotNull();
            assertThat(context).hasSingleBean(OpenAiSoraVideoProvider.class);
        });
    }
}
