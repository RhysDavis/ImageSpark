package com.skripiio.imagespark.cache.memory;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

import android.graphics.Bitmap;

public class WeakReferenceMemoryCache implements MemoryCache {

	private ConcurrentHashMap<String, WeakReference<Bitmap>> mCache;

	public WeakReferenceMemoryCache() {
		mCache = new ConcurrentHashMap<String, WeakReference<Bitmap>>();
	}

	@Override
	public boolean clearCache() {
		mCache.clear();
		if (mCache.size() == 0) {
			return true;
		} else
			return false;
	}

	@Override
	public Bitmap get(String pKey) {
		return null;
	}

	@Override
	public void put(String pKey, Bitmap pBitmap) {

	}

	@Override
	public int getSize() {
		return mCache.size();
	}

}
