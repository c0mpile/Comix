package com.c0mpile.comix.domain.usecase

import android.app.Activity
import com.c0mpile.comix.domain.model.DriveAccount
import com.c0mpile.comix.domain.repository.DriveRepository
import javax.inject.Inject

/**
 * Initiates interactive Google Drive sign-in and OAuth scope authorization.
 */
class SignInToDriveUseCase @Inject constructor(
    private val driveRepository: DriveRepository
) {
    suspend operator fun invoke(activity: Activity): Result<DriveAccount> =
        driveRepository.signIn(activity)
}
