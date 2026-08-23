---
name: task-output-layout
description: 按任务名称组织临时文件与产物：在 E:\Work Document\Temp Work Document\<任务名>\ 下建立 Temp Document 与 OutPut 两个子文件夹，分别存放过程临时文件与最终交付物。
---

# 任务临时文件与产物组织规范

## 触发条件

- 开始任何多步骤任务，或任务涉及多个中间文件、草稿、分析笔记、报告；
- 用户要求整理任务过程文件或交付物。

## 目录结构

每个任务在根目录 `E:\Work Document\Temp Work Document\` 下按任务名称建立文件夹：

```
E:\Work Document\Temp Work Document\
└── <任务名>\
    ├── README.md            （可选：说明任务内容与文件清单）
    ├── Temp Document\       （过程临时文件：草稿、分析笔记、中间产物、调试记录）
    └── OutPut\              （最终产物：报告、交付文件、总结）
```

## 规则

1. **任务名**：使用描述性名称（中文或英文均可），一个任务一个文件夹，不复用其他任务的文件夹。
2. **Temp Document**：任务进行中的草稿、中间数据、分析过程、临时脚本等一律放这里；任务结束时清理无价值文件。
3. **OutPut**：最终可交付成果统一放这里；若有多项成果，可用子文件夹归类。
4. **README.md**：任务较复杂时在任务文件夹根编写，说明任务背景、目录内容与使用方式。
5. 工作区根目录（`E:\Work Document\`）只保留指令类文件（如 `要求.txt`、`AGENTS.md`）与 `Temp Work Document\` 目录，不堆放任务文件。

## 示例

任务"编写数据清洗脚本"：

```
Temp Work Document\数据清洗脚本\
├── Temp Document\需求分析.md
├── Temp Document\清洗方案草稿.md
└── OutPut\清洗脚本.py
└── OutPut\使用说明.md
```

## 关联

- 本规范由用户全局指令 `~/.dsh/AGENTS.md` 第 3 条定义，此处为可执行流程版。
- 技能提取流程见 `skill-library-evolution`。
