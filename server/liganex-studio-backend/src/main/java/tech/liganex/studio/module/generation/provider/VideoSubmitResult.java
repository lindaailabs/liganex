package tech.liganex.studio.module.generation.provider;

/**
 * 提交生成后的受理结果。
 *
 * @param providerTaskId 供应商侧任务标识，后续轮询凭此回源；不得为 null
 * @param model          实际使用的模型（适配器回退到默认值后的结果），用于落库参数快照
 * @param status         受理时归一化后的状态，通常为 {@link VideoTaskStatus#PENDING}
 */
public record VideoSubmitResult(String providerTaskId, String model, VideoTaskStatus status) {
}
