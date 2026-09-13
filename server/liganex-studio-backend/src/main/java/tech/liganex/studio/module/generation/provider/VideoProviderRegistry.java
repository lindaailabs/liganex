package tech.liganex.studio.module.generation.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tech.liganex.studio.common.BizException;
import tech.liganex.studio.common.ErrorCode;
import tech.liganex.studio.module.generation.config.VideoGenerationProperties;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 视频生成供应商工厂：按名称解析到具体适配器。
 *
 * <p>刻意不做「未知供应商静默回退到默认供应商」——静默回退会让用户在错误的供应商上产生费用，
 * 且掩盖配置拼写错误，故一律抛出 {@link ErrorCode#VIDEO_PROVIDER_UNSUPPORTED}。
 */
@Slf4j
@Component
public class VideoProviderRegistry {

    private final Map<String, VideoGenerationProvider> providers;
    private final String defaultProviderName;

    public VideoProviderRegistry(
            List<VideoGenerationProvider> providers,
            VideoGenerationProperties properties) {
        this.providers = providers.stream().collect(Collectors.toMap(
                provider -> provider.name().toLowerCase(Locale.ROOT),
                provider -> provider,
                (first, duplicate) -> {
                    throw new IllegalStateException(
                            "重复注册的视频生成供应商：" + first.name());
                },
                LinkedHashMap::new));
        this.defaultProviderName = properties.getProvider();
        log.info("video generation providers registered={} default={}",
                this.providers.keySet(), this.defaultProviderName);
    }

    /**
     * 解析供应商标识；请求未指定时用配置的默认供应商。
     *
     * @throws BizException 供应商未注册
     */
    public VideoGenerationProvider resolve(String requestedName) {
        String name = requestedName == null || requestedName.isBlank()
                ? defaultProviderName
                : requestedName.trim();
        return find(name).orElseThrow(() -> new BizException(ErrorCode.VIDEO_PROVIDER_UNSUPPORTED));
    }

    /**
     * 宽松解析：用于**读取路径**（回源轮询）。
     *
     * <p>任务落库时的 provider 可能因配置变更而不再注册；此时应返回已有快照而非报错，
     * 否则历史任务会因一次配置调整而整体不可读。
     */
    public Optional<VideoGenerationProvider> find(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(name.trim().toLowerCase(Locale.ROOT)));
    }

    /** 已注册的供应商标识，用于排查配置问题。 */
    public Set<String> registered() {
        return Set.copyOf(providers.keySet());
    }
}
