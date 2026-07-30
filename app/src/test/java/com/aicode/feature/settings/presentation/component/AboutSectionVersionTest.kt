package com.aicode.feature.settings.presentation.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutSectionVersionTest {

    private fun compareVersions(v1: String, v2: String): Int {
        if (v1 == v2) return 0

        val split1 = splitVersion(v1)
        val split2 = splitVersion(v2)

        val parts1 = split1.first.split('.').mapNotNull { it.toIntOrNull() }
        val parts2 = split2.first.split('.').mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxLen) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }

        if (split1.second.isEmpty() && split2.second.isNotEmpty()) return 1
        if (split1.second.isNotEmpty() && split2.second.isEmpty()) return -1

        return split1.second.compareTo(split2.second)
    }

    private fun splitVersion(v: String): Pair<String, String> {
        val clean = v.substringBefore('+')
        val base = clean.substringBefore('-')
        val pre = if (clean.contains('-')) clean.substringAfter('-') else ""
        return base to pre
    }

    private fun isUpToDate(latest: String, current: String): Boolean {
        return compareVersions(latest, current) <= 0
    }

    @Test
    fun sameVersion_isUpToDate() {
        assertTrue(isUpToDate("1.7.0", "1.7.0"))
        assertTrue(isUpToDate("1.7.0-rc1", "1.7.0-rc1"))
    }

    @Test
    fun currentIsRc_latestIsRelease_needsUpdate() {
        // 当前是 1.7.0-rc1，远端发布了 1.7.0 正式版 -> 需更新
        assertFalse(isUpToDate("1.7.0", "1.7.0-rc1"))
    }

    @Test
    fun currentIsDevBuild_latestIsRc_needsUpdate() {
        // 开发版 1.7.0-dev.2+g04bc2fa，远端是 1.7.0-rc1（RC 优先于 dev） -> 需更新
        assertFalse(isUpToDate("1.7.0-rc1", "1.7.0-dev.2+g04bc2fa"))
    }

    @Test
    fun currentIsOlder_needsUpdate() {
        // 当前是 1.6.0，远端是 1.7.0 -> 需更新
        assertFalse(isUpToDate("1.7.0", "1.6.0"))
    }

    @Test
    fun currentIsNewer_isUpToDate() {
        // 当前本地已经开发 1.8.0-dev，远端最新是 1.7.0 -> 已是最新
        assertTrue(isUpToDate("1.7.0", "1.8.0-dev"))
    }
}
