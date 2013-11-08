package com.skripiio.imagespark;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import com.skripiio.imagespark.cache.memory.LruMemoryCache;
import com.skripiio.imagespark.cache.memory.MemoryCache;
import com.skripiio.imagespark.util.BitmapDecoder;
import com.skripiio.imagespark.util.BitmapDownloader;
import com.skripiio.imagespark.util.CompatibleAsyncTask;
import com.skripiio.imagespark.util.Utils;

public class ImageLoader {

	private static final String TAG = "ImageLoader";

	private Context mContext;

	/** Memory Cache */
	private MemoryCache mMemoryCache;

	/** Disk Cache */
	private File mDiskCacheDir;
	public static final int DISK_CACHE_SIZE_IN_MB = 20;

	/**
	 * The Level Threshold is used to determine whether an object should be
	 * stored in memory even if it's not being displayed. The threshold reveals
	 * the lowest level to be stored in memory, any level after the threshold
	 * will only be stored in local memory if it's being displayed in an
	 * imageview. This is used for eager lazy loading.
	 */
	private int mLevelThreshold = 1;

	private boolean mExitTasksEarly = false;

	/** Loading Bitmap */
	private Bitmap mLoadingBitmap;

	private ArrayList<Integer> mLevelsToCancel;

	/**
	 * Thread Pool Executors TODO: Abstract to allow for multiple pools based on
	 * user options
	 */
	private ThreadPoolExecutor mThreadPool;
	private ArrayBlockingQueue<Runnable> mQueue;
	private static final int THREAD_POOL_CORE_SIZE = 3;
	private static final int THREAD_POOL_MAX_SIZE = 3;
	private static final int THREAD_POOL_KEEP_ALIVE_IN_SECONDS = 100;

	private ThreadPoolExecutor mLargeDecoderThreadPool;
	private ArrayBlockingQueue<Runnable> mLargeDecoderQueue;
	private static final int THREAD_POOL_LARGE_DECODER_CORE_SIZE = 3;
	private static final int THREAD_POOL_LARGE_DECODER_MAX_SIZE = 3;
	private static final int THREAD_POOL_LARGE_DECODER_KEEP_ALIVE_IN_SECONDS = 100;

	private String mHttpDiskCacheName;
	private int mHttpDiskCacheSizeInMb;

	public class ImageLoaderOptions {

		public ImageLoaderOptions() {
			// memory cache size in percent [integer 30 - 90]
			// levels to cancel [integer array]
			// download disk cache name
			// download disk cache size
			// decoded disk cache name
			// decoded disk cache size
			// level threshold
		}
	}

	public ImageLoader(Context pContext, Bitmap pLoadingBitmap,
			String pHttpDiskCacheName, int pDiskCacheSizeInMb) {
		mContext = pContext;
		mHttpDiskCacheName = pHttpDiskCacheName;
		mHttpDiskCacheSizeInMb = pDiskCacheSizeInMb;
		mTasks = new ArrayList<ImageLoader.BitmapLevelListAsyncTask>();
		mLevelsToCancel = new ArrayList<Integer>();
		mLevelsToCancel.add(2);
		mMemoryCache = new LruMemoryCache(mContext, 15);
		mLoadingBitmap = pLoadingBitmap;
		mDiskCacheDir = Utils.getDiskCacheDir(pContext, "ImageSpark_Cache");

		mQueue = new ArrayBlockingQueue<Runnable>(800, true);

		mThreadPool = new ThreadPoolExecutor(THREAD_POOL_CORE_SIZE,
				THREAD_POOL_MAX_SIZE, THREAD_POOL_KEEP_ALIVE_IN_SECONDS,
				TimeUnit.MILLISECONDS, mQueue);

		mLargeDecoderQueue = new ArrayBlockingQueue<Runnable>(800, true);

		mLargeDecoderThreadPool = new ThreadPoolExecutor(
				THREAD_POOL_LARGE_DECODER_CORE_SIZE,
				THREAD_POOL_LARGE_DECODER_MAX_SIZE,
				THREAD_POOL_LARGE_DECODER_KEEP_ALIVE_IN_SECONDS,
				TimeUnit.MILLISECONDS, mLargeDecoderQueue);
	}

	public void setExitTasksEarly(boolean pExitTasksEarly) {
		mExitTasksEarly = pExitTasksEarly;
	}

	public void cancelAllTasks() {
		for (int i = 0; i < mTasks.size(); i++) {
			// these can be cancelled concurrently if the user is quick. so be
			// careful
			if (mTasks.size() > i)
				mTasks.get(i).cancel(true);
			if (mTasks.size() > i)
				mTasks.get(i).detachImageView();
			if (mTasks.size() > i)
				mQueue.remove(mTasks.get(i));
			if (mTasks.size() > i)
				mLargeDecoderQueue.remove(mTasks.get(i));
		}

		for (int i = mTasks.size() - 1; i >= 0; i--) {
			mTasks.remove(i);
		}
	}

	/**
	 * Loads an image into memory without associating it with an ImageView.
	 * Downloads both urls, storing all urls in the disk cache, but only
	 * decoding the smallest index in the memory cache
	 */
	public void loadImage(Map<String, Integer> pLoadLevelMap, int pImageViewSize) {
		loadImage(null, pLoadLevelMap, pImageViewSize);
	}

	/**
	 * Loads an image into an imageView. Defaults the ImageViewSize to the size
	 * of the users screen. Levels should be greater than 0. As 0 is the loading
	 * bitmap
	 */
	public void loadImage(ImageView pImageView,
			Map<String, Integer> mLoadLevelMap) {
		// TODO get size of screen
		loadImage(pImageView, mLoadLevelMap, 1024);
	}

	/** Cancels work for a given imageview */
	public void cancelWork(ImageView pImageView) {
		ArrayList<BitmapLevelListAsyncTask> tasks = getTask(pImageView);
		if (tasks.size() != 0) {
			for (int i = 0; i < tasks.size(); i++) {
				cancelTask(tasks.get(i));
			}
		}
	}

	/** Cancels a task and removes it from the executor queue */
	private void cancelTask(BitmapLevelListAsyncTask pTask) {
		pTask.cancel(true);
		pTask.detachImageView();
		mTasks.remove(pTask);
		mQueue.remove(pTask);
		mLargeDecoderQueue.remove(pTask);
	}

	/** Flushe's a URL from the memory cache */
	public void flushFromMem(String pUrl) {
		mMemoryCache.remove(pUrl);
	}

	/**
	 * Checks the Maps attached to an ImageView. If the maps are identicle, it
	 * means the ImageView is loading the same tasks that you are requesting. If
	 * the maps are different, it cancels the tasks attached to the ImageView.
	 * 
	 * @return true if the urls are equivalent and nothing should happen
	 */
	private boolean checkImageViewMaps(ImageView pImageView,
			Map<String, Integer> pLoadLevelMap) {
		Drawable d = pImageView.getDrawable();
		if (d != null) {
			if (d instanceof AsyncBitmapDrawable) {
				AsyncBitmapDrawable drawable = (AsyncBitmapDrawable) d;
				Map<String, Integer> imageViewLevels = drawable.getUrlLevels();

				// if the urls are the equivalent
				if (pLoadLevelMap.keySet().equals(imageViewLevels.keySet())) {
					return true;
				} else {
					// urls are not equivalent.
					ArrayList<BitmapLevelListAsyncTask> tasks = getTask(pImageView);
					for (BitmapLevelListAsyncTask task : tasks) {
						if (task.isCancellable()) {
							cancelTask(task);
						} else {
							task.detachImageView();
						}
					}
				}
			}
		}

		return false;
	}

	public void loadImage(ImageView pImageView,
			Map<String, Integer> pLoadLevelMap, int pImageViewSize) {
		loadImage(pImageView, pLoadLevelMap, pImageViewSize, null);
	}

	/**
	 * Load a series of images. Levels should be greater than 0, as 0 is the
	 * loading bitmap
	 */
	public void loadImage(ImageView pImageView,
			Map<String, Integer> pLoadLevelMap, int pImageViewSize,
			final ImageLoaderListener pListener) {
		if (pImageView != null) {
			checkImageViewMaps(pImageView, pLoadLevelMap);
		}

		// sort the map highest index first
		ArrayList<String> urls = Utils.SortUrlsHighIndexFirst(pLoadLevelMap);
		// make sure none are null
		for (String url : urls) {
			if (url == null) {
				Log.e("PhotoHub", "Image URL null!");
				pListener.onImageLoadFailed(-1);
				return;
			}
		}

		ArrayList<BitmapLevelListAsyncTask> runningTasks = new ArrayList<ImageLoader.BitmapLevelListAsyncTask>();

		String memCacheBitmapUrl = null;
		Bitmap memCacheBitmap = null;
		int memCacheLevel = 0;

		// check memory cache for urls, starting from highest index
		for (String url : urls) {
			memCacheBitmap = getImageFromMemCache(url);

			if (memCacheBitmap != null) {
				// if bitmap is found in a memory cache, stop trying to load
				// smaller images and load the image in
				memCacheBitmapUrl = url;
				memCacheLevel = pLoadLevelMap.get(url);
				break;

			} else {
				// everytime memcache is not found:
				// check our task pool if there is a current task trying to
				// load the url
				BitmapLevelListAsyncTask task = getTask(url);

				if (task != null) {
					// if there is, reference the task for later use
					// cancelTask(task);
					// note: commented out, as since position 0 always got
					// re-initialized by the gridview, it was
					// cancelling out the correct imageview.
				}

				// if not, create a new task and reference
				// check if that particular state level should be
				// cancellable
				boolean cancellable = false;
				if (mLevelsToCancel.contains(pLoadLevelMap.get(url))) {
					cancellable = true;
				}

				BitmapLevelListAsyncTask newTask;
				if (pImageView != null) {
					newTask = new BitmapLevelListAsyncTask(pImageView, url,
							pImageViewSize, pLoadLevelMap.get(url),
							cancellable, pListener);
				} else {
					newTask = new BitmapLevelListAsyncTask(url, pImageViewSize,
							pLoadLevelMap.get(url), cancellable, pListener);
				}

				// IMPORTANT TODO: calling execute while the BUFFER IS FULL WILL
				// BLOCK THIS THREAD
				// start running the new task
				if (cancellable) {
					newTask.executeOnExecutor(mLargeDecoderThreadPool);
					// if (pListener != null) {
					// pListener.onImageLoaded(memCacheLevel);
					// }
				} else {
					newTask.executeOnExecutor(mThreadPool);
					// if (pListener != null) {
					// pListener.onImageLoaded(memCacheLevel);
					// }
				}

				mTasks.add(newTask);
				runningTasks.add(newTask);

			}
		}

		// create new BitmapLevelListDrawable with task references & map, and
		// place in ImageView.

		// create the weak references
		ArrayList<WeakReference<BitmapLevelListAsyncTask>> weakReferenceTasks = new ArrayList<WeakReference<BitmapLevelListAsyncTask>>();
		for (BitmapLevelListAsyncTask task : runningTasks) {
			weakReferenceTasks
					.add(new WeakReference<ImageLoader.BitmapLevelListAsyncTask>(
							task));
			task.detachImageView(); // ensure no rogue imageviews are attached
									// to the task
			if (pImageView != null) {
				task.attachImageView(pImageView);
			}
		}

		if (pImageView != null) {
			AsyncBitmapDrawable drawable = new AsyncBitmapDrawable(
					mContext.getResources(), mLoadingBitmap,
					weakReferenceTasks, pLoadLevelMap, 0);

			pImageView.setImageDrawable(drawable);

			// if a memcache bitmap was found, place that in the imageview at
			// it's
			// index.
			if (memCacheBitmap != null) {
				setImageBitmap(pImageView, memCacheBitmap,
						pLoadLevelMap.get(memCacheBitmapUrl));
				if (pListener != null) {
					pListener.onImageLoaded(memCacheLevel);
				}

			}
		}
	}

	private ArrayList<BitmapLevelListAsyncTask> mTasks;

	/**
	 * @return an Array of BitmapAsyncTasks attached to a specific ImageView. We
	 *         are returning an array because we could have the big image
	 *         loading and the small image at the same time
	 */
	private ArrayList<BitmapLevelListAsyncTask> getTask(ImageView pImageView) {
		ArrayList<BitmapLevelListAsyncTask> tasks = new ArrayList<ImageLoader.BitmapLevelListAsyncTask>();
		for (BitmapLevelListAsyncTask task : mTasks) {
			if (task.isImageViewAttached()) {
				ImageView attachedView = task.mImageViewReference.get();
				if (attachedView != null) {
					if (attachedView == pImageView) {
						tasks.add(task);
					}
				}
			}
		}

		return tasks;
	}

	/**
	 * @return a BitmapAsyncTask finding a particular URL. Returns null if the
	 *         task does not exist
	 */
	private BitmapLevelListAsyncTask getTask(String pUrl) {
		for (BitmapLevelListAsyncTask task : mTasks) {
			if (task != null) {
				if (!task.isCancelled()) {
					if (task.getUrl().equals(pUrl)) {
						return task;
					}
				}
			}
		}
		return null;
	}

	/**
	 * Checks the memory cache for the image. If the image is in the memory
	 * cache. It sets it in the ImageView
	 * 
	 * @return the bitmap
	 */
	private Bitmap getImageFromMemCache(String pUrl) {
		Bitmap bitmap = null;

		// check memory cache for large images
		bitmap = mMemoryCache.get(pUrl);
		if (bitmap != null) {
			return bitmap;
		}

		return null;

	}

	/**
	 * TODO replace AsyncDrawable with this. Since there are multiple URLs to
	 * load
	 */
	public class AsyncBitmapDrawable extends BitmapDrawable {

		ArrayList<WeakReference<BitmapLevelListAsyncTask>> mTasks;

		private Map<String, Integer> mUrlLevels;

		private int mLevel;

		public AsyncBitmapDrawable(Resources pResources, Bitmap pBitmap,
				ArrayList<WeakReference<BitmapLevelListAsyncTask>> pTasks,
				Map<String, Integer> pUrlLevels, int pCurrentLevel) {
			super(pResources, pBitmap);
			mTasks = pTasks;
			mUrlLevels = new HashMap<String, Integer>();
			mUrlLevels.putAll(pUrlLevels);
			mLevel = pCurrentLevel;
		}

		public Map<String, Integer> getUrlLevels() {
			return mUrlLevels;
		}

		public int getCurrentLevel() {
			return mLevel;
		}

		public void recycle() {
			if (mLevel != 0) {
				// recycle all
				for (String url : mUrlLevels.keySet()) {
					mMemoryCache.remove(url);
				}
				if (getBitmap() != null) {
					getBitmap().recycle();
				}
			}
		}

	}

	/**
	 * Sets a transitiondrawable's state with a bitmap. If the index being
	 * inserted into the ImageBitmap is greater than the current level set on
	 * the bitmap, the level will be changed
	 */
	private void setImageBitmap(final ImageView pImageView, Bitmap pBitmap,
			int pIndex) {
		Drawable imageDrawable = pImageView.getDrawable();

		if (imageDrawable instanceof AsyncBitmapDrawable) {

			AsyncBitmapDrawable drawable = (AsyncBitmapDrawable) imageDrawable;

			if (drawable.getCurrentLevel() < pIndex) {

				drawable = new AsyncBitmapDrawable(mContext.getResources(),
						pBitmap, drawable.mTasks, drawable.mUrlLevels, pIndex);

				pImageView.setImageDrawable(drawable);
			}

		}
	}

	// public static DiskLruCache mCache;
	//
	// public static DiskLruCache getCache(File pDir) {
	// if (mCache == null) {
	// try {
	// mCache = DiskLruCache.open(pDir, 0, 1,
	// DISK_CACHE_SIZE_IN_MB * 1024 * 1024);
	// } catch (IOException e) {
	// e.printStackTrace();
	// }
	// }
	//
	// return mCache;
	// }

	public int mTaskNums = 0;

	public class BitmapLevelListAsyncTask extends
			CompatibleAsyncTask<Void, Void, Bitmap> {

		private WeakReference<ImageView> mImageViewReference;
		private boolean mCancellable = false;
		private String mUrl;
		private int mImageSize;
		private int mStateLevel;
		private int mTaskNumber;
		private ImageLoaderListener mListener;

		/**
		 * Creates an AsyncTask. If no ImageView has been set by the time the
		 * bitmap is placed in a DiskCache (or if the bitmap is already in the
		 * DiskCache and no ImageView is set).
		 * 
		 * @param pIsCancellable
		 *            If true, the image will not be placed in the memory cache
		 *            unless there is an ImageView present by the time the
		 *            loading is complete
		 */
		public BitmapLevelListAsyncTask(String pUrl, int pImageSize,
				int pStateLevel, boolean pIsCancellable) {
			mStateLevel = pStateLevel;
			mImageSize = pImageSize;
			mCancellable = pIsCancellable;
			mUrl = pUrl;
			mImageViewReference = new WeakReference<ImageView>(null);
			mTaskNumber = mTaskNums + 1;
			mTaskNums++;
		}

		public BitmapLevelListAsyncTask(String pUrl, int pImageSize,
				int pStateLevel, boolean pIsCancellable,
				ImageLoaderListener pListener) {
			mStateLevel = pStateLevel;
			mImageSize = pImageSize;
			mCancellable = pIsCancellable;
			mUrl = pUrl;
			mImageViewReference = new WeakReference<ImageView>(null);
			mTaskNumber = mTaskNums + 1;
			mTaskNums++;
			mListener = pListener;
		}

		/**
		 * Creates an AsyncTask to load into an ImageView
		 * 
		 * @param pImageView
		 *            the ImageView to load the bitmap into
		 */
		public BitmapLevelListAsyncTask(ImageView pImageView, String pUrl,
				int pImageSize, int pStateLevel, boolean pIsCancellable) {
			mStateLevel = pStateLevel;
			mImageSize = pImageSize;
			mCancellable = pIsCancellable;
			mUrl = pUrl;
			mImageViewReference = new WeakReference<ImageView>(pImageView);
			mTaskNumber = mTaskNums + 1;
			mTaskNums++;
		}

		public BitmapLevelListAsyncTask(ImageView pImageView, String pUrl,
				int pImageSize, int pStateLevel, boolean pIsCancellable,
				ImageLoaderListener pListener) {
			mStateLevel = pStateLevel;
			mImageSize = pImageSize;
			mCancellable = pIsCancellable;
			mUrl = pUrl;
			mImageViewReference = new WeakReference<ImageView>(pImageView);
			mTaskNumber = mTaskNums + 1;
			mTaskNums++;
			mListener = pListener;
		}

		long start;
		long finish;

		@Override
		protected void onPreExecute() {
			// put in loading thing
			start = System.currentTimeMillis();
		}

		private boolean checkCancelled() {
			if (!isCancelled() && !mExitTasksEarly) {
				return false;
			} else {
				mTasks.remove(this);
				return true;
			}
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			if (checkCancelled()) {
				return null;
			}
			// check the disk cache for image
			try {
				// DiskLruCache cache = getCache(mDiskCacheDir);
				// Snapshot snapshot = cache.get(mUrl);
				// if (snapshot != null) {
				// // found in disk cache
				// }

				if (checkCancelled()) {
					return null;
				}

				Bitmap godBitmap = null;
				InputStream godStream = null;

				// if (snapshot == null) {
				godStream = BitmapDownloader.downloadBitmap(mContext, mUrl,
						mHttpDiskCacheName, mHttpDiskCacheSizeInMb);

				if (godStream == null) {
					// something went wrong with the network to download the
					// bitmap

					return null;

				}

				if (checkCancelled()) {
					return null;
				}

				// duplicate inputstream for caching and returning
				// byte[] content = Utils
				// .getByteArrayFromInputStream(godStream);

				// once downloaded, decode it
				godBitmap = BitmapDecoder.decodeSampledBitmapFromFile(
						godStream, mImageSize, mImageSize);
				godStream.close();
				// put in disk cache
				// if (godBitmap != null) {
				// ByteArrayOutputStream bos = new ByteArrayOutputStream();
				// godBitmap.compress(CompressFormat.PNG, 100, bos);
				//
				// byte[] bitmapdata = bos.toByteArray();
				// ByteArrayInputStream bs = new ByteArrayInputStream(
				// bitmapdata);
				// cache.put(mCache, mUrl, bs);
				// }
				// } else {
				// final BufferedInputStream buffIn = new BufferedInputStream(
				// snapshot.getInputStream(0), Utils.IO_BUFFER_SIZE);
				// godBitmap = BitmapDecoder.decodeSampledBitmapFromFile(
				// buffIn, mImageSize, mImageSize);
				// snapshot.close();
				// }

				// once decoded, check if ImageView is attached
				if (godBitmap != null) {
					if (isImageViewAttached()) {

						mMemoryCache.put(mUrl, godBitmap);

					} else {
						// if the url is below the level threshold, put it into
						// memory cache
						if (mStateLevel <= mLevelThreshold) {
							// mMemoryCache.put(mUrl, godBitmap);
						}
					}
				}

				return godBitmap;

			} catch (IOException e) {
				Log.v(TAG + " Task", "AsyncTask " + mTaskNumber
						+ " - Failed to open Disk Cache! " + e.getMessage());
				e.printStackTrace();
			} catch (OutOfMemoryError e) {
				e.printStackTrace();
			}

			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {

			// check if ImageView is still there
			if (isImageViewAttached()) {
				ImageView v = mImageViewReference.get();
				setImageBitmap(v, result, mStateLevel);
				if (mListener != null) {
					mListener.onImageLoaded(mStateLevel);
				}
			} else {
				result = null;
			}

			mTasks.remove(this);

			if (result == null) {
				if (mListener != null) {
					mListener.onImageLoadFailed(mStateLevel);
				}
			} else {
				if (mListener != null) {
					mListener.onImageLoaded(mStateLevel);
				}
			}
		}

		public String getUrl() {
			return mUrl;
		}

		public void attachImageView(ImageView pImageView) {
			mImageViewReference = new WeakReference<ImageView>(pImageView);
		}

		/** @return true if an ImageView is attached */
		private boolean isImageViewAttached() {
			if (mImageViewReference.get() != null) {
				return true;
			} else {
				return false;
			}
		}

		/** Detaches an ImageView */
		public void detachImageView() {
			mImageViewReference = new WeakReference<ImageView>(null);
		}

		public boolean isCancellable() {
			return mCancellable;
		}

	}
}
