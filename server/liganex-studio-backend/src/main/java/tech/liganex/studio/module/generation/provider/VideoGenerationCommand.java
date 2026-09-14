package tech.liganex.studio.module.generation.provider;

/**
 * 与供应商无关的视频生成指令。
 *
 * <p>字段为跨供应商的公共子集；各适配器负责把自己的私有字段从 {@code model} / 参数快照里补齐。
 *
 * @param prompt          文本提示词（必填）
 * @param model           模型标识；为 null 时由适配器回退到自身默认模型
 * @param durationSeconds 期望时长（秒）；为 null 时用供应商默认
 * @param size            分辨率，如 {@code 720x1280}；为 null 时用供应商默认
 * @param imageUrl        图生视频的参考图地址；为 null 表示文生视频
 */
public record VideoGenerationCommand(
        String prompt,
        String model,
        Integer durationSeconds,
        String size,
        String imageUrl) {
}
