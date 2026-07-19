-- =============================================================================
-- V3__migrate_object_item_type_and_tags.sql
-- -----------------------------------------------------------------------------
-- 把旧 object_item.type（项目类型）与 object_item_tag_legacy.tag（字符串标签）
-- 迁移为 tag 字典记录 + object_item_tag 关联，并通过复合主键自动去重。
--
-- 已知英文 type 映射为中文展示名；未知 type 原样入库（待人工确认）。
-- 迁移前请备份；可在事务内执行，失败整体回滚。
--
-- 执行顺序：V2 → V3 →（校验通过）→ V4
-- =============================================================================

BEGIN;

-- 1) 建立一个「项目方向」分组节点（不可选），归集由旧 type 迁移出的标签。
INSERT INTO tag (name, normalized_name, parent_id, selectable, sort_order, create_time, update_time)
SELECT '项目方向', '项目方向', NULL, FALSE, 0, now(), now()
WHERE NOT EXISTS (
    SELECT 1 FROM tag WHERE normalized_name = '项目方向' AND deleted_at IS NULL
);

-- 2) 旧 type 去重后写入 tag（已知英文 → 中文，未知原样；挂在「项目方向」分组下）。
WITH mapped AS (
    SELECT DISTINCT
        CASE LOWER(TRIM(type))
            WHEN 'build'    THEN '建筑'
            WHEN 'survival' THEN '生存'
            WHEN 'rpg'      THEN 'RPG'
            ELSE TRIM(type)
        END AS display_name
    FROM object_item
    WHERE type IS NOT NULL AND TRIM(type) <> ''
)
INSERT INTO tag (name, normalized_name, parent_id, selectable, sort_order, create_time, update_time)
SELECT m.display_name,
       LOWER(m.display_name),
       (SELECT id FROM tag WHERE normalized_name = '项目方向' AND deleted_at IS NULL),
       TRUE, 0, now(), now()
FROM mapped m
WHERE NOT EXISTS (
    SELECT 1 FROM tag tg
    WHERE tg.normalized_name = LOWER(m.display_name) AND tg.deleted_at IS NULL
);

-- 3) 旧字符串标签去重后写入 tag（作为根级可选标签，复用上一步已建的同名标签）。
INSERT INTO tag (name, normalized_name, parent_id, selectable, sort_order, create_time, update_time)
SELECT DISTINCT TRIM(l.tag), LOWER(TRIM(l.tag)), NULL, TRUE, 0, now(), now()
FROM object_item_tag_legacy l
WHERE l.tag IS NOT NULL AND TRIM(l.tag) <> ''
  AND NOT EXISTS (
      SELECT 1 FROM tag tg
      WHERE tg.normalized_name = LOWER(TRIM(l.tag)) AND tg.deleted_at IS NULL
  );

-- 4) 按旧 type 建立项目 → 标签关联（ON CONFLICT 去重）。
INSERT INTO object_item_tag (object_item_id, tag_id)
SELECT o.id, tg.id
FROM object_item o
JOIN tag tg
  ON tg.normalized_name = LOWER(
        CASE LOWER(TRIM(o.type))
            WHEN 'build'    THEN '建筑'
            WHEN 'survival' THEN '生存'
            WHEN 'rpg'      THEN 'RPG'
            ELSE TRIM(o.type)
        END)
     AND tg.deleted_at IS NULL
WHERE o.type IS NOT NULL AND TRIM(o.type) <> ''
ON CONFLICT (object_item_id, tag_id) DO NOTHING;

-- 5) 按旧字符串标签建立项目 → 标签关联。
INSERT INTO object_item_tag (object_item_id, tag_id)
SELECT l.object_item_id, tg.id
FROM object_item_tag_legacy l
JOIN tag tg ON tg.normalized_name = LOWER(TRIM(l.tag)) AND tg.deleted_at IS NULL
WHERE l.tag IS NOT NULL AND TRIM(l.tag) <> ''
ON CONFLICT (object_item_id, tag_id) DO NOTHING;

COMMIT;

-- -----------------------------------------------------------------------------
-- 迁移校验（应在 COMMIT 后逐条运行，确认无误再做 V4 清理）：
--
-- -- 关联是否重复：应返回 0 行
-- SELECT object_item_id, tag_id, COUNT(*)
-- FROM object_item_tag
-- GROUP BY object_item_id, tag_id
-- HAVING COUNT(*) > 1;
--
-- -- 超过 10 个标签的项目：需人工确认保留项（不静默截断）
-- SELECT object_item_id, COUNT(*) AS cnt
-- FROM object_item_tag
-- GROUP BY object_item_id
-- HAVING COUNT(*) > 10
-- ORDER BY cnt DESC;
--
-- -- 孤儿关联（指向不存在的 tag）：应返回 0
-- SELECT COUNT(*)
-- FROM object_item_tag oit
-- LEFT JOIN tag t ON t.id = oit.tag_id
-- WHERE t.id IS NULL;
--
-- -- 活跃标签标准化名重复：应返回 0 行
-- SELECT normalized_name, COUNT(*)
-- FROM tag
-- WHERE deleted_at IS NULL
-- GROUP BY normalized_name
-- HAVING COUNT(*) > 1;
-- -----------------------------------------------------------------------------
