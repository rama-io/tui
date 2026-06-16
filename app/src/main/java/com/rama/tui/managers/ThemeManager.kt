package com.rama.tui.managers

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import com.rama.tui.R

//object ThemeManager {


//    private fun applyToView(
//        context: Context,
//        view: View,
//        palette: Palette,
//        typeface: android.graphics.Typeface?
//    ) {
//        // Icon tinting, ImageView src drawables use @color/* fill colors which don't
//        // update automatically when the palette changes. We apply an imageTintList so
//        // the color is remapped through the same mapColor logic used everywhere else.
//        if (view is ImageView) {
//            val currentTint = view.imageTintList?.defaultColor
//            if (currentTint != null) {
//                val mapped = mapColor(context, currentTint, palette)
//                if (mapped != null) {
//                    view.imageTintList = android.content.res.ColorStateList.valueOf(mapped)
//                }
//            } else {
//                // No tint set yet, seed from the drawable's fill color resource so
//                // subsequent theme switches can remap it correctly.
//                val seedColor = resolveDrawableFillColor(context, view) ?: return
//                val mapped = mapColor(context, seedColor, palette) ?: seedColor
//                view.imageTintList = android.content.res.ColorStateList.valueOf(mapped)
//            }
//            return
//        }
//
//        // Font + text color
//        if (view is TextView) {
//            typeface?.let { view.typeface = it }
//            when (view) {
//                is RadioButton, is CheckBox -> {
//                    // Apply foreground text color
//                    view.setTextColor(palette.foreground)
//                    // Apply accent tint to the button drawable (the circle/tick)
//                    val tintList = android.content.res.ColorStateList(
//                        arrayOf(
//                            intArrayOf(android.R.attr.state_checked),
//                            intArrayOf(-android.R.attr.state_checked)
//                        ),
//                        intArrayOf(palette.accent_1, palette.disabled)
//                    )
//                    view.buttonTintList = tintList
//                }
//
//                else -> {
//                    // Only remap if we recognise the color, don't blindly overwrite
//                    // with foreground, as that would clobber clock/icon/header text colors
//                    val mapped = mapColor(context, view.currentTextColor, palette)
//                    if (mapped != null) view.setTextColor(mapped)
//                }
//            }
//        }
//
//        // Background
//        val currentColor = resolveDrawableColor(view.background ?: return) ?: return
//        val mapped = mapColor(context, currentColor, palette) ?: return
//        view.setBackgroundColor(mapped)
//    }


//    private fun resolveDrawableColor(drawable: android.graphics.drawable.Drawable): Int? {
//        return if (drawable is ColorDrawable) drawable.color else null
//    }

//    /**
//     * Reads the tint color seeded by android:tint in the layout XML.
//     * Returns null if no tint has been set (icon will be skipped this pass).
//     */
//    private fun resolveDrawableFillColor(context: Context, view: ImageView): Int? {
//        // android:tint in XML is exposed as imageTintList, but we already handle
//        // the tintList != null case before calling this. This path is only reached
//        // when no tint is set at all, which shouldn't happen once the layouts are
//        // updated. Return null so we skip safely rather than guess.
//        return null
//    }
//}