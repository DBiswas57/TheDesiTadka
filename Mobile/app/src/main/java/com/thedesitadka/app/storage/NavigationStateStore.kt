package com.thedesitadka.app.storage

import com.thedesitadka.core.model.Category
import com.thedesitadka.core.model.VideoItem
import java.util.concurrent.ConcurrentHashMap

/**
 * Retains list state across forward and back navigation.
 * Preserves loaded items, pagination page, scroll position, and filters
 * to eliminate redundant network requests and scroll resetting when pressing Back.
 */
data class SavedListState(
    val items: List<VideoItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val currentPage: Int = 1,
    val hasNextPage: Boolean = true,
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
    val selectedCategory: Category? = null,
    val searchQuery: String = ""
)

object NavigationStateStore {
    private val stateCache = ConcurrentHashMap<String, SavedListState>()

    fun get(key: String): SavedListState? = stateCache[key]

    fun save(key: String, state: SavedListState) {
        stateCache[key] = state
    }

    fun updateScroll(key: String, index: Int, offset: Int) {
        val existing = stateCache[key]
        if (existing != null) {
            stateCache[key] = existing.copy(
                firstVisibleItemIndex = index,
                firstVisibleItemScrollOffset = offset
            )
        }
    }

    fun updateItems(
        key: String,
        items: List<VideoItem>,
        page: Int,
        hasNext: Boolean,
        categories: List<Category> = emptyList(),
        selectedCategory: Category? = null,
        searchQuery: String = ""
    ) {
        val existing = stateCache[key]
        if (existing != null) {
            stateCache[key] = existing.copy(
                items = items,
                currentPage = page,
                hasNextPage = hasNext,
                categories = if (categories.isNotEmpty()) categories else existing.categories,
                selectedCategory = selectedCategory ?: existing.selectedCategory,
                searchQuery = if (searchQuery.isNotEmpty()) searchQuery else existing.searchQuery
            )
        } else {
            stateCache[key] = SavedListState(
                items = items,
                categories = categories,
                currentPage = page,
                hasNextPage = hasNext,
                selectedCategory = selectedCategory,
                searchQuery = searchQuery
            )
        }
    }

    fun clear(key: String) {
        stateCache.remove(key)
    }

    fun clearAll() {
        stateCache.clear()
    }
}
