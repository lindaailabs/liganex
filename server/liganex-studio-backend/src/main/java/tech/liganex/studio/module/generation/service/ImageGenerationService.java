package tech.liganex.studio.module.generation.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.generation.dto.ImageGenerationDtos.ImageGenerationResponse;
import tech.liganex.studio.module.generation.dto.ImageGenerationDtos.SubmitImageGenerationRequest;
import tech.liganex.studio.module.generation.entity.ImageGenerationTask;
import tech.liganex.studio.module.generation.mapper.ImageGenerationTaskMapper;
import tech.liganex.studio.module.generation.provider.ImageGenerationCommand;
import tech.liganex.studio.module.generation.provider.ImageGenerationProvider;
import tech.liganex.studio.module.generation.provider.ImageGenerationResult;

import java.time.Instant;
import java.util.List;

/**
 * 图片生成任务服务：提交、查询、列表。
 *
 * <p>不变量：供应商未配置时，在发起**任何**出站调用之前拒绝
 * （{@link ErrorCode#IMAGE_PROVIDER_NOT_CONFIGURED}），不产生成本。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageGenerationService {

    private static final int DEFAULT_LIST_LIMIT = 20;
    private static final int MAX_LIST_LIMIT = 100;

    private final ImageGenerationTaskMapper taskMapper;
    private final ImageGenerationProvider provider;

    public ImageGenerationResponse submit(Long ownerUserId, SubmitImageGenerationRequest request) {
        requireOpenAi(request.provider());
        requireConfigured(provider);

        ImageGenerationCommand command = new ImageGenerationCommand(
                request.prompt().trim(),
                trimToNull(request.model()),
                trimToNull(request.size()));

        ImageGenerationResult result = provider.generate(command);

        Instant now = Instant.now();
        ImageGenerationTask task = new ImageGenerationTask();
        task.setOwnerUserId(ownerUserId);
        task.setProvider(provider.name());
        task.setModel(result.model());
        task.setStatus("SUCCEEDED");
        task.setPrompt(command.prompt());
        task.setSize(command.size());
        task.setImageUrl(result.url());
        task.setImageB64(result.b64Json());
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        task.setCompletedAt(now);
        taskMapper.insert(task);
        return ImageGenerationResponse.from(task);
    }

    public ImageGenerationResponse get(Long ownerUserId, Long taskId) {
        ImageGenerationTask task = requireOwned(ownerUserId, taskId);
        return ImageGenerationResponse.from(task);
    }

    public List<ImageGenerationResponse> list(Long ownerUserId, Integer limit) {
        int effectiveLimit = limit == null
                ? DEFAULT_LIST_LIMIT
                : Math.min(Math.max(limit, 1), MAX_LIST_LIMIT);
        return taskMapper.selectAllOwned(ownerUserId, effectiveLimit).stream()
                .map(ImageGenerationResponse::from)
                .toList();
    }

    /** owner 隔离：非本人资源与不存在返回同一错误，不泄露资源是否存在。 */
    public ImageGenerationTask requireOwned(Long ownerUserId, Long taskId) {
        ImageGenerationTask task = taskMapper.selectOwnedById(ownerUserId, taskId);
        if (task == null) {
            throw new BizException(ErrorCode.IMAGE_TASK_NOT_FOUND);
        }
        return task;
    }

    private void requireOpenAi(String requested) {
        if (requested != null && !requested.isBlank() && !"openai".equalsIgnoreCase(requested.trim())) {
            throw new BizException(ErrorCode.IMAGE_PROVIDER_UNSUPPORTED);
        }
    }

    private void requireConfigured(ImageGenerationProvider provider) {
        if (!provider.configured()) {
            throw new BizException(ErrorCode.IMAGE_PROVIDER_NOT_CONFIGURED);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
