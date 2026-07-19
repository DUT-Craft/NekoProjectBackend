-- =============================================================================
-- V4__drop_legacy_project_type.sql
-- -----------------------------------------------------------------------------
-- 清理旧结构：删除 object_item.type 列、旧 type 索引、旧字符串标签备份表。
--
-- 仅在 V3 迁移校验全部通过、且确认前端已切换到 tagIds / 对象型 tags 之后执行。
-- 执行前务必备份 object_item / object_item_tag_legacy。执行后 Hibernate validate
-- 仍可正常启动（实体已不再映射 type，validate 仅要求映射列存在，不拒绝多余列）。
-- =============================================================================

BEGIN;

ALTER TABLE object_item DROP COLUMN IF EXISTS type;

DROP INDEX IF EXISTS idx_object_item_type;

DROP TABLE IF EXISTS object_item_tag_legacy;

COMMIT;
