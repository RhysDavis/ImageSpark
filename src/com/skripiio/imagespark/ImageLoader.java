package com.skripiio.imagespark;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Map;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LevelListDrawable;
import android.widget.ImageView;

import com.skripiio.imagespark.cache.disk.DiskLruCache;
import com.skripiio.imagespark.cache.memory.MemoryCache;
import com.skripiio.imagespark.util.CompatibleAsyncTask;
import com.skripiio.imagespark.util.Utils;

public class ImageLoader {

	private Context mContext;

	/** Memory Cache */
	private MemoryCache mMemoryCache;

	/** Disk Cache */
	private DiskLruCache mDiskCache;

	/** Loading Bitmap */
	private BitmapDrawable mLoadingBitmap;

	private ArrayList<Integer> mLevelsToCancel;

	public ImageLoader(Context pContext) {
		mContext = pContext;
		mLevelsToCancel = new ArrayList<Integer>();
	}

	/**
	 * Loads an image into memory without associating it with an ImageView.
	 * Downloads both urls, storing all urls in the disk cache, but only
	 * decoding the smallest index in the memory cache
	 */
	public void loadImage(Map<String, Integer> mLoadLevelMap, int pImageViewSize) {

	}

	/**
	 * Loads an image into an imageView. Defaults the ImageViewSize to the size
	 * of the users screen
	 */
	public void loadImage(ImageView pImageView,
			Map<String, Integer> mLoadLevelMap) {
		// TODO get size of screen
		loadImage(pImageView, mLoadLevelMap, 1024);
	}

	public boolean checkImageViewMaps(ImageView pImageView,
			Map<String, Integer> pLoadLevelMap) {
		Drawable d = pImageView.getDrawable();

		if (d instanceof BitmapLevelListDrawable) {
			BitmapLevelListDrawable drawable = (BitmapLevelListDrawable) d;
			Map<String, Integer> imageViewLevels = drawable.getUrlLevels();

			// if the urls are the equivalent
			if (pLoadLevelMap.keySet().equals(imageViewLevels.keySet())) {
				return true;
			} else {
				// urls are not equivalent.
				ArrayList<BitmapLevelListAsyncTask> tasks = getTask(pImageView);
				for (BitmapLevelListAsyncTask task : tasks) {
					if (task.isCancellable()) {
						task.cancel(true);
					} else
						task.detachImageView();
				}
			}

		}

		return false;
	}

	/** Load a series of images */
	public void loadImage(ImageView pImageView,
			Map<String, Integer> pLoadLevelMap, int pImageViewSize) {

		if (pImageView != null) {
			// check if the map matches the current map associated with the
			// imageview. If not, disassociate the imageview from the tasks, and
			// cancel cancellable tasks
			checkImageViewMaps(pImageView, pLoadLevelMap);
			// if so, the same images are being loaded into the ImageView.
			// Proceed
			// to check memcaches
		}

		// sort the map highest index first
		ArrayList<String> urls = Utils.SortUrlsHighIndexFirst(pLoadLevelMap);

		ArrayList<BitmapLevelListAsyncTask> runningTasks = new ArrayList<ImageLoader.BitmapLevelListAsyncTask>();

		String memCacheBitmapUrl = null;
		Bitmap memCacheBitmap = null;

		// check memory cache for urls, starting from highest index
		for (String url : urls) {
			memCacheBitmap = getImageFromMemCache(url);

			if (memCacheBitmap != null) {
				// if bitmap is found in a memory cache, stop trying to load
				// smaller images and load the image in
				memCacheBitmapUrl = url;
				break;

			} else {
				// everytime memcache is not found:
				// check our task pool if there is a current task trying to
				// loading the url
				BitmapLevelListAsyncTask task = getTask(url);

				if (task != null) {
					// if there is, reference the task for later use
					runningTasks.add(task);
				} else {
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
								pLoadLevelMap.get(url), cancellable);
					} else {
						newTask = new BitmapLevelListAsyncTask(url,
								pLoadLevelMap.get(url), cancellable);
					}
					// start running the new task
					newTask.execute();
					
					mTasks.add(newTask);
					runningTasks.add(newTask);
				}
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
		}

		BitmapLevelListDrawable drawable = new BitmapLevelListDrawable(
				weakReferenceTasks, pLoadLevelMap);
		pImageView.setImageDrawable(drawable);

		// if a memcache bitmap was found, place that in the imageview at it's
		// index.
		if (memCacheBitmap != null) {
			setImageBitmap(pImageView, memCacheBitmap,
					pLoadLevelMap.get(memCacheBitmapUrl));
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
			if (task.getUrl().equals(pUrl)) {
				return task;
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
	public class BitmapLevelListDrawable extends LevelListDrawable {

		ArrayList<WeakReference<BitmapLevelListAsyncTask>> mTasks;

		private Map<String, Integer> mUrlLevels;

		public BitmapLevelListDrawable(
				ArrayList<WeakReference<BitmapLevelListAsyncTask>> pTasks,
				Map<String, Integer> pUrlLevels) {
			mTasks = pTasks;
			mUrlLevels = pUrlLevels;
		}

		public Map<String, Integer> getUrlLevels() {
			return mUrlLevels;
		}

	}

	/**
	 * Sets a transitiondrawable's state with a bitmap. If the index being
	 * inserted into the ImageBitmap is greater than the current level set on
	 * the bitmap, the level will be changed
	 */
	private void setImageBitmap(ImageView pImageView, Bitmap pBitmap, int pIndex) {
		// always assume the imageview has a level-list drawable.
		Drawable imageDrawable = pImageView.getDrawable();
		if (imageDrawable instanceof BitmapLevelListDrawable) {

			BitmapLevelListDrawable drawable = (BitmapLevelListDrawable) imageDrawable;

			// add the level at the index
			drawable.addLevel(pIndex, pIndex,
					new BitmapDrawable(mContext.getResources(), pBitmap));

			if (drawable.getLevel() < pIndex) {
				drawable.setLevel(pIndex);
			}
		}
	}

	private static final int FADE_IN_TIME_IN_MILLI = 200;

	public class BitmapLevelListAsyncTask extends
			CompatibleAsyncTask<Void, Void, Bitmap> {

		private WeakReference<ImageView> mImageViewReference;

		private boolean mCancellable = false;

		private String mUrl;

		private int mState;

		public String getUrl() {
			return mUrl;
		}

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
		public BitmapLevelListAsyncTask(String pUrl, int pStateLevel,
				boolean pIsCancellable) {
			mCancellable = pIsCancellable;
			mUrl = pUrl;
			mImageViewReference = new WeakReference<ImageView>(null);
		}

		/**
		 * Creates an AsyncTask to load into an ImageView
		 * 
		 * @param pImageView
		 *            the ImageView to load the bitmap into
		 */
		public BitmapLevelListAsyncTask(ImageView pImageView, String pUrl,
				int pStateLevel, boolean pIsCancellable) {
			mCancellable = pIsCancellable;
			mUrl = pUrl;
			mImageViewReference = new WeakReference<ImageView>(pImageView);
		}

		@Override
		protected void onPreExecute() {

		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			// check the disk cache for image

			// if not in disk cache, download it

			// once downloaded, decode it

			// once decoded, check if ImageView is attached

			// if ImageView is still attached or if NOT largeImage, put in
			// memory cache

			return null;
		}

		@Override
		protected void onPostExecute(Bitmap result) {
			// check if ImageView is still there

			// if it is, place bitmap in

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

	/**
	 * An AsyncBitmap that gets placed in an ImageView to indicate that an image
	 * is loading itself there.
	 */
	public class AsyncBitmap extends BitmapDrawable {

		private final WeakReference<BitmapLevelListAsyncTask> mBitmapAsyncTaskReference;

		private boolean isCancellable = false;

		/**
		 * A class to put in an ImageView while things load
		 * 
		 * @param pResource
		 *            resources
		 * @param pBitmap
		 *            bitmap to display while it loads
		 * @param pBitmapAsyncTask
		 *            task that's loading the url for this imageview
		 * @param pIsCancellable
		 *            true if the AsyncTask should be cancelled if the ImageView
		 *            gets recycled to a different task
		 */
		public AsyncBitmap(Resources pResources, Bitmap pBitmap,
				BitmapLevelListAsyncTask pBitmapAsyncTask,
				boolean pIsCancellable) {
			super(pResources, pBitmap);

			isCancellable = pIsCancellable;

			mBitmapAsyncTaskReference = new WeakReference<ImageLoader.BitmapLevelListAsyncTask>(
					pBitmapAsyncTask);
		}

		/**
		 * @return true if the task should be cancelled if the imageview is
		 *         recycled
		 */
		public boolean isCancellable() {
			return isCancellable;
		}

		/** @return the task associated with this drawable */
		public BitmapLevelListAsyncTask getBitmapTask() {
			BitmapLevelListAsyncTask task = mBitmapAsyncTaskReference.get();

			if (task != null) {
				return task;
			}

			return null;
		}

	}

	public class CompletedBitmap extends BitmapDrawable {

	}

	public interface OnImageLoadedListener {
		public void onImageLoaded(Bitmap pBitmap);

		public void onImageFailed(String pData, String pMessage);
	}
}
