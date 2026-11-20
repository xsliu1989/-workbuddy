# 项目长期记忆（绿益公益代码仓库）

## 自动同步约定
- 本目录 `F:/codex-lvyi` 的远端为 `https://github.com/xsliu1989/-workbuddy.git`。
- 任何 WorkBuddy 任务在本目录产生文件改动后，收尾时必须调用用户级 Skill `github-auto-sync` 提交并推送；无改动则跳过。
- 本地分支 `master` 必须显式推送到远端 `main`（`git push origin HEAD:main`），因为 `push.default` 未设置且分支名不一致。
- 禁止 `git push --force`、`--no-verify`、`git commit --amend`；敏感文件或超大文件被拦截时不得绕过检查。
