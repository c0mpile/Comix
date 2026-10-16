package com.c0mpile.comix

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import com.c0mpile.comix.data.archive.ComicCoverFetcher
import com.c0mpile.comix.data.archive.ComicCoverKeyer
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application entry point configured for Hilt dependency injection and Coil image loading.
 */
@HiltAndroidApp
class ComixApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var coverFetcherFactory: ComicCoverFetcher.Factory

    @Inject
    lateinit var coverKeyer: ComicCoverKeyer

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(coverKeyer)
                add(coverFetcherFactory)
            }
            .build()
    }
}
