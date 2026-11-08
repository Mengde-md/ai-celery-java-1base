-- Task 保存当前状态、最新结果/错误和所有已报告 Attempt 的累计 Token 用量。
CREATE TABLE ai_task (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT, -- 内部主键，用于关联和稳定排序
    task_id CHAR(36) NOT NULL,                  -- 服务端生成、对外暴露的 UUID
    task_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    priority INT NOT NULL DEFAULT 0,
    input_json JSON NOT NULL,
    options_json JSON NOT NULL,
    result_text LONGTEXT NULL,
    finish_reason VARCHAR(64) NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,       -- 每次成功抢占时递增
    max_attempts INT NOT NULL,
    lease_owner VARCHAR(128) NULL,
    lease_token CHAR(36) NULL,                  -- 本次执行所有权凭证，结果回写必须匹配
    lease_expire_at DATETIME(6) NULL,
    last_error_type VARCHAR(64) NULL,
    last_error_code VARCHAR(128) NULL,
    last_error_message VARCHAR(1024) NULL,
    input_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0, -- 所有已上报 Attempt 的累计输入 Token
    output_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_task_task_id (task_id),
    -- 与抢占 SQL 的过滤、排序顺序一致。
    KEY idx_ai_task_schedule (status ASC, priority DESC, updated_at ASC, id ASC),
    KEY idx_ai_task_lease_expire (status, lease_expire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Attempt 只记录一次实际执行的最终结论，不参与任务抢占和当前状态判断。
CREATE TABLE ai_task_attempt (
    id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    ai_task_id BIGINT UNSIGNED NOT NULL,
    attempt_no INT NOT NULL,
    lease_token CHAR(36) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    retryable BOOLEAN NULL,
    error_type VARCHAR(64) NULL,
    error_code VARCHAR(128) NULL,
    error_message VARCHAR(1024) NULL,
    input_tokens BIGINT UNSIGNED NULL,
    output_tokens BIGINT UNSIGNED NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NOT NULL,
    duration_ms BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attempt_task_no (ai_task_id, attempt_no),
    KEY idx_attempt_ai_task_id (ai_task_id),
    CONSTRAINT fk_attempt_ai_task FOREIGN KEY (ai_task_id) REFERENCES ai_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
