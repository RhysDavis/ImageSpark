package com.skripiio.imagespark.cache.memory;

import java.lang.reflect.Method;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.util.LruCache;
import android.util.Log;

public class LruMemoryCache implements MemoryCache {
	private LruCache<String, Bitmap> mCache;

	private static final String TAG = "LruMemoryCache";

	public LruMemoryCache(int pSizeInMb) {
		initializeLruCache(1024 * 1024 * pSizeInMb);
	}

	/** Creates a memory cache with the size of pSizeInPercent */
	public LruMemoryCache(Context pContext, int pSizeInPercent) {
		int memClass = 0;
		ActivityManager am = ((ActivityManager) pContext
				.getSystemService(Context.ACTIVITY_SERVICE));
		try {
			Method m = ActivityManager.class.getMethod("getMemoryClass");
			memClass = (Integer) m.invoke(am);
		} catch (Exception e) {
		}

		// auto-correct percent if greater than 80 or less than 0
		if (pSizeInPercent < 0) {
			pSizeInPercent = 0;
		}

		if (pSizeInPercent > 80) {
			pSizeInPercent = 80;
		}
		int capacity = (int) ((1024f * 1024f * (float) (memClass * pSizeInPercent)) / 100f);
		if (capacity <= 0) {
			capacity = 1024 * 1024 * 4;
		}

		initializeLruCache(capacity);
	}

	public boolean isInCache(String pKey) {
		if (mCache.get(pKey) != null) {
			return true;
		}

		return false;
	}

	/**
	 * Creates the LruCache. Trashes it and reinitializes it if it's already
	 * being used.
	 * 
	 * @param pCapacity
	 *            capacity of the cache in bytes
	 */
	public void initializeLruCache(int pCapacity) {

		mCache = new LruCache<String, Bitmap>(pCapacity) {
			@Override
			protected int sizeOf(String key, Bitmap bitmap) {
				return bitmap.getRowBytes() * bitmap.getHeight();
			}

			@Override
			protected void entryRemoved(boolean evicted, String key,
					Bitmap oldValue, Bitmap newValue) {
				super.entryRemoved(evicted, key, oldValue, newValue);

			}

		};
	}

	@Override
	public Bitmap get(String pKey) {
		if (pKey == null) {
			return null;
		}
		Bitmap b;
		synchronized (mCache) {
			b = mCache.get(pKey);
			if (b != null) {
				if (b.isRecycled()) {
					return null;
				}
			}
		}
		return b;
	}

	@Override
	public void put(String pKey, Bitmap pBitmap) {

		if (pKey == null || pBitmap == null) {
			Log.w(TAG, "Invalid Parameters in method Put");
			return;
		}

		synchronized (mCache) {
			if (mCache.get(pKey) == null) {
				mCache.put(pKey, pBitmap);
			}
		}
	}

	/** Outputs the size of the cache to the log */
	@Override
	public int getSize() {
		return mCache.size();
	}

	@Override
	public boolean clearCache() {
		mCache.evictAll();
		if (mCache.size() == 0) {
			return true;
		} else
			return false;
	}

	@Override
	public boolean remove(String pKey) {
		if (mCache.remove(pKey) != null) {
			return true;
		}
		return false;
	}
}