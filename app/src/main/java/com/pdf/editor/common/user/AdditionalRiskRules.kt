package com.pdf.editor.common.user

import java.math.BigInteger

/** 检测结果：UNKNOWN(-1) 信息不足或失败，NORMAL(0) 未命中，RISK(1) 命中。 */
enum class RiskVerdict(val value: Int) { UNKNOWN(-1), NORMAL(0), RISK(1) }

/** 三项检测使用的纯计算规则，不读取配置，不保存状态，不处理用户身份。 */
internal object AdditionalRiskRules {
    /**
     * 检测原生广告标题是否以测试广告标识开头。
     * prefixes 为从 SDK 资源读到的当前语言标识；英文 Test Ad 始终作为兜底。
     * 没有有效本地化资源且英文未命中时，无法排除其他语言的测试广告，返回 UNKNOWN。
     */
    fun testAd(headline: String?, prefixes: List<String>): RiskVerdict {
        if (headline.isNullOrBlank()) return RiskVerdict.UNKNOWN
        val validPrefixes = prefixes.filter { it.isNotBlank() }
        if ((listOf("test ad") + validPrefixes).any { matchesPrefix(headline, it) }) {
            return RiskVerdict.RISK
        }
        return if (validPrefixes.isEmpty()) RiskVerdict.UNKNOWN else RiskVerdict.NORMAL
    }

    /**
     * 去除首尾空白并忽略大小写，只匹配标题开头。
     * 标识后必须结束或跟随空白/指定分隔符，避免把 Test Adobe 误认为 Test Ad。
     * 例如 Test Ad - Install 和 测试广告：立即安装 可以命中。
     */
    private fun matchesPrefix(headline: String, prefix: String): Boolean {
        val value = headline.trim()
        val marker = prefix.trim()
        if (!value.startsWith(marker, ignoreCase = true)) return false
        if (value.length == marker.length) return true
        val boundary = value[marker.length]
        return boundary.isWhitespace() || boundary in ":：-–—|"
    }

    /**
     * 两个时间和阈值的单位均为毫秒，比较绝对差，手机时间过快或过慢都会检查。
     * 严格超过阈值才命中：默认阈值 300000 时，相差正好 5 分钟仍算正常。
     * 用 BigInteger 计算差值，避免极端时间值在 Long 减法或取绝对值时溢出。
     */
    fun trustedTime(local: Long, trusted: Long, maxDriftMillis: Long): RiskVerdict {
        val drift = BigInteger.valueOf(trusted).subtract(BigInteger.valueOf(local)).abs()
        return if (drift > BigInteger.valueOf(maxDriftMillis.coerceAtLeast(0))) {
            RiskVerdict.RISK
        } else RiskVerdict.NORMAL
    }
}
