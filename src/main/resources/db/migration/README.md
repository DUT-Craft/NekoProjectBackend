# 数据库迁移脚本（生产）

> 开发环境 `application.yaml` 默认 `ddl-auto=create`，Hibernate 每次启动重建表结构，**不需要**这些脚本。
> 生产环境 `application-prod.yaml` 强制 `ddl-auto=validate`，实体改造后**必须**先手动执行这些脚本，
> 否则 Hibernate 校验 `tag` 表 / 新 `object_item_tag` 关联缺失会导致启动失败。

本项目当前未启用 Flyway/Liquibase；以下 SQL 为**人工发布脚本**，按顺序在 PostgreSQL 上执行。
执行前请备份 `object_item`、`object_item_tag`（旧），执行后按脚本内注释逐条跑校验查询。

## 执行顺序

1. **备份**：`object_item`、`object_item_tag`、（执行后）`tag`、新 `object_item_tag`。
2. `V2__create_tag_schema.sql` — 建 `tag` 表 + ID 化 `object_item_tag`，旧字符串表重命名为 `object_item_tag_legacy`。
3. `V3__migrate_object_item_type_and_tags.sql` — 把旧 `type` 与旧字符串标签迁成 `tag` 记录 + 关联（去重）。
4. 运行 V3 文件末尾的校验查询：重复关联 / 孤儿关联 / 超 10 个标签项目 / 标准化名重复，均应符合预期。
5. `V4__drop_legacy_project_type.sql` — 确认前端已切换、校验通过后，删除 `type` 列、旧索引、`object_item_tag_legacy`。

## 设计要点

- **标签名唯一**：服务层 `TagService` 对活跃标签（`deleted_at IS NULL`）按 `normalized_name`（trim + 小写）兜底；
  数据库侧由 `uk_tag_normalized_name_active` 部分唯一索引补齐（Hibernate 注解无法表达部分索引）。
- **关联去重**：`object_item_tag` 以 `(object_item_id, tag_id)` 复合主键作最后防线，与实体 `@ManyToMany ... Set<Tag>` 对应。
- **每项目最多 10 个标签**：由应用服务统一校验（`TagService.resolveSelectableTags`），数据库不强制触发器（单写入口）。
- **应用层校验优于 DB**：迁移后如发现某项目超过 10 个标签，脚本不会静默截断，需人工确认保留项后再做 V4。
