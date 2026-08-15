#!/usr/bin/env python3
"""把 GitHub 上已有的 Release APK 历史资产同步到 Gitee（一次性迁移，幂等可重跑）。

用法：GITEE_TOKEN=<Gitee 私人令牌> python3 scripts/sync-gitee-releases.py

可选环境变量：
  GH_REPO       GitHub 仓库，默认 jieapi/aicode
  GITEE_OWNER   Gitee 用户名，默认取 GH_REPO 同名
  GITEE_REPO    Gitee 仓库名，默认取 GH_REPO 同名

前置：Gitee 仓库需已通过官方「仓库镜像」同步了对应 tag（否则创建 Release 会失败）。
"""

import json
import os
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
GITEE_API = f"https://gitee.com/api/v5/repos/{GITEE_OWNER}/{GITEE_REPO}"
GH_API = f"https://api.github.com/repos/{GH_REPO}"


def http_json(url: str, payload: dict | None = None, timeout: int = 90):
    if payload is None:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
    else:
        req = urllib.request.Request(
            url,
            data=json.dumps(payload).encode(),
            headers={"User-Agent": UA, "Content-Type": "application/json"},
            method="POST",
        )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode())


def gitee_get(path: str, params: dict | None = None):
    q = {"access_token": GITEE_TOKEN}
    if params:
        q.update(params)
    return http_json(f"{GITEE_API}{path}?{urllib.parse.urlencode(q)}")


def gitee_post(path: str, payload: dict):
    payload = dict(payload)
    payload["access_token"] = GITEE_TOKEN
    return http_json(f"{GITEE_API}{path}", payload)


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
        print(f"    上传失败（第 {attempt + 1} 次）：{r.stderr.strip()[:200]}")
        time.sleep(2 * (attempt + 1))
    return False


def main() -> int:
    if not GITEE_TOKEN:
        print("错误：请通过环境变量提供 GITEE_TOKEN（Gitee 私人令牌，需 projects 权限）")
        return 1
    print(f"源: GitHub {GH_REPO} -> 目标: Gitee {GITEE_OWNER}/{GITEE_REPO}")

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
    print(f"GitHub 上有 {len(gh_releases)} 个 Release")

    # 拉取 Gitee 已有 releases（幂等对比用）
    gitee_releases = {}
    page = 1
    while True:
        batch = gitee_get("/releases", {"per_page": 100, "page": page})
        for r in batch:
            gitee_releases[r["tag_name"]] = r
        if len(batch) < 100:
            break
        page += 1
    print(f"Gitee 已有 {len(gitee_releases)} 个 Release")

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
                attach_names = {a.get("name") for a in existing.get("attach_files", [])}
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
                except Exception as e:
                    print(f"  错误：创建 Release 失败：{e}")
                    failed += 1
                    continue
            if not attach_names:
                # 列表接口可能不含附件明细，查详情确认
                try:
                    detail = gitee_get(f"/releases/{release_id}")
                    attach_names = {a.get("name") for a in detail.get("attach_files", [])}
                except Exception:
                    pass

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
