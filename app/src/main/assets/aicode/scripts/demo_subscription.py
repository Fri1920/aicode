#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AiCode 套餐余量示例脚本 (demo_subscription.py) - 订阅制模板

本脚本演示如何为 AiCode 提供商返回按时间/额度周期的进度条余量数据。
支持返回 1~3 项卡片数据（自适应排布）：
- label: 周期简写 (如 "5h", "7d", "1m", "今日", "本月")
- suffix: 后缀标签 (默认 "余量")
- percent: 剩余或已用百分比 (0~100)
- used: 已用量数值 (如 4.0)
- total: 总量数值 (如 5.0)
- unit: 单位 (如 "小时", "天", "tokens")
- subText: 详情文字 (如 "4.0 / 5.0 小时")
- color: 进度条高亮颜色 (如 "#10B981")
"""

import json
import os
import sys

def main():
    # 模拟从接口返回的 5h / 7d / 1m 订阅余量
    data = {
        "items": [
            {
                "label": "5h",
                "suffix": "余量",
                "percent": 80,
                "used": 4.0,
                "total": 5.0,
                "unit": "小时",
                "subText": "4.0 / 5.0 小时",
                "color": "#10B981"
            },
            {
                "label": "7d",
                "suffix": "余量",
                "percent": 65,
                "used": 4.6,
                "total": 7.0,
                "unit": "天",
                "subText": "4.6 / 7.0 天",
                "color": "#3B82F6"
            },
            {
                "label": "1m",
                "suffix": "余量",
                "percent": 42,
                "used": 12.6,
                "total": 30.0,
                "unit": "天",
                "subText": "12.6 / 30.0 天",
                "color": "#8B5CF6"
            }
        ]
    }

    print(json.dumps(data, ensure_ascii=False))

if __name__ == "__main__":
    main()
