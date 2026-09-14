package tech.liganex.studio.module.generation.provider;

/**
 * 与供应商无关的视频生成任务状态机。
 *
 * <p>各供应商自有状态字面量（如 Sora 的 {@code queued/in_progress/completed/failed}）
 * 一律在适配器内归一化到本枚举，业务层、REST 契约与前端只依赖这四种状态。
 */
public enum VideoTaskStatus {

    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED;

    /** 终态：不再变化，调用方（含前端轮询）应停止回源。 */
    public boolean terminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
