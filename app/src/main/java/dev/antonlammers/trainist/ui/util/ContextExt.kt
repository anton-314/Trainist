package dev.antonlammers.trainist.ui.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/** Unwraps a (possibly wrapped) Compose [Context] to the hosting [Activity], or null. */
tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
