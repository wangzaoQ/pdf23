package com.pdf.editor.common.tools

import android.view.View

fun View.setOneClick(action: (View) -> Unit) {
    setOnClickListener {
        action.invoke(it)
    }
}
