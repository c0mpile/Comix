package com.c0mpile.comix.domain.usecase

import android.app.Activity
import android.content.Intent
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Completes a pending Google Drive OAuth consent resolution flow from the activity result intent.
 */
class CompleteDriveAuthUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    suspend operator fun invoke(activity: Activity, data: Intent?): Result<DriveAccount> =
        driveRepository.completeAuthorizationFromIntent(activity, data)
}
