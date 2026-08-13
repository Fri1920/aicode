#!/bin/sh
# 容器初始化依赖安装脚本（统一）：内置 Alpine 与自定义镜像一致——首次进入终端时在 PTY 上弹出
# 交互菜单，由用户选择自动安装基础工具、手动安装不再提示、或退出。脚本按容器内包管理器
# （apk/apt-get/dnf/yum/pacman）安装同一套工具并可选换国内镜像源。
# 由 App 启动时提取到 ~/.aicode/provision.sh（容器内 /root/.aicode/provision.sh，经 -b 绑定可见）。
# 修改包清单/安装逻辑/镜像源后，需同步在 LinuxContainerEngine.PROVISION_VERSION 上 +1 触发存量设备重跑。
# 注意：apk 源分支 v3.21 需与 assets 内 alpine-rootfs 版本（ContainerInstaller.INSTALL_VERSION）保持一致。

PROVISION_VERSION="provision-script-v6"
PROVISION_SKIPPED="provision-script-skipped"
MARKER="/.provisioned"
MIRROR="mirrors.aliyun.com"

# 已按当前版本完成或用户选择手动安装（跳过）则直接退出
if [ -f "$MARKER" ]; then
    state=$(cat "$MARKER" 2>/dev/null)
    [ "$state" = "$PROVISION_VERSION" ] && exit 0
    [ "$state" = "$PROVISION_SKIPPED" ] && exit 0
fi

# ── 基础工具安装：按容器内包管理器装同一套工具 ──
install_packages() {
    if command -v apk >/dev/null 2>&1; then
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
        echo "未支持的包管理器（apk/apt-get/dnf/yum/pacman）" >&2
        return 1
    fi
}

# ── 镜像源：按包管理器换阿里云国内源，失败自动恢复原配置（不阻塞后续安装）──
setup_mirror() {
    if command -v apk >/dev/null 2>&1; then
        setup_apk_mirror
    elif command -v apt-get >/dev/null 2>&1; then
        setup_apt_mirror
    elif command -v dnf >/dev/null 2>&1; then
        setup_dnf_mirror
    elif command -v yum >/dev/null 2>&1; then
        echo "yum（RHEL/CentOS）暂不支持自动换源，使用默认源" >&2
        return 0
    elif command -v pacman >/dev/null 2>&1; then
        setup_pacman_mirror
    fi
}

setup_apk_mirror() {
    # 用 http 而非 https：minirootfs 无 ca-certificates，apk 对索引与包做独立签名校验，http 不影响完整性。
    mkdir -p /etc/apk
    # Alpine 大版本分支从镜像自身动态读取，兼容用户导入的不同版本 Alpine 镜像：
    # 1) 优先从现有 repositories 提取（官方源 / 已换过的源都含 `alpine/<分支>/`，edge 也能拿到）；
    # 2) 读不到再回退到 /etc/os-release 的 VERSION_ID（如 3.21.3 → v3.21）；
    # 3) 最后兜底 v3.21（与内置 Alpine 一致）。
    branch=""
    if [ -f /etc/apk/repositories ]; then
        branch=$(sed -n 's#.*alpine/\([^/]*\)/.*#\1#p' /etc/apk/repositories 2>/dev/null | head -1)
    fi
    if [ -z "$branch" ] && [ -f /etc/os-release ]; then
        . /etc/os-release 2>/dev/null
        if [ "$ID" = "alpine" ] && [ -n "$VERSION_ID" ]; then
            branch="v$(echo "$VERSION_ID" | cut -d. -f1-2)"
        fi
    fi
    [ -z "$branch" ] && branch="v3.21"
    cat > /etc/apk/repositories <<EOF
http://$MIRROR/alpine/$branch/main
http://$MIRROR/alpine/$branch/community
EOF
}

setup_apt_mirror() {
    . /etc/os-release 2>/dev/null
    codename="${VERSION_CODENAME:-}"
    [ -z "$codename" ] && { echo "无法识别 apt 版本代号，跳过换源" >&2; return 1; }
    # ARM 架构（手机常见）的 Ubuntu 包在 ubuntu-ports 仓库（对应 ports.ubuntu.com）；x86 走 ubuntu 主仓库。
    # Debian 主仓库本身含 arm64/armhf，无需 ports。
    arch=$(dpkg --print-architecture 2>/dev/null || uname -m)
    case "$ID" in
        ubuntu)
            case "$arch" in
                amd64|i386) repo="ubuntu" ;;
                *) repo="ubuntu-ports" ;;
            esac
            uri="http://$MIRROR/$repo/"
            suites="$codename $codename-updates $codename-backports $codename-security"
            components="main restricted universe multiverse"
            ;;
        debian)
            uri="http://$MIRROR/debian/"
            suites="$codename $codename-updates $codename-backports"
            components="main contrib non-free non-free-firmware"
            security_uri="http://$MIRROR/debian-security/"
            ;;
        *) echo "不支持的 apt 发行版：$ID，跳过换源" >&2; return 1 ;;
    esac
    # keyring 存在才写 Signed-By；缺 keyring 时 apt 回退 trusted.gpg.d
    signed_by=""
    [ -f /usr/share/keyrings/ubuntu-archive-keyring.gpg ] && signed_by="Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg"
    [ -f /usr/share/keyrings/debian-archive-keyring.gpg ] && signed_by="Signed-By: /usr/share/keyrings/debian-archive-keyring.gpg"
    # 备份并清空旧源（sources.list 与 sources.list.d 两种格式一并处理）
    backup_dir=/etc/apt/mirror-backup
    rm -rf "$backup_dir" && mkdir -p "$backup_dir"
    [ -f /etc/apt/sources.list ] && cp /etc/apt/sources.list "$backup_dir/" 2>/dev/null
    [ -d /etc/apt/sources.list.d ] && cp -a /etc/apt/sources.list.d "$backup_dir/" 2>/dev/null
    rm -f /etc/apt/sources.list
    rm -rf /etc/apt/sources.list.d
    mkdir -p /etc/apt/sources.list.d
    {
        echo "Types: deb"
        echo "URIs: $uri"
        echo "Suites: $suites"
        echo "Components: $components"
        [ -n "$signed_by" ] && echo "$signed_by"
    } > /etc/apt/sources.list.d/aicode-mirror.sources
    if [ -n "$security_uri" ]; then
        {
            echo "Types: deb"
            echo "URIs: $security_uri"
            echo "Suites: $codename-security"
            echo "Components: $components"
            [ -n "$signed_by" ] && echo "$signed_by"
        } > /etc/apt/sources.list.d/aicode-mirror-security.sources
    fi
    # 验证：update 失败输出原因并恢复原配置
    if ! apt-get update -y; then
        echo "换源后 apt update 失败，已恢复原源配置" >&2
        rm -rf /etc/apt/sources.list.d
        [ -f "$backup_dir/sources.list" ] && cp "$backup_dir/sources.list" /etc/apt/sources.list
        [ -d "$backup_dir/sources.list.d" ] && mv "$backup_dir/sources.list.d" /etc/apt/sources.list.d
        rm -rf "$backup_dir"
        return 1
    fi
    rm -rf "$backup_dir"
    return 0
}

setup_dnf_mirror() {
    # Fedora：备份原 repo，写阿里云 baseurl（国内 metalink 不可用，直接覆写 repo 文件）
    backup_dir=/etc/yum.repos.d/mirror-backup
    rm -rf "$backup_dir" && mkdir -p "$backup_dir"
    cp -a /etc/yum.repos.d/ "$backup_dir/" 2>/dev/null
    rm -f /etc/yum.repos.d/*.repo
    cat > /etc/yum.repos.d/fedora.repo <<EOF
[fedora]
name=Fedora \$releasever - \$basearch
baseurl=https://$MIRROR/fedora/releases/\$releasever/Everything/\$basearch/os/
enabled=1
gpgcheck=1
EOF
    cat > /etc/yum.repos.d/fedora-updates.repo <<EOF
[updates]
name=Fedora \$releasever - \$basearch - Updates
baseurl=https://$MIRROR/fedora/updates/\$releasever/\$basearch/
enabled=1
gpgcheck=1
EOF
    if ! dnf makecache -y >/dev/null 2>&1; then
        echo "换源后 dnf makecache 失败，已恢复原 repo 配置" >&2
        rm -f /etc/yum.repos.d/fedora.repo /etc/yum.repos.d/fedora-updates.repo
        cp -a "$backup_dir/." /etc/yum.repos.d/ 2>/dev/null
        rm -rf "$backup_dir"
        return 1
    fi
    rm -rf "$backup_dir"
    return 0
}

setup_pacman_mirror() {
    backup=/etc/pacman.d/mirrorlist
    [ -f "$backup" ] && cp "$backup" "$backup.backup"
    cat > "$backup" <<EOF
Server = https://$MIRROR/archlinux/\$repo/os/\$arch
EOF
    if ! pacman -Sy --noconfirm >/dev/null 2>&1; then
        echo "换源后 pacman -Sy 失败，已恢复原 mirrorlist" >&2
        [ -f "$backup.backup" ] && mv "$backup.backup" "$backup"
        return 1
    fi
    rm -f "$backup.backup"
    return 0
}

git_config() {
    # git credential helper：store（命中已有凭据秒过）+ aicode 自定义 helper（未命中时经文件 IPC 弹窗回填）。
    # credential.helper 是 multi-valued，先 --replace-all 清旧值再 --add，保证顺序幂等（store 在前、aicode 在后）。
    git config --global --replace-all credential.helper 'store --file=/root/.aicode/git-credentials'
    git config --global --add credential.helper '/root/.aicode/git-credential-aicode'
}

# ── 交互初始化菜单（所有容器统一，在终端 PTY 上运行，用户自主选择安装方式）──
C_BOLD=$(printf '\033[1m')
C_CYAN=$(printf '\033[36m')
C_YELLOW=$(printf '\033[33m')
C_GREEN=$(printf '\033[32m')
C_RED=$(printf '\033[31m')
C_DIM=$(printf '\033[2m')
C_RESET=$(printf '\033[0m')

while :; do
    echo ""
    cat <<EOF
${C_CYAN}    _    ___ ____ ___  ____  _____ ${C_RESET}
${C_CYAN}   / \  |_ _/ ___/ _ \|  _ \| ____|${C_RESET}
${C_CYAN}  / _ \  | | |  | | | | | | |  _|  ${C_RESET}
${C_CYAN} / ___ \ | | |__| |_| | |_| | |___ ${C_RESET}
${C_CYAN}/_/   \_\___\____\___/|____/|_____|${C_RESET}
${C_YELLOW}══════════════════════════════════════════════${C_RESET}
${C_BOLD}  容器初始化 · 选择安装方式${C_RESET}
${C_YELLOW}══════════════════════════════════════════════${C_RESET}
  ${C_GREEN}1. 自动安装依赖${C_RESET}（推荐）
  ${C_BOLD}2. 手动安装${C_RESET}（不再提示）
  ${C_DIM}3. 退出${C_RESET}
${C_YELLOW}══════════════════════════════════════════════${C_RESET}
EOF
    printf "请选择: "
    if ! read choice; then
        echo ""
        echo "输入中断，退出初始化"
        break
    fi
    case "$choice" in
        1)
            echo ""
            echo "${C_YELLOW}安装提示${C_RESET}："
            echo "  · 安装耗时较长，建议开启「后台保活」并将 App 保持在前台"
            echo "  · 安装过程中请勿切走或锁屏，否则进程可能被系统杀死导致安装中断"
            echo ""
            printf "是否自动换源（国内镜像，加速下载）？[y/N]: "
            read mirror_ans
            if [ "$mirror_ans" = "y" ] || [ "$mirror_ans" = "Y" ]; then
                setup_mirror
            fi
            echo ""
            echo "开始安装基础依赖（可能需要几分钟，请耐心等待）..."
            if install_packages; then
                echo "$PROVISION_VERSION" > "$MARKER"
                echo ""
                echo "${C_GREEN}基础依赖安装完成，开始使用吧！${C_RESET}"
            else
                echo ""
                echo "${C_RED}安装失败。可在 AI 对话中让 AI 读取本终端内容进行诊断修复，或重新进入终端重试。${C_RESET}"
            fi
            break
            ;;
        2)
            echo ""
            echo "${C_YELLOW}手动安装提示${C_RESET}："
            echo "  · ripgrep（rg）是必装工具，缺失会影响使用体验"
            echo "  · git 是可视化版本管理操作的基础"
            echo "  · MCP 工具依赖 python3 / nodejs 等运行时"
            echo ""
            printf "确认选择手动安装，不再提示吗？[y/N]: "
            read manual_confirm
            if [ "$manual_confirm" = "y" ] || [ "$manual_confirm" = "Y" ]; then
                echo "$PROVISION_SKIPPED" > "$MARKER"
                echo "已选择手动安装，之后进入终端不再提示。"
                break
            else
                echo "已取消，返回菜单。"
            fi
            ;;
        3)
            echo "已退出，下次进入终端仍会提示。"
            break
            ;;
        *)
            echo "${C_RED}无效输入，请重新选择。${C_RESET}"
            ;;
    esac
done
git_config
