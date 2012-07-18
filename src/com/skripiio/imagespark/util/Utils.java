package com.skripiio.imagespark.util;

import java.util.ArrayList;
import java.util.Map;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Build;

public class Utils {
	public static final int IO_BUFFER_SIZE = 8 * 1024;

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
		// TODO

		return null;
	}
}
