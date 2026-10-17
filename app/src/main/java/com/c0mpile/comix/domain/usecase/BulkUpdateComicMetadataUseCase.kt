package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.BulkComicMetadataUpdate
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/** Applies a metadata update through the library repository. */
class BulkUpdateComicMetadataUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(update: BulkComicMetadataUpdate): Result<Unit> =
        libraryRepository.bulkUpdateComicMetadata(update)
}
