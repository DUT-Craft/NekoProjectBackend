-- =============================================================================
-- V2__create_tag_schema.sql
-- -----------------------------------------------------------------------------
-- 建立全局 Tag 字典 + ID 化的项目-标签关联表（替换旧的 object_item_tag 字符串集合）。
--
-- 何时执行：生产环境 ddl-auto=validate，实体改造后必须先执行本脚本，否则 Hibernate
-- 校验 tag 表 / 新 object_item_tag 关联缺失会启动失败。
--
-- 前置：已备份 object_item 与 object_item_tag。
-- 幂等说明：CREATE TABLE/INDEX 带 IF NOT EXISTS；旧表重命名用 DO 块守护，重复执行安全。
-- =============================================================================

BEGIN;

-- 1) Tag 字典表（自关联父子层级 + 软删除 + 标准化名）
CREATE TABLE IF NOT EXISTS tag (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(32) NOT NULL,
    normalized_name VARCHAR(32) NOT NULL,
    parent_id       BIGINT NULL REFERENCES tag(id) ON DELETE RESTRICT,
    selectable      BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order      INTEGER NOT NULL DEFAULT 0,
    description     VARCHAR(255),
    deleted_at      TIMESTAMP NULL,
    create_time     TIMESTAMP NOT NULL DEFAULT now(),
    update_time     TIMESTAMP NOT NULL DEFAULT now()
);

-- 活跃标签标准化名唯一（部分唯一索引；Hibernate 注解无法表达，仅由迁移脚本建立）
CREATE UNIQUE INDEX IF NOT EXISTS uk_tag_normalized_name_active
    ON tag(normalized_name)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_tag_parent_sort
    ON tag(parent_id, sort_order, id);

CREATE INDEX IF NOT EXISTS idx_tag_normalized_name
    ON tag(normalized_name);

-- 2) 备份旧的字符串标签关联表（原 object_item_tag 为 @ElementCollection(tag VARCHAR)）。
--    仅当它仍是旧的字符串形态（无 tag_id 列）时才重命名，避免误伤已迁移的结构。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_name = 'object_item_tag'
          AND NOT EXISTS (
              SELECT 1 FROM information_schema.columns
              WHERE table_name = 'object_item_tag' AND column_name = 'tag_id'
          )
    ) THEN
        ALTER TABLE object_item_tag RENAME TO object_item_tag_legacy;
    END IF;
END $$;

-- 3) 新的 ID 化多对多关联表：(object_item_id, tag_id) 复合主键 = 数据库去重最后防线。
CREATE TABLE IF NOT EXISTS object_item_tag (
    object_item_id INTEGER NOT NULL REFERENCES object_item(id) ON DELETE CASCADE,
    tag_id         BIGINT NOT NULL REFERENCES tag(id) ON DELETE RESTRICT,
    PRIMARY KEY (object_item_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_object_item_tag_tag_id
    ON object_item_tag(tag_id, object_item_id);

COMMIT;
