#!/usr/bin/env bash
# Auto-commit and push WorkBuddy changes in F:/codex-lvyi to origin/main.
set -euo pipefail

DEFAULT_REPO="F:/codex-lvyi"
EXPECTED_REMOTE="https://github.com/xsliu1989/-workbuddy.git"
REMOTE_BRANCH="main"
MAX_BYTES=$((50 * 1024 * 1024))

REPO="$DEFAULT_REPO"
MESSAGE=""

while [ $# -gt 0 ]; do
  case "$1" in
    --repo)
      [ $# -ge 2 ] || { echo "[auto-sync][ERROR] --repo needs a path" >&2; exit 2; }
      REPO="$2"; shift 2 ;;
    --message)
      [ $# -ge 2 ] || { echo "[auto-sync][ERROR] --message needs text" >&2; exit 2; }
      MESSAGE="$2"; shift 2 ;;
    *)
      echo "[auto-sync][ERROR] unknown argument: $1" >&2; exit 2 ;;
  esac
done

log()  { printf '[auto-sync] %s\n' "$*"; }
step() { printf '[auto-sync][STEP] %s\n' "$*"; }
cmd()  { printf '[auto-sync][CMD] %s\n' "$*"; }
run()  { cmd "$*"; "$@"; }
fail() { printf '[auto-sync][ERROR] %s\n' "$*" >&2; exit 1; }

print_uploaded_files() {
  log "本次上传的文件（${FILE_COUNT} 个）："
  while IFS=$'\t' read -r st f1 f2; do
    [ -n "$st" ] || continue
    case "${st:0:1}" in
      A) label="新增" ;;
      M) label="修改" ;;
      D) label="删除" ;;
      R) label="重命名" ;;
      C) label="复制" ;;
      T) label="类型变更" ;;
      *) label="变更" ;;
    esac
    if [ "${st:0:1}" = "R" ] || [ "${st:0:1}" = "C" ]; then
      log "  [${label}] ${f1} -> ${f2}"
    else
      log "  [${label}] ${f1}"
    fi
  done <<< "$CHANGED_STATUS"
}

step "预检：环境与远端"
cmd "command -v git"
command -v git >/dev/null 2>&1 || fail "git not found in PATH"
[ -d "$REPO/.git" ] || fail "not a git repository: $REPO"

cmd "git -C \"$REPO\" remote get-url origin"
ACTUAL_REMOTE="$(git -C "$REPO" remote get-url origin 2>/dev/null || true)"
[ "$ACTUAL_REMOTE" = "$EXPECTED_REMOTE" ] || fail "origin mismatch. expected=$EXPECTED_REMOTE actual=$ACTUAL_REMOTE"

cmd "git -C \"$REPO\" config user.name"
git -C "$REPO" config user.name >/dev/null 2>&1 || fail "git user.name is not set for $REPO"
cmd "git -C \"$REPO\" config user.email"
git -C "$REPO" config user.email >/dev/null 2>&1 || fail "git user.email is not set for $REPO"

step "变更检测：工作区与待推送提交"
cmd "git -C \"$REPO\" status --porcelain"
DIRTY="$(git -C "$REPO" status --porcelain)"
cmd "git -C \"$REPO\" rev-list --count origin/${REMOTE_BRANCH}..HEAD"
AHEAD="$(git -C "$REPO" rev-list --count "origin/${REMOTE_BRANCH}..HEAD" 2>/dev/null || echo 0)"
if [ -z "$DIRTY" ] && [ "$AHEAD" -eq 0 ]; then
  log "无改动且无待推送提交，跳过提交与推送"
  exit 0
fi

NEED_COMMIT=0
if [ -n "$DIRTY" ]; then
  NEED_COMMIT=1
  step "暂存改动"
  run git -C "$REPO" add -A
  cmd "git -C \"$REPO\" diff --cached --name-only"
  CHANGED_FILES="$(git -C "$REPO" diff --cached --name-only)"
  cmd "git -C \"$REPO\" diff --cached --name-status"
  CHANGED_STATUS="$(git -C "$REPO" diff --cached --name-status)"
  [ -n "$CHANGED_FILES" ] || { log "无改动，跳过提交与推送"; exit 0; }
else
  step "工作区干净，复用 ${AHEAD} 个待推送提交"
  cmd "git -C \"$REPO\" diff --name-only origin/${REMOTE_BRANCH}..HEAD"
  CHANGED_FILES="$(git -C "$REPO" diff --name-only "origin/${REMOTE_BRANCH}..HEAD")"
  cmd "git -C \"$REPO\" diff --name-status origin/${REMOTE_BRANCH}..HEAD"
  CHANGED_STATUS="$(git -C "$REPO" diff --name-status "origin/${REMOTE_BRANCH}..HEAD")"
fi
FILE_COUNT="$(printf '%s\n' "$CHANGED_FILES" | grep -c . || true)"

SENSITIVE_NAME_RE='(^|/)\.env($|\.)|(^|/)secrets?([-_.].*)?$|(^|/)credentials?([-_.].*)?$|(^|/)id_(rsa|dsa|ecdsa|ed25519)|\.pem$|\.key$|\.pfx$|\.p12$|\.keystore$|application-local\.(yml|yaml|properties)$|\.git-credentials$|\.netrc$|(^|/)\.npmrc$|(^|/)\.htpasswd$'
SECRET_CONTENT_RE='ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----|AIza[0-9A-Za-z_-]{35}'

if [ "$NEED_COMMIT" -eq 1 ]; then
  step "敏感信息扫描（文件名 / 凭据内容 / 50MB 大小）"
  cmd "grep -Eqi(敏感文件名规则) + grep -IqnE(凭据规则) + wc -c  # 共 ${FILE_COUNT} 个文件"
  BLOCKED=""
  while IFS= read -r file; do
    [ -n "$file" ] || continue
    if printf '%s\n' "$file" | grep -Eqi "$SENSITIVE_NAME_RE"; then
      BLOCKED="${BLOCKED}${file} (敏感文件名)\n"
      continue
    fi
    abs="$REPO/$file"
    [ -f "$abs" ] || continue
    size="$(wc -c < "$abs" 2>/dev/null | tr -d ' ')"
    if [ -n "$size" ] && [ "$size" -gt "$MAX_BYTES" ]; then
      BLOCKED="${BLOCKED}${file} (超过 50 MB)\n"
    fi
    if grep -IqnE "$SECRET_CONTENT_RE" "$abs" 2>/dev/null; then
      BLOCKED="${BLOCKED}${file} (疑似凭据内容)\n"
    fi
  done <<< "$CHANGED_FILES"

  if [ -n "$BLOCKED" ]; then
    run git -C "$REPO" reset -q
    printf '[auto-sync][ERROR] 检测到敏感文件或超大文件，已取消暂存：\n%b' "$BLOCKED" >&2
    fail "请处理后重试；不要绕过此检查"
  fi

  step "生成提交信息"
  if [ -z "$MESSAGE" ]; then
    FIRST="$(printf '%s\n' "$CHANGED_FILES" | head -1)"
    if [ "$FILE_COUNT" -eq 1 ]; then
      MESSAGE="自动同步: 更新 ${FIRST}"
    else
      MESSAGE="自动同步: 更新 ${FILE_COUNT} 个文件（${FIRST} 等）"
    fi
  fi
  log "提交信息: $MESSAGE"

  step "提交"
  cmd "git -C \"$REPO\" commit -m \"$MESSAGE\" --quiet"
  git -C "$REPO" commit -m "$MESSAGE" --quiet
else
  step "跳过提交（工作区无改动）"
fi

push_once() {
  cmd "git -C \"$REPO\" push origin HEAD:${REMOTE_BRANCH}"
  git -C "$REPO" push origin "HEAD:${REMOTE_BRANCH}"
}

push_http11() {
  cmd "git -C \"$REPO\" -c http.version=HTTP/1.1 push origin HEAD:${REMOTE_BRANCH}"
  git -C "$REPO" -c http.version=HTTP/1.1 push origin "HEAD:${REMOTE_BRANCH}"
}

push_openssl() {
  cmd "git -C \"$REPO\" -c http.sslBackend=openssl -c http.version=HTTP/1.1 push origin HEAD:${REMOTE_BRANCH}"
  git -C "$REPO" -c http.sslBackend=openssl -c http.version=HTTP/1.1 push origin "HEAD:${REMOTE_BRANCH}"
}

push_with_fallback() {
  if push_once; then return 0; fi
  log "首次推送失败，尝试使用 HTTP/1.1 重试..."
  if push_http11; then return 0; fi
  log "仍失败，尝试使用 OpenSSL SSL 后端重试..."
  if push_openssl; then return 0; fi
  log "仍失败，尝试拉取远端变更并变基后重试..."
  cmd "git -C \"$REPO\" pull --rebase origin ${REMOTE_BRANCH}"
  if ! git -C "$REPO" pull --rebase origin "$REMOTE_BRANCH"; then
    if [ -n "$(git -C "$REPO" diff --name-only --diff-filter=U)" ]; then
      fail "变基存在冲突；本地提交已保留，请人工处理后重试"
    fi
    fail "拉取远端失败（网络原因）；本地提交已保留，请稍后重试"
  fi
  if push_once; then return 0; fi
  if push_http11; then return 0; fi
  if push_openssl; then return 0; fi
  return 1
}

step "推送到 origin/${REMOTE_BRANCH}"
push_with_fallback || fail "推送仍失败；本地提交已保留，请人工处理"

step "远端校验"
cmd "git -C \"$REPO\" rev-parse HEAD"
LOCAL_HEAD="$(git -C "$REPO" rev-parse HEAD)"
get_remote_head() {
  local out=""
  cmd "git -C \"$REPO\" ls-remote origin refs/heads/${REMOTE_BRANCH}"
  out="$(git -C "$REPO" ls-remote origin "refs/heads/${REMOTE_BRANCH}" 2>/dev/null | awk 'NR==1{print $1}')" || true
  if [ -z "$out" ]; then
    cmd "git -C \"$REPO\" -c http.version=HTTP/1.1 ls-remote origin refs/heads/${REMOTE_BRANCH}"
    out="$(git -C "$REPO" -c http.version=HTTP/1.1 ls-remote origin "refs/heads/${REMOTE_BRANCH}" 2>/dev/null | awk 'NR==1{print $1}')" || true
  fi
  if [ -z "$out" ]; then
    cmd "git -C \"$REPO\" -c http.sslBackend=openssl -c http.version=HTTP/1.1 ls-remote origin refs/heads/${REMOTE_BRANCH}"
    out="$(git -C "$REPO" -c http.sslBackend=openssl -c http.version=HTTP/1.1 ls-remote origin "refs/heads/${REMOTE_BRANCH}" 2>/dev/null | awk 'NR==1{print $1}')" || true
  fi
  printf '%s' "$out"
}
REMOTE_HEAD="$(get_remote_head)"

step "汇总"
if [ -z "$REMOTE_HEAD" ]; then
  log "警告: 无法查询远端 HEAD（网络原因）；推送命令已成功返回，请稍后手动确认"
  log "完成：${FILE_COUNT} 个文件，commit=${LOCAL_HEAD:0:7}，推送已执行但远端校验跳过"
else
  [ "$LOCAL_HEAD" = "$REMOTE_HEAD" ] || fail "推送后校验失败：local=$LOCAL_HEAD remote=$REMOTE_HEAD"
  log "完成：${FILE_COUNT} 个文件，commit=${LOCAL_HEAD:0:7}，origin/${REMOTE_BRANCH} 已校验一致"
fi
print_uploaded_files
