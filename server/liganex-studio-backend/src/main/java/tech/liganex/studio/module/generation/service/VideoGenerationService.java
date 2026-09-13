package tech.liganex.studio.module.generation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

/**
 * 视频生成任务服务：提交、查询、列表。
 *
 * <p>两条成本相关的不变量：
 * <ol>
 *   <li>供应商未配置时，在发起**任何**出站调用之前拒绝（{@link ErrorCode#VIDEO_PROVIDER_NOT_CONFIGURED}）；</li>
 *   <li>查询仅在任务处于非终态时才回源供应商，终态直接读库——避免读取放大上游调用。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoGenerationService {

    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;
    /** error_summary 列为 VARCHAR(500)，截断避免超长上游报文把一次成功查询变成 DB 错误。 */
    private static final int MAX_ERROR_SUMMARY_LENGTH = 500;
    private static final String FALLBACK_ERROR_SUMMARY = "视频生成失败";

    private final VideoGenerationTaskMapper taskMapper;
    private final VideoProviderRegistry providerRegistry;

    @Transactional
    public VideoGenerationResponse submit(Long ownerUserId, SubmitVideoGenerationRequest request) {
        VideoGenerationProvider provider = providerRegistry.resolve(request.provider());
        requireConfigured(provider);

        VideoGenerationCommand command = new VideoGenerationCommand(
                request.prompt().trim(),
                trimToNull(request.model()),
                request.durationSeconds(),
                trimToNull(request.size()),
                trimToNull(request.imageUrl()));

        VideoSubmitResult submitted = provider.submit(command);

        Instant now = Instant.now();
        VideoGenerationTask task = new VideoGenerationTask();
        task.setOwnerUserId(ownerUserId);
        task.setProvider(provider.name());
        task.setModel(submitted.model());
        task.setStatus(submitted.status().name());
        task.setPrompt(command.prompt());
        task.setSourceImageUrl(command.imageUrl());
        task.setDurationSeconds(command.durationSeconds());
        task.setSize(command.size());
        task.setProviderTaskId(submitted.providerTaskId());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        if (submitted.status().terminal()) {
            task.setCompletedAt(now);
        }
        taskMapper.insert(task);
        return VideoGenerationResponse.from(task);
    }

    @Transactional
    public VideoGenerationResponse get(Long ownerUserId, Long taskId) {
        VideoGenerationTask task = requireOwned(ownerUserId, taskId);
        return VideoGenerationResponse.from(refreshIfNotTerminal(task));
    }

    public List<VideoGenerationResponse> list(Long ownerUserId, Integer limit) {
        int effectiveLimit = limit == null
                ? DEFAULT_LIST_LIMIT
                : Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        return taskMapper.selectAllOwned(ownerUserId, effectiveLimit).stream()
                .map(VideoGenerationResponse::from)
                .toList();
    }

    /** owner 隔离：非本人资源与不存在返回同一错误，不泄露资源是否存在。 */
    public VideoGenerationTask requireOwned(Long ownerUserId, Long taskId) {
        VideoGenerationTask task = taskMapper.selectOwnedById(ownerUserId, taskId);
        if (task == null) {
            throw new BizException(ErrorCode.VIDEO_TASK_NOT_FOUND);
        }
        return task;
    }

    private VideoGenerationTask refreshIfNotTerminal(VideoGenerationTask task) {
        VideoTaskStatus status = VideoTaskStatus.valueOf(task.getStatus());
        if (status.terminal() || task.getProviderTaskId() == null) {
            return task;
        }

        Optional<VideoGenerationProvider> provider = providerRegistry.find(task.getProvider());
        if (provider.isEmpty() || !provider.get().configured()) {
            // 配置变更或凭证缺失不应让历史任务整体不可读：保留已有快照，下次再试
            log.debug("skip refresh for task={} provider={} (unresolvable or unconfigured)",
                    task.getId(), task.getProvider());
            return task;
        }

        applySnapshot(task, provider.get().query(task.getProviderTaskId()));
        taskMapper.updateById(task);
        return task;
    }

    private void applySnapshot(VideoGenerationTask task, VideoTaskSnapshot snapshot) {
        task.setStatus(snapshot.status().name());
        task.setUpdatedAt(Instant.now());
        if (snapshot.status() == VideoTaskStatus.SUCCEEDED) {
            task.setVideoUrl(snapshot.videoUrl());
            task.setErrorSummary(null);
            task.setCompletedAt(task.getUpdatedAt());
        } else if (snapshot.status() == VideoTaskStatus.FAILED) {
            task.setErrorSummary(truncate(
                    firstNonBlank(snapshot.errorMessage(), FALLBACK_ERROR_SUMMARY),
                    MAX_ERROR_SUMMARY_LENGTH));
            task.setCompletedAt(task.getUpdatedAt());
        }
    }

    private void requireConfigured(VideoGenerationProvider provider) {
        if (!provider.configured()) {
            throw new BizException(ErrorCode.VIDEO_PROVIDER_NOT_CONFIGURED);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
