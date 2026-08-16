#!/usr/bin/env python3
"""把 GitHub 上已有的 Release APK 历史资产同步到 Gitee（幂等可重跑，支持策略清理）。

用法：
  GITEE_TOKEN=<token> python3 scripts/sync-gitee-releases.py            # 同步保留集合
  GITEE_TOKEN=<token> python3 scripts/sync-gitee-releases.py --prune    # 先删除 Gitee 上超出保留集合的 Release，再同步
  GITEE_TOKEN=<token> python3 scripts/sync-gitee-releases.py --clean    # 先删除 Gitee 上全部 Release，再重建

保留集合（未设 RELEASE_FILTER 时自动推导）：
  最新 KEEP_STABLE 个正式版（默认 3）+ 最新正式版之后的全部 RC（如 v1.8.0 之后的 v1.9.0-rc*）。
  正式版发布后其 RC 自然过期，下次运行 --prune 时会被清理。

可选环境变量：
  GH_REPO         GitHub 仓库，默认 jieapi/aicode
  GITEE_OWNER     Gitee 用户名，默认取 GH_REPO 同名
  GITEE_REPO      Gitee 仓库名，默认取 GH_REPO 同名
  GITHUB_TOKEN    GitHub 令牌（可选，提高 API 限额）
  RELEASE_FILTER  Python 正则，覆盖自动推导，仅同步匹配 tag_name 的 Release
  KEEP_STABLE     保留的正式版数量，默认 3

前置：Gitee 仓库需已通过官方「仓库镜像」同步了对应 tag（否则创建 Release 会失败）。
"""

import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.parse
import urllib.request

UA = "aicode-gitee-sync"
MAX_ATTACH = 100 * 1024 * 1024  # Gitee 社区版附件单文件上限 100MB

GH_REPO = os.environ.get("GH_REPO", "jieapi/aicode")
GITEE_OWNER = os.environ.get("GITEE_OWNER", GH_REPO.split("/")[0])
GITEE_REPO = os.environ.get("GITEE_REPO", GH_REPO.split("/")[1])
GITEE_TOKEN = os.environ.get("GITEE_TOKEN", "")
GITHUB_TOKEN = os.environ.get("GITHUB_TOKEN", "")
RELEASE_FILTER = os.environ.get("RELEASE_FILTER", "")
KEEP_STABLE = int(os.environ.get("KEEP_STABLE", "3"))
GITEE_API = f"https://gitee.com/api/v5/repos/{GITEE_OWNER}/{GITEE_REPO}"
GH_API = f"https://api.github.com/repos/{GH_REPO}"

TAG_RE = re.compile(r"^v(\d+)\.(\d+)\.(\d+)(?:-rc(\d+))?$")


def parse_tag(tag: str) -> tuple | None:
    m = TAG_RE.match(tag)
    if not m:
        return None
    return (int(m.group(1)), int(m.group(2)), int(m.group(3)), bool(m.group(4)))


def http_json(url: str, payload: dict | None = None, method: str | None = None, timeout: int = 90):
    headers = {"User-Agent": UA}
    if GITHUB_TOKEN and url.startswith(GH_API):
        headers["Authorization"] = f"Bearer {GITHUB_TOKEN}"
    if payload is None:
        req = urllib.request.Request(url, headers=headers, method=method)
    else:
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode(),
            headers={**headers, "Content-Type": "application/json"},
            method=method or "POST",
        )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        body = resp.read().decode()
        if not body:  # 部分接口（如 DELETE）成功时返回空响应体
            return None
        return json.loads(body)


def gitee_get(path: str, params: dict | None = None):
    q = {"access_token": GITEE_TOKEN}
    if params:
        q.update(params)
    return http_json(f"{GITEE_API}{path}?{urllib.parse.urlencode(q)}")


def gitee_post(path: str, payload: dict):
    payload = dict(payload)
    payload["access_token"] = GITEE_TOKEN
    return http_json(f"{GITEE_API}{path}", payload)


def gitee_delete(path: str):
    url = f"{GITEE_API}{path}?{urllib.parse.urlencode({'access_token': GITEE_TOKEN})}"
    http_json(url, method="DELETE")


def gh_get(path: str):
    return http_json(f"{GH_API}{path}")


def download(url: str, dest: str) -> bool:
    for attempt in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=180) as resp, open(dest, "wb") as f:
                shutil.copyfileobj(resp, f)
            return True
        except Exception as e:
            print(f"    下载失败（第 {attempt + 1} 次）：{e}")
            time.sleep(2 * (attempt + 1))
    return False


def upload(release_id: str, path: str) -> bool:
    for attempt in range(3):
        r = subprocess.run(
            [
                "curl", "-sS", "-f", "-X", "POST",
                "-F", f"access_token={GITEE_TOKEN}",
                "-F", f"file=@{path}",
                f"{GITEE_API}/releases/{release_id}/attach_files",
            ],
            capture_output=True,
            text=True,
        )
        if r.returncode == 0:
            return True
        print(f"    上传失败（第 {attempt + 1} 次）：{r.stdout.strip()[:200] or r.stderr.strip()[:200]}")
        time.sleep(2 * (attempt + 1))
    return False


def list_gitee_releases() -> list:
    out = []
    page = 1
    while True:
        batch = gitee_get("/releases", {"per_page": 100, "page": page})
        out.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    return out


def derive_keep_set(releases: list) -> set:
    """自动推导保留集合：最新 KEEP_STABLE 个正式版 + 最新正式版之后的全部 RC。"""
    parsed = [(parse_tag(r["tag_name"]), r["tag_name"]) for r in releases]
    parsed = [(p, t) for p, t in parsed if p]
    stable = sorted((p for p, _ in parsed if not p[3]), reverse=True)
    if not stable:
        return set()
    keep_main = {s[:3] for s in stable[:KEEP_STABLE]}
    newest_main = stable[0][:3]
    keep = set()
    for p, t in parsed:
        if not p[3] and p[:3] in keep_main:
            keep.add(t)
        elif p[3] and p[:3] > newest_main:
            keep.add(t)
    return keep


def main() -> int:
    if not GITEE_TOKEN:
        print("错误：请通过环境变量提供 GITEE_TOKEN（Gitee 私人令牌，需 projects 权限）")
        return 1
    print(f"源: GitHub {GH_REPO} -> 目标: Gitee {GITEE_OWNER}/{GITEE_REPO}")
    if RELEASE_FILTER:
        print(f"版本过滤（正则）: {RELEASE_FILTER}")

    # 验证 Gitee token 与仓库可达，避免把鉴权错误误判成其它问题
    try:
        gitee_get("")
    except Exception as e:
        print(f"错误：无法访问 Gitee 仓库 {GITEE_OWNER}/{GITEE_REPO}：{e}")
        return 1

    # 拉取 GitHub 全部 releases（分页）
    gh_releases = []
    page = 1
    while True:
        batch = gh_get(f"/releases?per_page=100&page={page}")
        gh_releases.extend(batch)
        if len(batch) < 100:
            break
        page += 1

    # 保留集合：RELEASE_FILTER 覆盖时用正则，否则自动推导（最新 KEEP_STABLE 正式版 + 其后全部 RC）
    if RELEASE_FILTER:
        pattern = re.compile(RELEASE_FILTER)
        keep = {r["tag_name"] for r in gh_releases if pattern.match(r["tag_name"])}
        print(f"版本过滤（正则）: {RELEASE_FILTER}")
    else:
        keep = derive_keep_set(gh_releases)
        print(f"保留策略: 最新 {KEEP_STABLE} 个正式版 + 最新正式版之后的全部 RC")
    gh_releases = [r for r in gh_releases if r["tag_name"] in keep]
    print(f"GitHub 上 {len(gh_releases)} 个 Release 需保留")

    # 拉取 Gitee 已有 releases（幂等对比 + 清理用）
    gitee_releases = {r["tag_name"]: r for r in list_gitee_releases()}
    print(f"Gitee 已有 {len(gitee_releases)} 个 Release")

    # 可选：清空全部（--clean）或删除超出保留集合的（--prune）
    if "--clean" in sys.argv:
        for tag, rel in list(gitee_releases.items()):
            gitee_delete(f"/releases/{rel['id']}")
            print(f"  已删除 {tag}")
        gitee_releases.clear()
    elif "--prune" in sys.argv:
        for tag, rel in list(gitee_releases.items()):
            if tag not in keep:
                gitee_delete(f"/releases/{rel['id']}")
                print(f"  [清理过期] 已删除 {tag}")
                del gitee_releases[tag]

    ok = skipped = failed = 0
    tmpdir = tempfile.mkdtemp(prefix="gitee-sync-")
    try:
        # 按创建时间升序处理，先旧后新
        for rel in sorted(gh_releases, key=lambda r: r["created_at"]):
            tag = rel["tag_name"]
            existing = gitee_releases.get(tag)
            attach_names = set()
            if existing is not None:
                release_id = str(existing["id"])
                # Gitee 列表接口直接带 assets，无需再查详情
                attach_names = {a.get("name") for a in existing.get("assets", [])}
                print(f"[跳过创建] {tag}（已存在 Release #{release_id}，附件 {len(attach_names)} 个）")
            else:
                print(f"[创建] {tag} ...")
                try:
                    created = gitee_post("/releases", {
                        "tag_name": tag,
                        "name": rel.get("name") or f"Release {tag}",
                        "body": rel.get("body") or "",
                        "prerelease": bool(rel.get("prerelease")),
                        "target_commitish": "main",
                    })
                    release_id = str(created["id"])
                    attach_names = set()
                except Exception as e:
                    print(f"  错误：创建 Release 失败：{e}")
                    failed += 1
                    continue

            for asset in rel.get("assets", []):
                name = asset["name"]
                size = asset["size"]
                if size > MAX_ATTACH:
                    print(f"  [跳过] {name}（{size / 1048576:.1f}MB 超过 100MB 上限）")
                    skipped += 1
                    continue
                if name in attach_names:
                    print(f"  [已存在] {name}")
                    skipped += 1
                    continue
                dest = os.path.join(tmpdir, name)
                print(f"  下载 {name}（{size / 1048576:.1f}MB）...")
                if not download(asset["browser_download_url"], dest):
                    print(f"  [失败] {name} 下载失败")
                    failed += 1
                    continue
                if upload(release_id, dest):
                    print(f"  [成功] {name} -> Gitee Release {tag}")
                    ok += 1
                else:
                    print(f"  [失败] {name} 上传失败")
                    failed += 1
                os.remove(dest)
    finally:
        shutil.rmtree(tmpdir, ignore_errors=True)

    print(f"\n完成：成功 {ok}，跳过 {skipped}，失败 {failed}")
    if failed:
        print("有失败项，重跑本脚本即可续传（已成功的会跳过）。")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
