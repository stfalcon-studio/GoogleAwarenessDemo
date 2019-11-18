package com.stfalcon.awarenessdemo.extensions

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Created by Anton Bevza on 2019-05-06.
 */

private const val commentPostedFormat = "dd.MM hh:mm:ss"

fun Date.toDateTimeFormat(): String {
    return SimpleDateFormat(commentPostedFormat, Locale.ENGLISH).format(this)
}