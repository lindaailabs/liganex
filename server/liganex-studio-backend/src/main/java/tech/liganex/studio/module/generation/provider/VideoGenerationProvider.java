package tech.liganex.studio.module.generation.provider;

/**
 * 视频生成供应商抽象。
 *
 * <p>新增一家供应商 = 实现本接口并注册为 Spring 组件，业务层（service/controller）无需改动。
 * 供应商私有的请求构造、响应字段解析、状态字面量与鉴权头，**必须**全部收敛在实现类内，
 * 不得泄漏到业务层——线上字段若有出入，只需改适配器。
 */
public interface VideoGenerationProvider {

    /** 供应商标识（小写），用于配置与请求路由，如 {@code openai}。 */
    String name();

    /**
     * 凭证是否就绪。
     *
     * <p>未就绪时调用方必须在**发起任何出站调用之前**拒绝请求（不产生成本）。
     */
    boolean configured();

    /**
     * 提交一次生成。实现方须先自行校验 {@link #configured()}，保证未配置时不会有出站调用。
     *
     * @return 供应商侧任务标识与受理状态
     */
    VideoSubmitResult submit(VideoGenerationCommand command);

    /**
     * 按供应商侧任务标识回源查询当前状态。
     */
    VideoTaskSnapshot query(String providerTaskId);
}
