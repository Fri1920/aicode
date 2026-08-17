package com.aicode.feature.settings.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderBalanceRunnerTest {

    private val subscriptionJson = """
        {
          "items": [
            {
              "label": "5h",
              "suffix": "余量",
              "percent": 80,
              "used": 4.0,
              "total": 5.0,
              "unit": "小时",
              "color": "#10B981"
            },
            {
              "label": "7d",
              "suffix": "余量",
              "percent": 0.65,
              "used": 4.6,
              "total": 7.0,
              "unit": "天",
              "color": "#3B82F6"
            }
          ]
        }
    """.trimIndent()

    private val balanceJson = """
        {
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
              "statusDot": true,
              "color": "#10B981"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun testParseSubscriptionJson() {
        val result = ProviderBalanceRunner.parseBalanceJson(subscriptionJson)
        assertEquals(2, result.items.size)

        val item1 = result.items[0]
        assertEquals("5h", item1.label)
        assertEquals("余量", item1.suffix)
        assertEquals(80f, item1.percent ?: 0f, 0.01f)
        assertEquals(4.0, item1.used ?: 0.0, 0.01)
        assertEquals(5.0, item1.total ?: 0.0, 0.01)
        assertEquals("4 / 5 小时", item1.displaySubText)
        assertTrue(item1.hasProgress)

        val item2 = result.items[1]
        assertEquals("7d", item2.label)
        assertEquals(65f, item2.percent ?: 0f, 0.01f)
        assertEquals("4.6 / 7 天", item2.displaySubText)
        assertTrue(item2.hasProgress)
    }

    @Test
    fun testParseBalanceJson() {
        val result = ProviderBalanceRunner.parseBalanceJson(balanceJson)
        assertEquals(2, result.items.size)

        val item1 = result.items[0]
        assertEquals("当前余额", item1.label)
        assertEquals("$12.45", item1.displayValue)
        assertEquals("≈ ¥89.32 CNY", item1.displaySubText)
        assertEquals("余额 $12.45", item1.compactText)
        assertFalse(item1.hasProgress)
        assertFalse(item1.statusDot)

        val item2 = result.items[1]
        assertEquals("本月消费", item2.label)
        assertEquals("$7.55", item2.displayValue)
        assertEquals("今日消费 $0.83", item2.displaySubText)
        assertTrue(item2.statusDot)
        assertFalse(item2.hasProgress)
    }
}
