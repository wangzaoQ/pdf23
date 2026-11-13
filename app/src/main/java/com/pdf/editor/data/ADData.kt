package com.pdf.editor.data

data class ADData(
    var adShowCount: Int = 0,
    var adClickCount: Int = 0,
    val pr_new_launch: MutableList<ADRequestData>? = null,
    val pr_launch: MutableList<ADRequestData>? = null,
    val pr_ban1: MutableList<ADRequestData>? = null,
    val pr_ban2: MutableList<ADRequestData>? = null,
    val pr_user_use: MutableList<ADRequestData>? = null,
    val pr_exit: MutableList<ADRequestData>? = null,
    val pr_new_int: MutableList<ADRequestData>? = null,
)

data class ADRequestData(
    val jsk: String = "",
    val iwk: String = "",
    val iwn: String = "",
    val isk: Int = 0,
    val ipn: Int = 0,
    val grp: List<Int> = emptyList(),
)
