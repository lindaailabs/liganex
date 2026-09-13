package tech.liganex.studio.common;

/**
 * 业务错误码。对外只暴露 message，不泄露内部异常细节。
 */
public enum ErrorCode {

    // 通用
    BAD_REQUEST(40000, "请求参数有误"),
    UNAUTHORIZED(40100, "未认证或认证已失效"),
    FORBIDDEN(40300, "没有访问权限"),
    NOT_FOUND(40400, "资源不存在"),
    INTERNAL_ERROR(50000, "服务内部错误"),

    // 用户与认证（user-auth）
    EMAIL_ALREADY_EXISTS(41001, "该邮箱已被注册"),
    INVALID_CREDENTIALS(41002, "邮箱或密码错误"),
    USER_DISABLED(41003, "账号已被停用"),
    INVALID_TOKEN(41004, "令牌无效或已过期"),

    // 开放平台（open-platform-app）
    APP_NOT_FOUND(42001, "应用不存在"),
    APP_DISABLED(42002, "应用已被停用"),
    PERMISSION_NOT_FOUND(42003, "权限不存在"),

    // 订单（order-query / order-write）
    ORDER_QUERY_FAILED(43001, "订单查询失败"),
    ORDER_NOT_FOUND(43002, "订单不存在"),

    // 知识库（knowledge-base-rag）
    KNOWLEDGE_BASE_NOT_FOUND(40410, "知识库不存在"),
    KNOWLEDGE_DOCUMENT_NOT_FOUND(40411, "知识文档不存在"),
    KNOWLEDGE_DOCUMENT_EMPTY(40010, "知识文档内容不能为空"),
    KNOWLEDGE_DOCUMENT_TOO_LARGE(40011, "知识文档超过大小限制"),
    KNOWLEDGE_DOCUMENT_TYPE_UNSUPPORTED(40012, "仅支持 TXT、Markdown 和 PDF 文件"),
    KNOWLEDGE_DOCUMENT_FILENAME_INVALID(40013, "文件名不合法"),
    KNOWLEDGE_DOCUMENT_RETRY_NOT_ALLOWED(40014, "仅处理失败的文档可以重试"),
    KNOWLEDGE_DOCUMENT_ALREADY_EXISTS(40015, "知识库中已存在相同内容的文档"),

    // 内部服务接口（internal-api）
    INTERNAL_API_KEY_INVALID(45001, "服务间凭证无效"),

    // 视频生成（ai-video-generation）
    // 460 段 = 服务未就绪（HTTP 503）：区别于内部错误，用户可据此判断是配置缺失而非服务故障
    VIDEO_PROVIDER_NOT_CONFIGURED(46001, "视频生成服务未配置"),
    // 470 段 = 请求侧可纠正的错误（HTTP 400）
    VIDEO_PROVIDER_UNSUPPORTED(47001, "不支持的视频生成供应商"),
    // 480 段 = 上游供应商故障（HTTP 502）
    VIDEO_GENERATION_FAILED(48001, "视频生成供应商调用失败"),
    VIDEO_TASK_NOT_FOUND(40412, "视频生成任务不存在"),

    // MCP / 开放平台签名鉴权（mcp-auth）
    SIGNATURE_INVALID(44001, "签名校验失败"),
    TIMESTAMP_EXPIRED(44002, "请求时间戳超出有效期"),
    NONCE_REPLAY(44003, "nonce 重复，疑似重放攻击"),
    SCOPE_FORBIDDEN(44004, "应用未授权该权限"),
    QUOTA_EXCEEDED(44005, "调用配额已用尽"),
    APP_SECRET_BROKEN(44006, "应用密钥不可用");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }
}
