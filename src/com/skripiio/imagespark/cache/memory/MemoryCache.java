package com.skripiio.imagespark.cache.memory;

import android.graphics.Bitmap;

public interface MemoryCache {

	/** Clears the cache of everything */
	public boolean clearCache();

	/** @return a bitmap for the current key */
	public Bitmap get(String pKey);

	/** Puts a bitmap into the cache with key pKey */
	public void put(String pKey, Bitmap pBitmap);

	/** @return the size of the cache in number of entries */
	public int getSize();

}
