package tech.liganex.studio.module.generation.provider;

/**
 * 与供应商无关的图片生成指令。
 *
 * @param prompt 文本提示词（必填）
 * @param model  模型标识；为 null 时由适配器回退到配置默认模型
 * @param size   分辨率，如 {@code 1024x1024}；为 null 时用供应商默认
 */
public record ImageGenerationCommand(String prompt, String model, String size) {
}
