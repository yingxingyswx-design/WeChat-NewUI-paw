# 源码文档索引

本目录存放与代码一起版本化的分析与审计文档。

| 文档 | 内容 | 来源 |
|---|---|---|
| [微信TopBar与UI架构总报告.md](微信TopBar与UI架构总报告.md) | 微信 8.0.79 Top Bar 的完整分层、调用链、布局与状态机 | 桌面文档，2026-11-17 |
| [微信EtE真实性审计.md](微信EtE真实性审计.md) | Window / Content / Visual 三层 EtE 的证据与未完成项清单 | 桌面文档，2026-11-17 |

## 与代码的对应关系

- 基线源码为 GitHub release **v0.2**（`89fd040`），包含 `ChatGlassHook` / `HomeGlassHook` / `GlassRenderer` 等。
- 审计文档第 10 节列出了“下一步处理顺序”，是后续改动的待办清单；本轮仅搭建备份流程，未改代码。
- 每次改动都会在 `backups/<时间>_<短 SHA>/CHANGES.md` 中记录，并引用上述文档。
