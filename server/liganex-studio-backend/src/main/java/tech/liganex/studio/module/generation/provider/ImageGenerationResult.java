package tech.liganex.studio.module.generation.provider;

/**
 * 图片生成结果。
 *
 * @param url     生成图片的可访问地址（优先）
 * @param b64Json Base64 编码的图片字节（当供应商仅返回内联字节时）
 * @param model   实际使用的模型，用于落库参数快照
 */
public record ImageGenerationResult(String url, String b64Json, String model) {
}
