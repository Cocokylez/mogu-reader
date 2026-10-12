package com.tome.reader.domain.usecase

import android.net.Uri
import com.tome.reader.data.repository.LibraryRepository
import com.tome.reader.data.source.PageImageProvider
import com.tome.reader.domain.model.Book
import com.tome.reader.domain.model.ReadingProgress
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Action use cases. Each is a thin, single-responsibility wrapper over the
 * repository so the UI depends on intent ("import these books") rather than on
 * data-layer details. Reactive streams are observed straight from repositories.
 */

class ImportBooksUseCase(private val repository: LibraryRepository) {
    data class Result(val added: Int, val skipped: Int = 0)

    suspend operator fun invoke(files: List<Uri>, trees: List<Uri>): Result = withContext(Dispatchers.IO) {
        var added = 0
        var skipped = 0
        // A bad or inaccessible file must not cancel the rest of a batch.
        for (uri in files) {
            if (attempt { if (repository.importDocument(uri)) 1 else 0 } > 0) added++ else skipped++
        }
        for (tree in trees) {
            val count = attempt { repository.importTree(tree) }
            added += count
            if (count == 0) skipped++
        }
        Result(added, skipped)
    }

    private suspend fun attempt(block: suspend () -> Int): Int = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        0
    }
}

class DeleteBookUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(book: Book) = repository.delete(book)
}

class SaveProgressUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(bookId: Long, progress: ReadingProgress) =
        repository.saveProgress(bookId, progress)
}

class OpenImageBookUseCase(private val repository: LibraryRepository) {
    suspend operator fun invoke(book: Book): PageImageProvider = repository.openImages(book)
}
