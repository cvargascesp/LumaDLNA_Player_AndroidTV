package cl.androiddlna

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache

class LumaApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("dlna-artwork"))
                .maxSizeBytes(500L * 1024 * 1024)
                .build()
        }
        // DLNA servers may omit cache headers or require revalidation on every request.
        .respectCacheHeaders(false)
        .build()
}
