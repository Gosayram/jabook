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

package com.jabook.app.jabook.compose.feature.library

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.view.drawToBitmap
import com.jabook.app.jabook.R
import java.io.File

public fun shareYearRecap(
    context: Context,
    stats: YearRecapState,
) {
    val bitmap = renderYearRecapBitmap(context, stats)
    val outputFile = File(context.cacheDir, "year_recap_${stats.year}.png")
    outputFile.outputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
    }

    val shareUri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            outputFile,
        )

    val shareIntent =
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.yearRecapShareAction)))
}

private fun renderYearRecapBitmap(
    context: Context,
    stats: YearRecapState,
): Bitmap {
    val width = 1080
    val height = 1920
    val composeView =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            layoutParams = FrameLayout.LayoutParams(width, height)
            setContent {
                MaterialTheme {
                    YearRecapShareCard(stats = stats)
                }
            }
        }

    val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
    composeView.measure(widthSpec, heightSpec)
    composeView.layout(0, 0, width, height)
    return composeView.drawToBitmap()
}

@Composable
private fun YearRecapShareCard(stats: YearRecapState) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    brush =
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        ),
                ),
    ) {
        Column(
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 56.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(32.dp),
                    ).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = stringResource(R.string.yearRecapTitle, stats.year),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.yearRecapSubtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            YearRecapMetric(
                label = stringResource(R.string.yearRecapMinutesLabel),
                value = stats.totalMinutesListened.toString(),
            )
            YearRecapMetric(
                label = stringResource(R.string.yearRecapBooksLabel),
                value = stats.booksCompleted.toString(),
            )
            YearRecapMetric(
                label = stringResource(R.string.yearRecapDaysLabel),
                value = stats.activeDays.toString(),
            )
            YearRecapMetric(
                label = stringResource(R.string.yearRecapSessionsLabel),
                value = stats.sessions.toString(),
            )
            YearRecapMetric(
                label = stringResource(R.string.yearRecapTopAuthorLabel),
                value = stats.topAuthor,
            )
        }
    }
}

@Composable
private fun YearRecapMetric(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
