package com.skripiio.imagespark;

public interface ImageLoaderListener {
	
	public void onImageLoaded(int pStateLevel);
	public void onImageLoadFailed(int pStateLevel);
}
