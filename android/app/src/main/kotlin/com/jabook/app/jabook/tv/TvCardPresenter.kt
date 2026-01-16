// Copyright 2026 Jabook Contributors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.jabook.app.jabook.tv

import android.view.ViewGroup
import androidx.leanback.widget.ImageCardView
import androidx.leanback.widget.Presenter
import coil3.load
import com.jabook.app.jabook.R
import com.jabook.app.jabook.compose.domain.model.Book
import java.io.File

/**
 * Presenter for displaying audiobook cards on Android TV.
 *
 * Uses Leanback's ImageCardView for consistent TV UI.
 */
public class TvCardPresenter : Presenter() {
    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        val cardView =
            ImageCardView(parent.context).apply {
                isFocusable = true
                isFocusableInTouchMode = true
                // Set card dimensions for TV
                setMainImageDimensions(CARD_WIDTH, CARD_HEIGHT)
                // Set default placeholder
                mainImageView?.setImageResource(R.mipmap.ic_launcher)
            }
        return ViewHolder(cardView)
    }

    override fun onBindViewHolder(
        viewHolder: ViewHolder,
        item: Any?,
    ) {
        val book = item as? Book ?: return
        val cardView = viewHolder.view as ImageCardView

        cardView.titleText = book.title
        cardView.contentText = book.author

        // Load cover image
        val coverUrl = book.coverUrl
        if (!coverUrl.isNullOrBlank()) {
            if (coverUrl.startsWith("/") || coverUrl.startsWith("file://")) {
                // Local file
                val file = File(coverUrl.removePrefix("file://"))
                if (file.exists()) {
                    cardView.mainImageView?.load(file)
                } else {
                    cardView.mainImageView?.setImageResource(R.mipmap.ic_launcher)
                }
            } else {
                // Remote URL
                cardView.mainImageView?.load(coverUrl)
            }
        } else {
            cardView.mainImageView?.setImageResource(R.mipmap.ic_launcher)
        }
    }

    override fun onUnbindViewHolder(viewHolder: ViewHolder) {
        val cardView = viewHolder.view as ImageCardView
        cardView.mainImage = null
    }

    private companion object {
        const val CARD_WIDTH = 313
        const val CARD_HEIGHT = 176
    }
}
