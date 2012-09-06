package com.skripiio.imagespark.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import android.content.Context;
import android.util.Log;

import com.imagespark.imagespark.BuildConfig;
import com.skripiio.imagespark.cache.disk.DiskLruCache;
import com.skripiio.imagespark.cache.disk.DiskLruCache.Snapshot;

public class BitmapDownloader {
	private static final String TAG = "BitmapDownloader";

	private static final int HTTP_CACHE_SIZE_IN_MB = 50;
	public static final String HTTP_CACHE_DIR = "http";

	public static DiskLruCache mCache;

	public synchronized static DiskLruCache getCache(Context pContext) {
		if (mCache == null) {
			try {
				mCache = DiskLruCache.open(
						Utils.getDiskCacheDir(pContext, HTTP_CACHE_DIR), 1, 1,
						(HTTP_CACHE_SIZE_IN_MB * 1024 * 1024));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return mCache;
	}

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
	public static InputStream downloadBitmap(Context context, String urlString)
			throws IOException, OutOfMemoryError {
		DiskLruCache cache = getCache(context);

		cache.get(urlString);
		Snapshot cacheSnapshot = cache.get(urlString);

		if (cacheSnapshot != null) {

			final BufferedInputStream buffIn = new BufferedInputStream(
					cacheSnapshot.getInputStream(0), Utils.IO_BUFFER_SIZE);

			return buffIn;
		}

		HttpURLConnection urlConnection = null;
		OutputStream out = null;

		try {
			final URL url = new URL(urlString);
			urlConnection = (HttpURLConnection) url.openConnection();
			final InputStream in = new BufferedInputStream(
					urlConnection.getInputStream(), Utils.IO_BUFFER_SIZE);

			// duplicate inputstream for caching and returning
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
			int n = 0;
			while ((n = in.read(buf)) >= 0)
				baos.write(buf, 0, n);
			byte[] content = baos.toByteArray();

			InputStream cacheStream = new ByteArrayInputStream(content);

			cache.put(cache, urlString, cacheStream);

			return new ByteArrayInputStream(content);

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

		return null;
	}
}
