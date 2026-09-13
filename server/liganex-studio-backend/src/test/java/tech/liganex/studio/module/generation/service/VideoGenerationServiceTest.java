package tech.liganex.studio.module.generation.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.generation.dto.VideoGenerationDtos.SubmitVideoGenerationRequest;
import tech.liganex.studio.module.generation.dto.VideoGenerationDtos.VideoGenerationResponse;
import tech.liganex.studio.module.generation.entity.VideoGenerationTask;
import tech.liganex.studio.module.generation.mapper.VideoGenerationTaskMapper;
import tech.liganex.studio.module.generation.provider.VideoGenerationCommand;
import tech.liganex.studio.module.generation.provider.VideoGenerationProvider;
import tech.liganex.studio.module.generation.provider.VideoProviderRegistry;
import tech.liganex.studio.module.generation.provider.VideoSubmitResult;
import tech.liganex.studio.module.generation.provider.VideoTaskSnapshot;
import tech.liganex.studio.module.generation.provider.VideoTaskStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VideoGenerationServiceTest {

    private static final Long OWNER = 42L;
    private static final SubmitVideoGenerationRequest REQUEST =
            new SubmitVideoGenerationRequest("一只猫在草地上奔跑", null, null, 4, "720x1280", null);

    @Mock
    private VideoGenerationTaskMapper taskMapper;

    @Mock
    private VideoProviderRegistry providerRegistry;

    @Mock
    private VideoGenerationProvider provider;

    @InjectMocks
    private VideoGenerationService service;

    @Test
    void unconfiguredProviderIsRejectedBeforeAnyProviderCallOrPersistence() {
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(provider.configured()).thenReturn(false);

        assertThatThrownBy(() -> service.submit(OWNER, REQUEST))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED);

        // 「不产生成本」的核心语义：未配置时既不调供应商，也不落任何任务
        verify(provider, never()).submit(any(VideoGenerationCommand.class));
        verifyNoInteractions(taskMapper);
    }

    @Test
    void unknownProviderIsRejectedBeforeAnyProviderCall() {
        when(providerRegistry.resolve("gemini"))
                .thenThrow(new BizException(ErrorCode.VIDEO_PROVIDER_UNSUPPORTED));

        assertThatThrownBy(() -> service.submit(OWNER, new SubmitVideoGenerationRequest(
                "prompt", "gemini", null, null, null, null)))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VIDEO_PROVIDER_UNSUPPORTED);

        verifyNoInteractions(taskMapper);
    }

    @Test
    void submitPersistsTaskUnderAuthenticatedOwner() {
        when(providerRegistry.resolve(null)).thenReturn(provider);
        when(provider.configured()).thenReturn(true);
        when(provider.name()).thenReturn("openai");
        when(provider.submit(any(VideoGenerationCommand.class)))
                .thenReturn(new VideoSubmitResult("video_123", "sora-2", VideoTaskStatus.PENDING));

        VideoGenerationResponse response = service.submit(OWNER, REQUEST);

        ArgumentCaptor<VideoGenerationTask> persisted = ArgumentCaptor.forClass(VideoGenerationTask.class);
        verify(taskMapper).insert(persisted.capture());
        VideoGenerationTask task = persisted.getValue();

        // owner 只能来自认证上下文，不能由请求体传入
        assertThat(task.getOwnerUserId()).isEqualTo(OWNER);
        assertThat(task.getProvider()).isEqualTo("openai");
        assertThat(task.getModel()).isEqualTo("sora-2");
        assertThat(task.getProviderTaskId()).isEqualTo("video_123");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getPrompt()).isEqualTo("一只猫在草地上奔跑");
        assertThat(task.getDurationSeconds()).isEqualTo(4);
        assertThat(task.getSize()).isEqualTo("720x1280");
        // 非终态不应写 completedAt，否则前端会误判任务已结束
        assertThat(task.getCompletedAt()).isNull();

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.terminal()).isFalse();
    }

    @Test
    void foreignOrMissingTaskLooksLikeMissingTask() {
        when(taskMapper.selectOwnedById(OWNER, 99L)).thenReturn(null);

        assertThatThrownBy(() -> service.get(OWNER, 99L))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(ErrorCode.VIDEO_TASK_NOT_FOUND);

        // 越权与不存在同构：不应因此去回源供应商
        verifyNoInteractions(providerRegistry);
    }

    @Test
    void terminalTaskIsServedFromStorageWithoutCallingProvider() {
        when(taskMapper.selectOwnedById(OWNER, 7L)).thenReturn(task("SUCCEEDED", "video_123"));

        VideoGenerationResponse response = service.get(OWNER, 7L);

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.terminal()).isTrue();
        // 终态不回源：避免读取放大上游调用
        verify(providerRegistry, never()).find(anyString());
        verify(taskMapper, never()).updateById(any(VideoGenerationTask.class));
    }

    @Test
    void nonTerminalTaskIsRefreshedFromProviderAndPersisted() {
        VideoGenerationTask task = task("RUNNING", "video_123");
        when(taskMapper.selectOwnedById(OWNER, 7L)).thenReturn(task);
        when(providerRegistry.find("openai")).thenReturn(Optional.of(provider));
        when(provider.configured()).thenReturn(true);
        when(provider.query("video_123"))
                .thenReturn(VideoTaskSnapshot.succeeded("https://cdn.example/video_123.mp4"));

        VideoGenerationResponse response = service.get(OWNER, 7L);

        assertThat(response.status()).isEqualTo("SUCCEEDED");
        assertThat(response.terminal()).isTrue();
        assertThat(response.videoUrl()).isEqualTo("https://cdn.example/video_123.mp4");
        assertThat(task.getCompletedAt()).isNotNull();
        verify(taskMapper).updateById(task);
    }

    @Test
    void unregisteredProviderLeavesHistoricalTaskReadable() {
        VideoGenerationTask task = task("RUNNING", "video_123");
        task.setProvider("retired-vendor");
        when(taskMapper.selectOwnedById(OWNER, 7L)).thenReturn(task);
        when(providerRegistry.find("retired-vendor")).thenReturn(Optional.empty());

        VideoGenerationResponse response = service.get(OWNER, 7L);

        // 配置变更不应让历史任务整体不可读
        assertThat(response.status()).isEqualTo("RUNNING");
        verify(taskMapper, never()).updateById(any(VideoGenerationTask.class));
    }

    @Test
    void unconfiguredProviderLeavesTaskUnrefreshedInsteadOfFailing() {
        VideoGenerationTask task = task("PENDING", "video_123");
        when(taskMapper.selectOwnedById(OWNER, 7L)).thenReturn(task);
        when(providerRegistry.find("openai")).thenReturn(Optional.of(provider));
        when(provider.configured()).thenReturn(false);

        VideoGenerationResponse response = service.get(OWNER, 7L);

        assertThat(response.status()).isEqualTo("PENDING");
        verify(provider, never()).query(anyString());
    }

    @Test
    void providerFailureMessageIsPersistedTruncatedToColumnWidth() {
        VideoGenerationTask task = task("RUNNING", "video_123");
        when(taskMapper.selectOwnedById(OWNER, 7L)).thenReturn(task);
        when(providerRegistry.find("openai")).thenReturn(Optional.of(provider));
        when(provider.configured()).thenReturn(true);
        when(provider.query("video_123"))
                .thenReturn(VideoTaskSnapshot.failed("x".repeat(900)));

        VideoGenerationResponse response = service.get(OWNER, 7L);

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(response.errorMessage()).hasSize(500);
    }

    @Test
    void listClampsLimitToConfiguredBounds() {
        when(taskMapper.selectAllOwned(OWNER, 100)).thenReturn(List.of());
        when(taskMapper.selectAllOwned(OWNER, 1)).thenReturn(List.of());
        when(taskMapper.selectAllOwned(OWNER, 20)).thenReturn(List.of());

        service.list(OWNER, 10_000);
        service.list(OWNER, 0);
        service.list(OWNER, null);

        verify(taskMapper).selectAllOwned(OWNER, 100);
        verify(taskMapper).selectAllOwned(OWNER, 1);
        verify(taskMapper).selectAllOwned(OWNER, 20);
    }

    private static VideoGenerationTask task(String status, String providerTaskId) {
        VideoGenerationTask task = new VideoGenerationTask();
        task.setId(7L);
        task.setOwnerUserId(OWNER);
        task.setProvider("openai");
        task.setModel("sora-2");
        task.setStatus(status);
        task.setPrompt("一只猫在草地上奔跑");
        task.setProviderTaskId(providerTaskId);
        task.setCreatedAt(Instant.now());
        task.setUpdatedAt(Instant.now());
        return task;
    }
}
