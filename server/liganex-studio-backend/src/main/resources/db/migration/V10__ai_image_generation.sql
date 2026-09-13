-- AI 图片生成任务（bend-ai-generation 的图片生成切片）。
-- 与视频生成同款：状态机与供应商无关，供应商私有状态归一化后落库。

CREATE TABLE image_generation_task (
    id                BIGSERIAL    PRIMARY KEY,
    owner_user_id     BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    provider          VARCHAR(32)  NOT NULL,
    model             VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    prompt            TEXT         NOT NULL,
    size              VARCHAR(32),
    -- 生成结果：可访问地址或 Base64 字节（二者择一）
    image_url         VARCHAR(4096),
    image_b64         TEXT,
    error_summary     VARCHAR(500),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    completed_at      TIMESTAMPTZ,
    CONSTRAINT ck_image_generation_task_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ux_image_generation_task_id_owner UNIQUE (id, owner_user_id)
);

CREATE INDEX ix_image_generation_task_owner_created
    ON image_generation_task (owner_user_id, created_at DESC, id DESC);

COMMENT ON TABLE  image_generation_task        IS 'AI 图片生成任务（统一状态机）';
COMMENT ON COLUMN image_generation_task.status IS 'PENDING | RUNNING | SUCCEEDED | FAILED';
COMMENT ON COLUMN image_generation_task.provider IS '供应商标识，如 openai';
COMMENT ON COLUMN image_generation_task.image_url IS '生成图片的可访问地址；未完成时为 NULL';
