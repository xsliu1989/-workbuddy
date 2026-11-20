---
name: github-auto-sync
description: This skill should be used at the end of any WorkBuddy task that changed files under F:/codex-lvyi (the 绿益公益 project), or whenever the user asks to save, sync, or push changes. It commits local changes and pushes them to https://github.com/xsliu1989/-workbuddy.git with secret scanning and safe ignore rules. Use when work in that repository is complete and files were added, modified, renamed, or deleted.
agent_created: true
---

# GitHub Auto Sync

## Overview

Automatically persist work done by WorkBuddy in the `F:/codex-lvyi` repository: commit all local changes and push them to `https://github.com/xsliu1989/-workbuddy.git` so the user never needs to push manually from another device.

Local branch `master` maps to remote branch `main`; always push with the explicit refspec `HEAD:main`.

## When to Trigger

- At the end of any task that modified files under `F:/codex-lvyi`, before reporting completion.
- When the user explicitly asks to save, sync, commit, or push the repository.
- Do not trigger for work done in other directories, or when the working tree has no changes.

## Workflow

### Step 1: Check for changes

Run `git -C "F:/codex-lvyi" status --short` and `git -C "F:/codex-lvyi" rev-list --count origin/main..HEAD`.

If the working tree is clean and the ahead count is 0, report "无改动且无待推送提交，跳过提交与推送" and stop. Do not create empty commits. If the tree is clean but there are pending local commits, let the script push them and list their files; do not report "无改动".

### Step 2: Run the sync script

Execute the bundled script, passing a short Chinese commit message that summarizes the actual changes:

```bash
bash "C:/Users/Administrator/.workbuddy/skills/github-auto-sync/scripts/git_auto_sync.sh" \
  --repo "F:/codex-lvyi" \
  --message "自动同步: <一句话概括本次改动>"
```

Derive the message from the status output and, when useful, `git -C "F:/codex-lvyi" diff --stat`. Mention the main files or feature names touched.

If no `--message` is provided, the script generates a summary from the changed file list.

### Step 3: Interpret the result

- On success: report the short commit hash, number of files changed, and that the push to `origin/main` was verified. Always include the `本次上传的文件` list printed by the script in the final reply to the user, with each file path and its status (新增/修改/删除/重命名). Never summarize the list as just a count.
- On `无改动`: report that nothing was pushed.
- On `敏感文件` or `大文件` errors: do not bypass, do not use `--force`, and do not edit the checks. Tell the user exactly which files were blocked and ask whether to remove them or add a justified ignore rule.
- On push failure: report that the local commit is preserved, include the error, and ask the user before any manual recovery.

### Step 4: Confirm final state

Run `git -C "F:/codex-lvyi" status --short --branch` and include the tracking state in the final report.

## Safety Rules

- Never use `git push --force`, `--no-verify`, `git commit --amend`, or `git reset --hard`.
- Never disable secret scanning or ignore rules to make a sync pass.
- Never modify `.gitignore` solely to hide a secret file.
- Keep `.gitignore` at the repository root up to date when new build artifacts or dependency directories appear, using ignore patterns only for generated or non-sensitive files.
- Authentication uses the local Git Credential Manager already configured for `origin`. Do not print tokens or credentials in output.

## Resources

### scripts/git_auto_sync.sh

Deterministic sync script. It validates the remote URL and repo path, stages changes, scans staged files for sensitive filenames and credential-like content, rejects files larger than 50 MB, commits with the supplied or generated message, pushes to `origin/main` (retrying with HTTP/1.1, an OpenSSL SSL backend, and then once after a rebase if the remote moved or the connection drops), verifies that the remote HEAD matches the local commit, and finally prints every uploaded file with its change status. If the remote cannot be queried because of a network error after a successful push, the script reports a warning instead of a failure.
