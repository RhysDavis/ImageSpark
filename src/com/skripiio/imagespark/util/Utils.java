package com.skripiio.imagespark.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Environment;

public class Utils {
	public static final int IO_BUFFER_SIZE = 8 * 1024;

	public static byte[] getByteArrayFromInputStream(InputStream pInputStream)
			throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[1024];
		int n = 0;
		// error - unexpected end of strem on line below
		while ((n = pInputStream.read(buf)) >= 0)
			baos.write(buf, 0, n);

		return baos.toByteArray();
	}

	/**
	 * Get the size in bytes of a bitmap.
	 * 
	 * @param bitmap
	 * @return size in bytes
	 */
	@SuppressLint("NewApi")
	public static int getBitmapSize(Bitmap bitmap) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
			return bitmap.getByteCount();
		}
		// Pre HC-MR1
		return bitmap.getRowBytes() * bitmap.getHeight();
	}

	/** @return an ArrayList<String> or URLs ordered with the highest index first */
	public static ArrayList<String> SortUrlsHighIndexFirst(
			Map<String, Integer> pLevelList) {
		Map<String, Integer> map = sortByValue(pLevelList);
		ArrayList<String> values = new ArrayList<String>();
		for (String key : map.keySet()) {
			values.add(key);
		}

		// reverse values
		ArrayList<String> rightValues = new ArrayList<String>();
		for (int i = values.size() - 1; i >= 0; i--) {
			rightValues.add(values.get(i));
		}

		return rightValues;
	}

	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(
			Map<K, V> map) {
		List<Map.Entry<K, V>> list = new LinkedList<Map.Entry<K, V>>(
				map.entrySet());
		Collections.sort(list, new Comparator<Map.Entry<K, V>>() {
			public int compare(Map.Entry<K, V> o1, Map.Entry<K, V> o2) {
				return (o1.getValue()).compareTo(o2.getValue());
			}
		});

		Map<K, V> result = new LinkedHashMap<K, V>();
		for (Map.Entry<K, V> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	/**
	 * Get a usable cache directory (external if available, internal otherwise).
	 * 
	 * @param context
	 *            The context to use
	 * @param uniqueName
	 *            A unique directory name to append to the cache dir
	 * @return The cache dir
	 */
	public static File getDiskCacheDir(Context context, String uniqueName) {
		String state = Environment.getExternalStorageState();
		String cachePath;
		if (Environment.MEDIA_MOUNTED.equals(state) && Utils.getExternalCacheDir(context) != null) {
			cachePath = Utils.getExternalCacheDir(context).getPath();
		}  else {
			cachePath = context.getCacheDir().getPath();
		}

		return new File(cachePath + File.separator + uniqueName);
	}

	/**
	 * Check if external storage is built-in or removable.
	 * 
	 * @return True if external storage is removable (like an SD card), false
	 *         otherwise.
	 */
	@SuppressLint("NewApi")
	public static boolean isExternalStorageRemovable() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			return Environment.isExternalStorageRemovable();
		}
		return true;
	}

	/**
	 * Get the external app cache directory.
	 * 
	 * @param context
	 *            The context to use
	 * @return The external cache dir
	 */
	@SuppressLint("NewApi")
	public static File getExternalCacheDir(Context context) {
		return context.getExternalCacheDir();
	}
}
