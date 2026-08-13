package com.aicode.feature.agent.presentation.component

import com.aicode.feature.agent.domain.model.ChatSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 侧边栏会话时间分组的边界验证：今天 / 昨天 / 7天内 / 30天内 / 更早按月。
 */
class SessionGroupKeyTest {

    private val zone = ZoneId.systemDefault()

    /** 固定“当前时间”：2026-08-14 12:00（本地时区）。 */
    private val now = ZonedDateTime.of(2026, 8, 14, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    /** 距 now 往前 [daysAgo] 天的上午 10 点。 */
    private fun at(daysAgo: Long): Long =
        ZonedDateTime.of(2026, 8, 14, 10, 0, 0, 0, zone)
            .minusDays(daysAgo)
            .toInstant()
            .toEpochMilli()

    private fun session(id: String, updatedAt: Long) =
        ChatSession(id = id, title = "t$id", createdAt = 0L, updatedAt = updatedAt)

    @Test
    fun groupKey_updatedToday_isToday() {
        assertEquals("today", sessionGroupKey(at(0), now))
    }

    @Test
    fun groupKey_futureTimestamp_isToday() {
        assertEquals("today", sessionGroupKey(at(-1), now))
    }

    @Test
    fun groupKey_oneDayAgo_isYesterday() {
        assertEquals("yesterday", sessionGroupKey(at(1), now))
    }

    @Test
    fun groupKey_twoToSevenDaysAgo_isLast7Days() {
        assertEquals("7d", sessionGroupKey(at(2), now))
        assertEquals("7d", sessionGroupKey(at(7), now))
    }

    @Test
    fun groupKey_eightToThirtyDaysAgo_isLast30Days() {
        assertEquals("30d", sessionGroupKey(at(8), now))
        assertEquals("30d", sessionGroupKey(at(30), now))
    }

    @Test
    fun groupKey_overThirtyDays_isMonth() {
        assertEquals("2026-07", sessionGroupKey(at(31), now))
        assertEquals("2026-06", sessionGroupKey(at(45), now))
    }

    @Test
    fun groupKey_crossYear_keepsYearMonth() {
        val ts = ZonedDateTime.of(2025, 12, 1, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("2025-12", sessionGroupKey(ts, now))
    }

    @Test
    fun buildEntries_insertsHeaderPerGroupInOrder() {
        val sessions = listOf(
            session("s1", at(0)), // 今天
            session("s2", at(1)), // 昨天
            session("s3", at(3)), // 7天内
            session("s4", at(20)), // 30天内
            session("s5", at(60)), // 月份 2026-06
            session("s6", at(90)) // 月份 2026-05
        )

        val entries = buildSessionEntries(sessions, now)

        val keys = entries.map { it.key }
        assertEquals(
            listOf("header-today", "s1", "header-yesterday", "s2", "header-7d", "s3", "header-30d", "s4", "header-2026-06", "s5", "header-2026-05", "s6"),
            keys
        )
    }

    @Test
    fun buildEntries_sameGroupKeepsSessionOrder() {
        val sessions = listOf(
            session("s1", at(0)),
            session("s2", at(0)),
            session("s3", at(0))
        )

        val entries = buildSessionEntries(sessions, now)

        assertEquals(listOf("header-today", "s1", "s2", "s3"), entries.map { it.key })
    }

    @Test
    fun buildEntries_emptyList_isEmpty() {
        assertEquals(emptyList<SessionListEntry>(), buildSessionEntries(emptyList(), now))
    }

    @Test
    fun buildEntries_headerAnchorsToFirstSessionOfGroup() {
        val sessions = listOf(
            session("s1", at(0)),
            session("s2", at(1)),
            session("s3", at(2))
        )

        val entries = buildSessionEntries(sessions, now)

        val header = entries[0] as SessionListEntry.Header
        assertEquals("today", header.groupKey)
        assertEquals("s1", header.anchorSession.id)
        val yesterdayHeader = entries[2] as SessionListEntry.Header
        assertEquals("yesterday", yesterdayHeader.groupKey)
        assertEquals("s2", yesterdayHeader.anchorSession.id)
    }
}
