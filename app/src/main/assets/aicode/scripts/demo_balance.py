#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AiCode 余额查询示例脚本 (demo_balance.py) - 余额制模板

本脚本演示如何为 AiCode 提供商返回余额/消费数据。
支持返回 1~3 项卡片数据（自适应排布）：
- label: 标题 (如 "当前余额", "本月消费", "累计充值")
- value: 金额/数值 (如 "$12.45", "$7.55", "$20.00")
- subText: 底部副文案 (如 "≈ ¥89.32 CNY", "今日消费 $0.83", "最近充值 2025-07-26")
- statusDot: 是否显示状态圆点 (True/False)
- color: 高亮/状态颜色 (如 "#10B981")
- compactText: 收起状态显示的摘要 (可选)
"""

import json
import os
import sys

def main():
    # 从环境变量读取当前提供商信息
    provider_name = os.environ.get("AICODE_PROVIDER_NAME", "OpenAI")
    api_key = os.environ.get("AICODE_PROVIDER_API_KEY", "")
    base_url = os.environ.get("AICODE_PROVIDER_BASE_URL", "")

    # 模拟从接口返回的余额与消费数据
    data = {
        "items": [
            {
                "label": "当前余额",
                "value": "$12.45",
                "subText": "≈ ¥89.32 CNY",
                "compactText": "余额 $12.45",
                "color": "#10B981"
            },
            {
                "label": "本月消费",
                "value": "$7.55",
                "subText": "今日消费 $0.83",
                "compactText": "余额充足",
                "statusDot": True,
                "color": "#10B981"
            },
            {
                "label": "累计充值",
                "value": "$20.00",
                "subText": "最近充值 2025-07-26",
                "color": "#3B82F6"
            }
        ]
    }

    print(json.dumps(data, ensure_ascii=False))

if __name__ == "__main__":
    main()
