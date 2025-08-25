package com.jabook.app.shared.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.autoMirrored.filled.Sort
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Компонент для отображения фильтров и сортировки Поддерживает адаптивную компоновку для разных размеров экрана */
@Composable
fun JaBookFilterChips(
  filters: List<FilterChip>,
  onFilterChange: (String, Boolean) -> Unit,
  sortOptions: List<SortOption>,
  currentSort: String,
  onSortChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  showSortIcon: Boolean = true,
) {
  Row(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Иконка фильтра (опционально)
    if (showSortIcon) {
      Icon(
        imageVector = Icons.Default.FilterList,
        contentDescription = "Фильтры",
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
      )
    }

    // Список фильтров
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
      items(filters) { filter ->
        FilterChip(
          selected = filter.isSelected,
          onClick = { onFilterChange(filter.key, !filter.isSelected) },
          label = { Text(text = filter.label, style = MaterialTheme.typography.bodyMedium) },
          leadingIcon =
            if (filter.isSelected) {
              { Icon(imageVector = Icons.Default.Check, contentDescription = "Выбрано", modifier = Modifier.size(16.dp)) }
            } else {
              null
            },
          colors =
            FilterChipDefaults.filterChipColors(
              selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
              selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
              selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        )
      }
    }

    // Сортировка
    if (sortOptions.isNotEmpty()) {
      Box {
        AssistChip(
          onClick = {
            // Циклический переход между опциями сортировки
            val currentIndex = sortOptions.indexOfFirst { it.key == currentSort }
            val nextIndex = (currentIndex + 1) % sortOptions.size
            onSortChange(sortOptions[nextIndex].key)
          },
          label = {
            Text(
              text = sortOptions.find { it.key == currentSort }?.label ?: "Сортировка",
              style = MaterialTheme.typography.bodyMedium,
            )
          },
          leadingIcon = {
            Icon(imageVector = Icons.AutoMirrored.filled.Sort, contentDescription = "Сортировка", modifier = Modifier.size(16.dp))
          },
          colors =
            AssistChipDefaults.assistChipColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
              labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
              leadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
      }
    }
  }
}

/** Модель для фильтра */
data class FilterChip(
  val key: String,
  val label: String,
  val isSelected: Boolean = false,
)

/** Модель для опции сортировки */
data class SortOption(
  val key: String,
  val label: String,
)

/** Простая версия фильтров только для категорий */
@Composable
fun JaBookCategoryChips(
  categories: List<String>,
  selectedCategory: String?,
  onCategorySelect: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyRow(
    modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    // Чип "Все"
    item {
      FilterChip(
        selected = selectedCategory == null,
        onClick = { onCategorySelect(null) },
        label = { Text(text = "Все", style = MaterialTheme.typography.bodyMedium) },
        leadingIcon =
          if (selectedCategory == null) {
            { Icon(imageVector = Icons.Default.Check, contentDescription = "Выбрано", modifier = Modifier.size(16.dp)) }
          } else {
            null
          },
        colors =
          FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
      )
    }

    // Чипы категорий
    items(categories) { category ->
      FilterChip(
        selected = selectedCategory == category,
        onClick = { onCategorySelect(category) },
        label = { Text(text = category, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon =
          if (selectedCategory == category) {
            { Icon(imageVector = Icons.Default.Check, contentDescription = "Выбрано", modifier = Modifier.size(16.dp)) }
          } else {
            null
          },
        colors =
          FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
          ),
      )
    }
  }
}

/** Предустановленные фильтры для аудиокниг */
object AudiobookFilters {
  val STATUS_FILTERS =
    listOf(
      FilterChip("downloaded", "Загружено"),
      FilterChip("downloading", "Загружается"),
      FilterChip("favorites", "Избранное"),
      FilterChip("completed", "Прослушано"),
    )

  val SORT_OPTIONS =
    listOf(
      SortOption("title", "По названию"),
      SortOption("author", "По автору"),
      SortOption("date_added", "По дате добавления"),
      SortOption("progress", "По прогрессу"),
      SortOption("size", "По размеру"),
    )

  val CATEGORY_FILTERS =
    listOf("Фантастика", "Детективы", "Классика", "Современная проза", "Психология", "История", "Биография", "Детская литература")
}
