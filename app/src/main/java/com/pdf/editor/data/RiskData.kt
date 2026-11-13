package com.pdf.editor.data

data class RiskData(
    val device: List<String> = emptyList(),
    /** IP 风险是否生效,"0"代表不生效，“1”代表生效 */
    val ip: Int = 0
)
