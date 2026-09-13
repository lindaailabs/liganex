-- AI 视频生成异步任务（bend-ai-generation 的视频生成切片）。
-- 状态机与供应商无关：供应商自有状态字面量在 provider 适配器内归一化后再落库
-- （见 module/generation/provider/openai），故本表只存统一状态，CHECK 约束可收紧。

CREATE TABLE video_generation_task (
    id                BIGSERIAL    PRIMARY KEY,
    owner_user_id     BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider          VARCHAR(32)  NOT NULL,
    model             VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    prompt            TEXT         NOT NULL,
    source_image_url  VARCHAR(2048),
    duration_seconds  SMALLINT,
    size              VARCHAR(32),
    -- 供应商侧任务标识：轮询回源时使用，与本地 id 解耦，便于日后换供应商/拆仓迁移
    provider_task_id  VARCHAR(128),
    video_url         VARCHAR(2048),
    error_summary     VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    CONSTRAINT ck_video_generation_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    -- 与 knowledge_base 同款：把 (id, owner_user_id) 做成唯一键，供子表按 owner 维度做组合外键
    CONSTRAINT ux_video_generation_task_id_owner UNIQUE (id, owner_user_id)
);

-- 列表按 owner + 时间倒序；查询单条走主键，owner 隔离由 SQL 的 WHERE 保证
CREATE INDEX ix_video_generation_task_owner_created
    ON video_generation_task (owner_user_id, created_at DESC, id DESC);

COMMENT ON TABLE  video_generation_task            IS 'AI 视频生成异步任务（统一状态机）';
COMMENT ON COLUMN video_generation_task.status     IS 'PENDING | RUNNING | SUCCEEDED | FAILED';
COMMENT ON COLUMN video_generation_task.provider   IS '供应商标识，如 openai；用于回源轮询时解析适配器';
COMMENT ON COLUMN video_generation_task.video_url  IS '生成完成的视频资产地址；未完成时为 NULL';
