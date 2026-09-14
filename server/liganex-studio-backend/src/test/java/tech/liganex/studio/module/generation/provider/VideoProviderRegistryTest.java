package tech.liganex.studio.module.generation.provider;

import org.junit.jupiter.api.Test;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.generation.config.VideoGenerationProperties;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VideoProviderRegistryTest {

    @Test
    void resolvesConfiguredDefaultWhenRequestOmitsProvider() {
        VideoProviderRegistry registry = registryWith("openai", "volcengine");

        assertThat(registry.resolve(null).name()).isEqualTo("openai");
    }

    @Test
    void resolvesBlankProviderToDefault() {
        VideoProviderRegistry registry = registryWith("openai", "volcengine");

        assertThat(registry.resolve("  ").name()).isEqualTo("openai");
    }

    @Test
    void resolvesExplicitProviderIgnoringCaseAndPadding() {
        VideoProviderRegistry registry = registryWith("openai", "volcengine");

        assertThat(registry.resolve(" VolcEngine ").name()).isEqualTo("volcengine");
    }

    @Test
    void unknownProviderIsRejectedInsteadOfSilentlyFallingBack() {
        VideoProviderRegistry registry = registryWith("openai", "volcengine");

        // 静默回退会在用户未选择的供应商上产生费用，必须显式报错
        assertThatThrownBy(() -> registry.resolve("gemini"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VIDEO_PROVIDER_UNSUPPORTED);
    }

    @Test
    void findIsLenientForReadPathSoHistoricalTasksStayReadable() {
        VideoProviderRegistry registry = registryWith("openai");

        assertThat(registry.find("removed-provider")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
        assertThat(registry.find("openai")).isPresent();
    }

    @Test
    void duplicateProviderNamesFailFast() {
        List<VideoGenerationProvider> duplicates =
                List.of(new StubProvider("openai"), new StubProvider("openai"));

        assertThatThrownBy(() -> new VideoProviderRegistry(duplicates, propertiesWithDefault("openai")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openai");
    }

    private static VideoProviderRegistry registryWith(String... names) {
        List<VideoGenerationProvider> providers = Arrays.stream(names)
                .map(StubProvider::new)
                .map(VideoGenerationProvider.class::cast)
                .toList();
        return new VideoProviderRegistry(providers, propertiesWithDefault(names[0]));
    }

    private static VideoGenerationProperties propertiesWithDefault(String name) {
        VideoGenerationProperties properties = new VideoGenerationProperties();
        properties.setProvider(name);
        return properties;
    }

    /** 仅用于路由断言的最小实现，不参与任何出站调用。 */
    private record StubProvider(String name) implements VideoGenerationProvider {

        @Override
        public boolean configured() {
            return true;
        }

        @Override
        public VideoSubmitResult submit(VideoGenerationCommand command) {
            throw new UnsupportedOperationException("路由测试不应提交生成请求");
        }

        @Override
        public VideoTaskSnapshot query(String providerTaskId) {
            throw new UnsupportedOperationException("路由测试不应回源查询");
        }
    }
}
