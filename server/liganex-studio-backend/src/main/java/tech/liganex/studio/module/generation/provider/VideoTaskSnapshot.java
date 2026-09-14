package tech.liganex.studio.module.generation.provider;

/**
 * 轮询回源得到的一次任务快照。
 *
 * @param status       归一化后的统一状态
 * @param videoUrl     仅当 {@link VideoTaskStatus#SUCCEEDED} 时有值
 * @param errorMessage 仅当 {@link VideoTaskStatus#FAILED} 时有值；面向用户，不含供应商原始报文
 */
public record VideoTaskSnapshot(VideoTaskStatus status, String videoUrl, String errorMessage) {

    public static VideoTaskSnapshot succeeded(String videoUrl) {
        return new VideoTaskSnapshot(VideoTaskStatus.SUCCEEDED, videoUrl, null);
    }

    public static VideoTaskSnapshot failed(String errorMessage) {
        return new VideoTaskSnapshot(VideoTaskStatus.FAILED, null, errorMessage);
    }

    public static VideoTaskSnapshot of(VideoTaskStatus status) {
        return new VideoTaskSnapshot(status, null, null);
    }
}
