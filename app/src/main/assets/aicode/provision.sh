#!/bin/sh
# 容器初始化依赖安装脚本（通用）：按包管理器安装基础工具。
# 由 App 启动时提取到 ~/.aicode/provision.sh，内置 Alpine 首次初始化时以 `sh` 执行。
# 修改包清单或安装逻辑后，需同步在 LinuxContainerEngine.PROVISION_VERSION 上 +1 触发存量设备重跑。
# 注意：apk 源分支 v3.21 需与 assets 内 alpine-rootfs 版本（ContainerInstaller.INSTALL_VERSION）保持一致。
set -e

if command -v apk >/dev/null 2>&1; then
    # Alpine：幂等覆盖 apk 源为阿里云国内镜像（兜底存量旧 rootfs 仍指向官方源），再刷新索引装包。
    # 用 http 而非 https：minirootfs 无 ca-certificates，apk 对索引与包做独立签名校验，http 不影响完整性。
    mkdir -p /etc/apk
    cat > /etc/apk/repositories <<'EOF'
http://mirrors.aliyun.com/alpine/v3.21/main
http://mirrors.aliyun.com/alpine/v3.21/community
EOF
    apk update
    apk add --no-cache python3 py3-pip nodejs npm bash curl ripgrep git
elif command -v apt-get >/dev/null 2>&1; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -y
    apt-get install -y python3 python3-pip nodejs npm bash curl ripgrep git
elif command -v dnf >/dev/null 2>&1; then
    dnf install -y python3 python3-pip nodejs npm bash curl ripgrep git
elif command -v yum >/dev/null 2>&1; then
    yum install -y python3 python3-pip nodejs npm bash curl ripgrep git
elif command -v pacman >/dev/null 2>&1; then
    pacman -Sy --noconfirm python python-pip nodejs npm bash curl ripgrep git
else
    echo "No supported package manager (apk/apt-get/dnf/yum/pacman)" >&2
    exit 1
fi

# git credential helper：store（命中已有凭据秒过）+ aicode 自定义 helper（未命中时经文件 IPC 弹窗回填）。
# credential.helper 是 multi-valued，先 --replace-all 清旧值再 --add，保证顺序幂等（store 在前、aicode 在后）。
git config --global --replace-all credential.helper 'store --file=/root/.aicode/git-credentials'
git config --global --add credential.helper '/root/.aicode/git-credential-aicode'
