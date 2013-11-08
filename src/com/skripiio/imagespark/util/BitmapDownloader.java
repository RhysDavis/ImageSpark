package com.skripiio.imagespark.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import com.skripiio.imagespark.cache.disk.DiskLruCache;
import com.skripiio.imagespark.cache.disk.DiskLruCache.Snapshot;

public class BitmapDownloader {
	private static final String TAG = "BitmapDownloader";

	public static final int HTTP_CACHE_SIZE_IN_MB = 50;
	public static final String HTTP_CACHE_DIR = "http";

	public static DiskLruCache mCache;

	public synchronized static DiskLruCache getCache(Context pContext,
			String pCacheName, int pCacheSizeInMB) {
		if (mCache == null || mCache.isClosed()) {
			try {
				boolean external = Environment.getExternalStorageState()
						.equals(Environment.MEDIA_MOUNTED);
				File dir = Utils.getDiskCacheDir(pContext, pCacheName);

				int cacheSize = (pCacheSizeInMB * 1024 * 1024);
				// if only internal. Use 5mb of space
				if (!external) {
					cacheSize = (5 * 1024 * 1024);
				}

				mCache = DiskLruCache.open(dir, 1, 1, cacheSize);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			}
		}
		return mCache;
	}

	public static void resetCache() {
		if (mCache != null && !mCache.isClosed()) {
			try {
				mCache.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			mCache = null;
		}
	}

	/**
	 * Downloads a bitmap and returns the InputStream representing the bitmap.
	 * 
	 * @param pContext
	 *            Application Context to access data network
	 * @param pUrlString
	 *            Url of the Bitmap to download
	 * @param pCacheName
	 *            Name of the Disk Cache to access / download to
	 * @param pContentHolder
	 *            The download will store the content of the stream in the
	 *            byte[] for use
	 */
	public static InputStream downloadBitmap(Context context, String urlString,
			String pCacheName, int pCacheSizeInMb) throws IOException,
			OutOfMemoryError {

		// Access Disk Cache
		// at the moment there is a bug if the cache corrupts the download cache
		// is no longer a thing
		DiskLruCache cache = getCache(context, pCacheName, pCacheSizeInMb);
		if (cache != null) {
			Snapshot cacheSnapshot = cache.get(urlString);

			if (cacheSnapshot != null) {
				final BufferedInputStream buffIn = new BufferedInputStream(
						cacheSnapshot.getInputStream(0), Utils.IO_BUFFER_SIZE);
				return buffIn;
			}
		}
		// Download
		HttpURLConnection urlConnection = null;
		try {
			urlConnection = (HttpURLConnection) new URL(urlString)
					.openConnection();
			final InputStream in = new BufferedInputStream(
					urlConnection.getInputStream(), Utils.IO_BUFFER_SIZE);

			byte[] content = Utils.getByteArrayFromInputStream(in);

			in.close();
			if (cache != null) { // dump in cache if it exists
				InputStream cacheStream = new ByteArrayInputStream(content);
				cache.put(cache, urlString, cacheStream);
				cacheStream.close();
			}

			ByteArrayInputStream stream = new ByteArrayInputStream(content);
			return stream;

		} catch (final IOException e) {
			e.printStackTrace();
			Log.e(TAG, "\t- Error in downloadBitmap - " + e);
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
		}

		return null;
	}
}
