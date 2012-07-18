package com.skripiio.imagespark.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.util.Log;

import com.imagespark.imagespark.BuildConfig;
import com.skripiio.imagespark.cache.disk.DiskLruCache;
import com.skripiio.imagespark.cache.disk.DiskLruCache.Editor;
import com.skripiio.imagespark.cache.disk.DiskLruCache.Snapshot;

public class BitmapDownloader {
	private static final String TAG = "BitmapDownloader";

	private static final int HTTP_CACHE_SIZE_IN_MB = 10;
	public static final String HTTP_CACHE_DIR = "http";

	/**
	 * Download a bitmap from a URL, write it to a disk. This implementation
	 * uses the Android 4.0 DiskLruCache authored by JakeWharton.
	 * 
	 * @param context
	 *            The context to use
	 * @param urlString
	 *            The URL to fetch
	 * @return A File pointing to the fetched bitmap
	 * @throws IOException
	 */
	public static void downloadBitmap(Context context, String urlString)
			throws IOException {
		if (BuildConfig.DEBUG) {
			Log.d(TAG, "Requesting to download " + urlString);
		}

		DiskLruCache cache = DiskLruCache.open(new File(HTTP_CACHE_DIR), 0, 1,
				(HTTP_CACHE_SIZE_IN_MB * 1024 * 1024));

		cache.get(urlString);

		Snapshot cacheSnapshot = cache.get(urlString);

		if (cacheSnapshot != null) {
			if (BuildConfig.DEBUG) {
				Log.d(TAG, "\t- Found in HTTP cache");
			}
			return;
		}

		if (BuildConfig.DEBUG) {
			Log.d(TAG, "\t- Downloading Image...");
		}

		HttpURLConnection urlConnection = null;
		OutputStream out = null;

		try {
			final URL url = new URL(urlString);
			urlConnection = (HttpURLConnection) url.openConnection();
			final InputStream in = new BufferedInputStream(
					urlConnection.getInputStream(), Utils.IO_BUFFER_SIZE);

			// put editor into disk cache
			Editor edit = cache.edit(urlString);
			out = edit.newOutputStream(0);

			int b;
			while ((b = in.read()) != -1) {
				out.write(b);
			}

			// commit the cache
			edit.commit();

			if (BuildConfig.DEBUG) {
				Log.d(TAG,
						"\t- Image Cached. Cache "
								+ (((float) cache.size() / (float) cache
										.maxSize()) * 100) + "% full");
			}

		} catch (final IOException e) {
			Log.e(TAG, "\t- Error in downloadBitmap - " + e);
		} finally {
			if (urlConnection != null) {
				urlConnection.disconnect();
			}
			if (out != null) {
				try {
					out.close();
				} catch (final IOException e) {
					Log.e(TAG, "\t- Error in downloadBitmap - " + e);
				}
			}
		}

	}
}
