package tech.liganex.studio.module.generation.provider.openai;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.generation.config.VideoGenerationProperties;
import tech.liganex.studio.module.generation.provider.VideoGenerationCommand;
import tech.liganex.studio.module.generation.provider.VideoSubmitResult;
import tech.liganex.studio.module.generation.provider.VideoTaskSnapshot;
import tech.liganex.studio.module.generation.provider.VideoTaskStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Sora 适配器契约测试。
 *
 * <p>HTTP 层用 {@link MockRestServiceServer} 打桩，全程无真实网络：既验证**发出的请求体**，
 * 也验证**四种供应商状态的归一化**，并把「未配置时零出站调用」钉成断言。
 */
class OpenAiSoraVideoProviderTest {

    private static final String BASE_URL = "https://api.openai.test";
    private static final String SUBMIT_URL = BASE_URL + "/v1/videos";
    private static final String QUERY_URL = BASE_URL + "/v1/videos/video_123";

    private VideoGenerationProperties properties;
    private MockRestServiceServer server;
    private OpenAiSoraVideoProvider provider;

    @BeforeEach
    void setUp() {
        properties = new VideoGenerationProperties();
        properties.getOpenai().setBaseUrl(BASE_URL);
        properties.getOpenai().setApiKey("sk-test-key");
        properties.getOpenai().setModel("sora-2");

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new OpenAiSoraVideoProvider(properties, builder);
    }

    @Test
    void submitSendsDocumentedFieldsAndReturnsProviderTaskId() {
        server.expect(requestTo(SUBMIT_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("sora-2"))
                .andExpect(jsonPath("$.prompt").value("一只猫在草地上奔跑"))
                .andExpect(jsonPath("$.seconds").value("4"))
                .andExpect(jsonPath("$.size").value("720x1280"))
                .andRespond(withSuccess(
                        "{\"id\":\"video_123\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        VideoSubmitResult result = provider.submit(
                new VideoGenerationCommand("一只猫在草地上奔跑", null, 4, "720x1280", null));

        assertThat(result.providerTaskId()).isEqualTo("video_123");
        // 适配器回退到自身默认模型后回报，供服务层落库为参数快照
        assertThat(result.model()).isEqualTo("sora-2");
        assertThat(result.status()).isEqualTo(VideoTaskStatus.PENDING);
        server.verify();
    }

    @Test
    void submitOmitsOptionalFieldsWhenNotProvided() {
        server.expect(requestTo(SUBMIT_URL))
                .andExpect(jsonPath("$.prompt").value("海边日落"))
                .andExpect(jsonPath("$.seconds").doesNotExist())
                .andExpect(jsonPath("$.size").doesNotExist())
                .andExpect(jsonPath("$.input_reference").doesNotExist())
                .andRespond(withSuccess(
                        "{\"id\":\"video_456\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        provider.submit(new VideoGenerationCommand("海边日落", "sora-2-pro", null, null, null));

        server.verify();
    }

    @Test
    void submitPassesExplicitModelThrough() {
        server.expect(requestTo(SUBMIT_URL))
                .andExpect(jsonPath("$.model").value("sora-2-pro"))
                .andRespond(withSuccess(
                        "{\"id\":\"video_789\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        VideoSubmitResult result = provider.submit(
                new VideoGenerationCommand("prompt", "sora-2-pro", null, null, null));

        assertThat(result.model()).isEqualTo("sora-2-pro");
        server.verify();
    }

    @Test
    void submitMapsImageReferenceForImageToVideo() {
        server.expect(requestTo(SUBMIT_URL))
                .andExpect(jsonPath("$.input_reference").value("https://cdn.example/product.png"))
                .andRespond(withSuccess(
                        "{\"id\":\"video_abc\",\"status\":\"queued\"}", MediaType.APPLICATION_JSON));

        provider.submit(new VideoGenerationCommand(
                "让商品旋转", null, null, null, "https://cdn.example/product.png"));

        server.verify();
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "queued,PENDING",
            "in_progress,RUNNING",
            "completed,SUCCEEDED",
            "failed,FAILED",
    })
    void mapsProviderStatusOntoUnifiedStateMachine(String providerStatus, VideoTaskStatus expected) {
        server.expect(requestTo(QUERY_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"id\":\"video_123\",\"status\":\"" + providerStatus + "\"}",
                        MediaType.APPLICATION_JSON));

        VideoTaskSnapshot snapshot = provider.query("video_123");

        assertThat(snapshot.status()).isEqualTo(expected);
        assertThat(snapshot.status().terminal()).isEqualTo(expected.terminal());
        server.verify();
    }

    @Test
    void completedTaskExposesContentLocator() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(
                        "{\"id\":\"video_123\",\"status\":\"completed\"}", MediaType.APPLICATION_JSON));

        VideoTaskSnapshot snapshot = provider.query("video_123");

        assertThat(snapshot.videoUrl()).isEqualTo(BASE_URL + "/v1/videos/video_123/content");
        assertThat(snapshot.errorMessage()).isNull();
        server.verify();
    }

    @Test
    void failedTaskSurfacesProviderErrorMessage() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(
                        "{\"id\":\"video_123\",\"status\":\"failed\","
                                + "\"error\":{\"code\":\"moderation_blocked\",\"message\":\"内容不合规\"}}",
                        MediaType.APPLICATION_JSON));

        VideoTaskSnapshot snapshot = provider.query("video_123");

        assertThat(snapshot.status()).isEqualTo(VideoTaskStatus.FAILED);
        assertThat(snapshot.errorMessage()).isEqualTo("内容不合规");
        assertThat(snapshot.videoUrl()).isNull();
        server.verify();
    }

    @Test
    void unrecognizedStatusFailsLoudlyInsteadOfPollingForever() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withSuccess(
                        "{\"id\":\"video_123\",\"status\":\"who_knows\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.query("video_123"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VIDEO_GENERATION_FAILED);
    }

    @Test
    void upstreamFailureIsSurfacedAsProviderErrorWithoutLeakingBody() {
        server.expect(requestTo(QUERY_URL))
                .andRespond(withServerError().body("upstream detail api-key=sk-leak"));

        assertThatThrownBy(() -> provider.query("video_123"))
                .isInstanceOf(BizException.class)
                .hasMessage(ErrorCode.VIDEO_GENERATION_FAILED.message());
    }

    @Test
    void unconfiguredProviderRejectsSubmitWithoutAnyHttpCall() {
        properties.getOpenai().setApiKey("");
        assertThat(provider.configured()).isFalse();

        // 任何请求都会命中这条 never 期望，从而把「无出站调用」钉死为断言
        server.expect(ExpectedCount.never(), requestTo(SUBMIT_URL));

        assertThatThrownBy(() -> provider.submit(new VideoGenerationCommand("prompt", null, null, null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED);

        server.verify();
    }

    @Test
    void unconfiguredProviderRejectsQueryWithoutAnyHttpCall() {
        properties.getOpenai().setApiKey("   ");

        server.expect(ExpectedCount.never(), requestTo(QUERY_URL));

        assertThatThrownBy(() -> provider.query("video_123"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED);

        server.verify();
    }

    @Test
    void missingBaseUrlAlsoCountsAsUnconfigured() {
        properties.getOpenai().setBaseUrl(null);

        assertThat(provider.configured()).isFalse();
    }
}
