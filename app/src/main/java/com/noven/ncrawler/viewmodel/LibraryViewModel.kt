package com.noven.ncrawler.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noven.ncrawler.NCrawlerApp
import com.noven.ncrawler.data.db.DownloadProgress
import com.noven.ncrawler.data.db.NovelEntity
import com.noven.ncrawler.data.db.ReadingProgress
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class LibraryItem(
    val novel: NovelEntity,
    val readingProgress: ReadingProgress?,
    val downloadProgress: DownloadProgress?
)

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as NCrawlerApp).repository

    val libraryItems: StateFlow<List<LibraryItem>> = combine(
        repo.libraryFlow(),
        repo.allReadingProgressFlow(),
        repo.allDownloadProgressFlow()
    ) { novels, readingList, downloadList ->
        novels.map { novel ->
            LibraryItem(
                novel           = novel,
                readingProgress = readingList.find { it.novelSlug == novel.slug },
                downloadProgress = downloadList.find { it.novelSlug == novel.slug }
            )
        }
    }.stateIn(
        scope         = viewModelScope,
        started       = SharingStarted.WhileSubscribed(5000),
        initialValue  = emptyList()
    )

    fun removeFromLibrary(slug: String) {
        viewModelScope.launch {
            repo.setLibrary(slug, false)
        }
    }
}
