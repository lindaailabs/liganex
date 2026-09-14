package tech.liganex.studio.module.generation.provider;

/**
 * 图片生成供应商抽象（OpenAI 兼容协议）。
 *
 * <p>新增一家供应商 = 实现本接口并注册为 Spring 组件；具体请求构造、响应字段解析与鉴权头
 * 全部收敛在实现类内，业务层不感知供应商私有细节。
 */
public interface ImageGenerationProvider {

    /** 供应商标识（小写），如 {@code openai}。 */
    String name();

    /** 凭证与模型是否就绪；未就绪时调用方必须在发起任何出站调用之前拒绝。 */
    boolean configured();

    /** 同步生成一次图片；实现方须先校验 {@link #configured()}。 */
    ImageGenerationResult generate(ImageGenerationCommand command);
}
