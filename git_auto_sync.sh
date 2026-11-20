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

log() { printf '[auto-sync] %s\n' "$*"; }
fail() { printf '[auto-sync][ERROR] %s\n' "$*" >&2; exit 1; }

command -v git >/dev/null 2>&1 || fail "git not found in PATH"
[ -d "$REPO/.git" ] || fail "not a git repository: $REPO"

ACTUAL_REMOTE="$(git -C "$REPO" remote get-url origin 2>/dev/null || true)"
[ "$ACTUAL_REMOTE" = "$EXPECTED_REMOTE" ] || fail "origin mismatch. expected=$EXPECTED_REMOTE actual=$ACTUAL_REMOTE"

git -C "$REPO" config user.name >/dev/null 2>&1 || fail "git user.name is not set for $REPO"
git -C "$REPO" config user.email >/dev/null 2>&1 || fail "git user.email is not set for $REPO"

if [ -z "$(git -C "$REPO" status --porcelain)" ]; then
  log "无改动，跳过提交与推送"
  exit 0
fi

log "暂存改动..."
git -C "$REPO" add -A

CHANGED_FILES="$(git -C "$REPO" diff --cached --name-only)"
[ -n "$CHANGED_FILES" ] || { log "无改动，跳过提交与推送"; exit 0; }

SENSITIVE_NAME_RE='(^|/)\.env($|\.)|(^|/)secrets?([-_.].*)?$|(^|/)credentials?([-_.].*)?$|(^|/)id_(rsa|dsa|ecdsa|ed25519)|\.pem$|\.key$|\.pfx$|\.p12$|\.keystore$|application-local\.(yml|yaml|properties)$|\.git-credentials$|\.netrc$|(^|/)\.npmrc$|(^|/)\.htpasswd$'
SECRET_CONTENT_RE='ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|-----BEGIN (RSA |EC |DSA |OPENSSH |PGP )?PRIVATE KEY-----|AIza[0-9A-Za-z_-]{35}'

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
  git -C "$REPO" reset -q
  printf '[auto-sync][ERROR] 检测到敏感文件或超大文件，已取消暂存：\n%b' "$BLOCKED" >&2
  fail "请处理后重试；不要绕过此检查"
fi

if [ -z "$MESSAGE" ]; then
  COUNT="$(printf '%s\n' "$CHANGED_FILES" | grep -c . || true)"
  FIRST="$(printf '%s\n' "$CHANGED_FILES" | head -1)"
  if [ "$COUNT" -eq 1 ]; then
    MESSAGE="自动同步: 更新 ${FIRST}"
  else
    MESSAGE="自动同步: 更新 ${COUNT} 个文件（${FIRST} 等）"
  fi
fi

log "提交: $MESSAGE"
git -C "$REPO" commit -m "$MESSAGE" --quiet

push_once() {
  git -C "$REPO" push origin "HEAD:${REMOTE_BRANCH}"
}

push_http11() {
  git -C "$REPO" -c http.version=HTTP/1.1 push origin "HEAD:${REMOTE_BRANCH}"
}

push_openssl() {
  git -C "$REPO" -c http.sslBackend=openssl -c http.version=HTTP/1.1 push origin "HEAD:${REMOTE_BRANCH}"
}

push_with_fallback() {
  if push_once; then return 0; fi
  log "首次推送失败，尝试使用 HTTP/1.1 重试..."
  if push_http11; then return 0; fi
  log "仍失败，尝试使用 OpenSSL SSL 后端重试..."
  if push_openssl; then return 0; fi
  log "仍失败，尝试拉取远端变更并变基后重试..."
  if ! git -C "$REPO" pull --rebase origin "$REMOTE_BRANCH"; then
    fail "变基失败或存在冲突；本地提交已保留，请人工处理后重试"
  fi
  if push_once; then return 0; fi
  if push_http11; then return 0; fi
  if push_openssl; then return 0; fi
  return 1
}

log "推送到 origin/${REMOTE_BRANCH}..."
push_with_fallback || fail "推送仍失败；本地提交已保留，请人工处理"

get_remote_head() {
  local out=""
  out="$(git -C "$REPO" ls-remote origin "refs/heads/${REMOTE_BRANCH}" 2>/dev/null | awk 'NR==1{print $1}')" || true
  if [ -z "$out" ]; then
    out="$(git -C "$REPO" -c http.version=HTTP/1.1 ls-remote origin "refs/heads/${REMOTE_BRANCH}" 2>/dev/null | awk 'NR==1{print $1}')" || true
  fi
  if [ -z "$out" ]; then
    out="$(git -C "$REPO" -c http.sslBackend=openssl -c http.version=HTTP/1.1 ls-remote origin "refs/heads/${REMOTE_BRANCH}" 2>/dev/null | awk 'NR==1{print $1}')" || true
  fi
  printf '%s' "$out"
}

LOCAL_HEAD="$(git -C "$REPO" rev-parse HEAD)"
REMOTE_HEAD="$(get_remote_head)"
FILE_COUNT="$(printf '%s\n' "$CHANGED_FILES" | grep -c . || true)"

if [ -z "$REMOTE_HEAD" ]; then
  log "警告: 无法查询远端 HEAD（网络原因）；推送命令已成功返回，请稍后手动确认"
  log "完成：${FILE_COUNT} 个文件，commit=${LOCAL_HEAD:0:7}，推送已执行但远端校验跳过"
else
  [ "$LOCAL_HEAD" = "$REMOTE_HEAD" ] || fail "推送后校验失败：local=$LOCAL_HEAD remote=$REMOTE_HEAD"
  log "完成：${FILE_COUNT} 个文件，commit=${LOCAL_HEAD:0:7}，origin/${REMOTE_BRANCH} 已校验一致"
fi
