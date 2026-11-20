# 项目长期记忆（绿益公益代码仓库）

## 自动同步约定
- 本目录 `F:/codex-lvyi` 的远端为 `https://github.com/xsliu1989/-workbuddy.git`。
- 任何 WorkBuddy 任务在本目录产生文件改动后，收尾时必须调用用户级 Skill `github-auto-sync` 提交并推送；无改动则跳过。
- 本地分支 `master` 必须显式推送到远端 `main`（`git push origin HEAD:main`），因为 `push.default` 未设置且分支名不一致。
- 同步完成后必须向用户展示本次上传的文件清单及变更状态（新增/修改/删除/重命名），不能只报数量。
- 同步脚本会逐步输出 `[auto-sync][STEP]` 与 `[auto-sync][CMD]`，最终回复需保留这些命令记录，便于定位卡在哪一步。
- 禁止 `git push --force`、`--no-verify`、`git commit --amend`；敏感文件或超大文件被拦截时不得绕过检查。
