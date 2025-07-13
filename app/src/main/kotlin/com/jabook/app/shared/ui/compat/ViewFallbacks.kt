package com.jabook.app.shared.ui.compat

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.jabook.app.shared.ui.components.ButtonVariant
import com.jabook.app.shared.ui.components.EmptyStateType
import com.jabook.app.shared.ui.components.JaBookButton
import com.jabook.app.shared.ui.components.JaBookEmptyState
import com.jabook.app.shared.ui.theme.JaBookTheme

/** System fallback for compatibility with Android API 23+ Automatically selects between Compose and View based on device support */
object ViewFallbacks {
    /** Checks support for Compose on this device */
    fun isComposeSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP // API 21+
    }

    /** Creates a button with automatic selection between Compose and View */
    fun createButton(context: Context, text: String, onClick: () -> Unit, variant: ButtonVariant = ButtonVariant.Primary): View {
        return if (isComposeSupported()) {
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { JaBookTheme { JaBookButton(text = text, onClick = onClick, variant = variant) } }
            }
        } else {
            createLegacyButton(context, text, onClick, variant)
        }
    }

    /** Creates loading/error state with automatic selection */
    fun createEmptyState(
        context: Context,
        state: EmptyStateType,
        title: String? = null,
        subtitle: String? = null,
        actionButton: (() -> Unit)? = null,
    ): View {
        return if (isComposeSupported()) {
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    JaBookTheme {
                        JaBookEmptyState(
                            state = state,
                            title = title,
                            subtitle = subtitle,
                            actionButton =
                            if (actionButton != null) {
                                {
                                    JaBookButton(
                                        text =
                                        when (state) {
                                            EmptyStateType.NetworkError -> "Повторить"
                                            EmptyStateType.GeneralError -> "Попробовать снова"
                                            else -> "Обновить"
                                        },
                                        onClick = actionButton,
                                    )
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        } else {
            createLegacyEmptyState(context, state, title, subtitle, actionButton)
        }
    }

    /** Creates legacy button for older Android versions */
    private fun createLegacyButton(context: Context, text: String, onClick: () -> Unit, variant: ButtonVariant): Button {
        return Button(context).apply {
            this.text = text
            setOnClickListener { onClick() }

            // Apply style based on variant
            when (variant) {
                ButtonVariant.Primary -> {
                    setBackgroundColor(context.getColor(android.R.color.holo_blue_bright))
                    setTextColor(context.getColor(android.R.color.white))
                }
                ButtonVariant.Secondary -> {
                    setBackgroundColor(context.getColor(android.R.color.transparent))
                    setTextColor(context.getColor(android.R.color.holo_blue_bright))
                }
                ButtonVariant.Text -> {
                    setBackgroundColor(context.getColor(android.R.color.transparent))
                    setTextColor(context.getColor(android.R.color.holo_blue_bright))
                }
                ButtonVariant.Danger -> {
                    setBackgroundColor(context.getColor(android.R.color.holo_red_light))
                    setTextColor(context.getColor(android.R.color.white))
                }
            }

            // Set padding and sizes
            setPadding(48, 32, 48, 32)
            minimumHeight = 120 // 48dp in pixels
        }
    }

    /** Creates legacy state for older Android versions */
    private fun createLegacyEmptyState(
        context: Context,
        state: EmptyStateType,
        title: String?,
        subtitle: String?,
        actionButton: (() -> Unit)?,
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(64, 64, 64, 64)

            // Icon or loading indicator
            if (state == EmptyStateType.Loading) {
                addView(
                    ProgressBar(context).apply {
                        layoutParams = LinearLayout.LayoutParams(120, 120)
                        isIndeterminate = true
                    }
                )
            } else {
                addView(
                    ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(160, 160)
                        // Here you can add icons for different states
                        setImageResource(android.R.drawable.ic_dialog_info)
                    }
                )
            }

            // Title
            addView(
                TextView(context).apply {
                    text = title ?: state.defaultTitle
                    textSize = 18f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 32, 0, 16)
                }
            )

            // Subtitle
            addView(
                TextView(context).apply {
                    text = subtitle ?: state.defaultSubtitle
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                    setPadding(0, 0, 0, 32)
                }
            )

            // Action button
            if (actionButton != null) {
                addView(
                    createLegacyButton(
                        context,
                        when (state) {
                            EmptyStateType.NetworkError -> "Повторить"
                            EmptyStateType.GeneralError -> "Попробовать снова"
                            else -> "Обновить"
                        },
                        actionButton,
                        ButtonVariant.Primary,
                    )
                )
            }
        }
    }

    /** Creates container for compatibility with automatic technology selection */
    @Composable
    fun CompatContainer(content: @Composable () -> Unit) {
        // If
        content()
    }

    /** Utilities for compatibility */
    object Utils {
        /** Gets the correct color for the current Android version */
        fun getCompatColor(context: Context, colorRes: Int): Int {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.getColor(colorRes)
            } else {
                // For Android 6.0 we use the old API without @Suppress
                context.resources.getColor(colorRes)
            }
        }

        /** Applies compatible styles to View */
        fun applyCompatStyles(view: View, context: Context) {
            // Apply elevation for API 21+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.elevation = 8f
            }

            // Apply rounded corners for API 21+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                view.clipToOutline = true
            }
        }

        /** Creates adaptive layout for different screen sizes */
        fun createAdaptiveLayout(context: Context): ViewGroup {
            return LinearLayout(context).apply {
                orientation =
                    if (isTablet(context)) {
                        LinearLayout.HORIZONTAL
                    } else {
                        LinearLayout.VERTICAL
                    }
            }
        }

        /** Checks if the device is a tablet */
        private fun isTablet(context: Context): Boolean {
            return context.resources.configuration.smallestScreenWidthDp >= 600
        }
    }
}

/** Extension for simplifying creation of fallback View */
fun ViewGroup.addCompatView(context: Context, createCompose: @Composable () -> Unit, createView: () -> View) {
    val view =
        if (ViewFallbacks.isComposeSupported()) {
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent { JaBookTheme { createCompose() } }
            }
        } else {
            createView()
        }

    addView(view)
}
