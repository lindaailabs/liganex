package tech.liganex.studio.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {
    @Test
    void unexpectedErrorResponseStaysGeneric() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleUnexpected(new IllegalStateException(
                "provider failed with api-key=secret-token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INTERNAL_ERROR.message());
        assertThat(response.getBody().message()).doesNotContain("secret-token");
    }

    /**
     * 「未配置」必须呈现为 503 而非 500：前者告诉用户「缺配置」，后者让人误以为服务故障。
     * 这条断言同时防止「只加错误码、忘改 handler」的回归。
     */
    @Test
    void unconfiguredProviderMapsToServiceUnavailable() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBiz(new BizException(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED.code());
    }

    @Test
    void requestSideProviderErrorsMapToBadRequest() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBiz(new BizException(ErrorCode.VIDEO_PROVIDER_UNSUPPORTED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void upstreamProviderFailureMapsToBadGateway() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBiz(new BizException(ErrorCode.VIDEO_GENERATION_FAILED));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    /** 视频生成新增的错误码不得落回 500（默认分支），否则前端只能看到泛化错误。 */
    @ParameterizedTest
    @EnumSource(value = ErrorCode.class, names = {
            "VIDEO_PROVIDER_NOT_CONFIGURED",
            "VIDEO_PROVIDER_UNSUPPORTED",
            "VIDEO_GENERATION_FAILED",
            "VIDEO_TASK_NOT_FOUND",
    })
    void everyVideoGenerationErrorCodeHasAnExplicitHttpMapping(ErrorCode errorCode) {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        var response = handler.handleBiz(new BizException(errorCode));

        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
