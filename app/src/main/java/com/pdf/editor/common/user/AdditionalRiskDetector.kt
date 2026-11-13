package com.pdf.editor.common.user

import android.content.Context
import android.provider.Settings

/**
 * 独立检测接口，只返回结果，由调用方决定何时调用以及如何处理。
 * 不读取远程配置，不保存风险记录，不修改身份，不上报埋点。
 */
object AdditionalRiskDetector {
    /**
     * 用途：检测 AdMob 原生广告是否为测试广告（testAd）。
     * 判断标题是否以 Test Ad 或当前语言的测试广告标识开头，匹配成功返回 RISK。
     * 调用方式：广告加载成功后传入 nativeAd.headline，同步返回，不主动请求广告。
     * 标题为空，或本地化资源缺失且英文未匹配时返回 NORMAL，与 pdf14 保持一致。
     *
     * @param headline 已加载原生广告的标题。
     */
    fun testAd(context: Context, headline: String?): RiskVerdict {
        if (headline.isNullOrEmpty()) return RiskVerdict.NORMAL
        if (headline.startsWith("test ad", true)) return RiskVerdict.RISK
        val testAdString = runCatching {
            context.getString(
                context.resources.getIdentifier("s7", "string", context.packageName)
            )
        }.getOrNull() ?: ""
        return if (testAdString.isNotEmpty() && headline.startsWith(testAdString)) {
            RiskVerdict.RISK
        } else {
            RiskVerdict.NORMAL
        }
    }

    /**
     * 用途：检测设备是否带有 Firebase Test Lab 自动化测试环境标记（testLab）。
     * 调用方式：直接调用，同步返回；读取的系统设置键为 firebase.test.lab。
     * 同步读取 Firebase Test Lab 的环境标记，不等同于模拟器检测或测试广告检测。
     * 两个设置表独立读取，某个表读取异常不影响另一个表；任一为 true 即命中。
     * 都未读到 true 时按未命中处理，与参考插件保持一致。
     */
    fun testLab(context: Context): RiskVerdict {
        val system = runCatching {
            Settings.System.getString(context.contentResolver, "firebase.test.lab")
        }.getOrNull()
        val secure = runCatching {
            Settings.Secure.getString(context.contentResolver, "firebase.test.lab")
        }.getOrNull()
        return if (system.equals("true", true) || secure.equals("true", true)) {
            RiskVerdict.RISK
        } else RiskVerdict.NORMAL
    }

}
