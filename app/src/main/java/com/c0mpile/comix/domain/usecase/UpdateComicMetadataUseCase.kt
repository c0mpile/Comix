package com.c0mpile.comix.domain.usecase

import com.c0mpile.comix.domain.model.SingleComicMetadataUpdate
import com.c0mpile.comix.domain.repository.LibraryRepository
import javax.inject.Inject

/** Applies a metadata update through the library repository. */
class UpdateComicMetadataUseCase @Inject constructor(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(update: SingleComicMetadataUpdate): Result<Unit> =
        libraryRepository.updateComicMetadata(update)
}
