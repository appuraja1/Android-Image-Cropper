package com.github.appuraja1.cropper;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Lightweight, crash-free background task to crop bitmaps off the UI thread.
 * Uses modern Java Concurrency (ExecutorService) replacing deprecated AsyncTask.
 */
final class BitmapCroppingWorkerTask implements Runnable {

  // Single worker queue for crop tasks to avoid thrashing CPU/Memory
  private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
  private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

  private final WeakReference<CropImageView> mCropImageViewReference;
  private final Context mAppContext;
  private final Bitmap mBitmap;
  private final Uri mUri;
  private final float[] mCropPoints;
  private final int mDegreesRotated;
  private final int mOrgWidth;
  private final int mOrgHeight;
  private final boolean mFixAspectRatio;
  private final int mAspectRatioX;
  private final int mAspectRatioY;
  private final int mReqWidth;
  private final int mReqHeight;
  private final boolean mFlipHorizontally;
  private final boolean mFlipVertically;
  private final CropImageView.RequestSizeOptions mReqSizeOptions;
  private final Uri mSaveUri;
  private final Bitmap.CompressFormat mSaveCompressFormat;
  private final int mSaveCompressQuality;

  private Future<?> mRunningTask;
  private volatile boolean mIsCancelled = false;

  BitmapCroppingWorkerTask(
          CropImageView cropImageView,
          Bitmap bitmap,
          float[] cropPoints,
          int degreesRotated,
          boolean fixAspectRatio,
          int aspectRatioX,
          int aspectRatioY,
          int reqWidth,
          int reqHeight,
          boolean flipHorizontally,
          boolean flipVertically,
          CropImageView.RequestSizeOptions options,
          Uri saveUri,
          Bitmap.CompressFormat saveCompressFormat,
          int saveCompressQuality) {

    this.mCropImageViewReference = new WeakReference<>(cropImageView);
    this.mAppContext = cropImageView.getContext().getApplicationContext(); // Safe from Activity leaks
    this.mBitmap = bitmap;
    this.mCropPoints = cropPoints;
    this.mUri = null;
    this.mDegreesRotated = degreesRotated;
    this.mFixAspectRatio = fixAspectRatio;
    this.mAspectRatioX = aspectRatioX;
    this.mAspectRatioY = aspectRatioY;
    this.mReqWidth = reqWidth;
    this.mReqHeight = reqHeight;
    this.mFlipHorizontally = flipHorizontally;
    this.mFlipVertically = flipVertically;
    this.mReqSizeOptions = options;
    this.mSaveUri = saveUri;
    this.mSaveCompressFormat = saveCompressFormat;
    this.mSaveCompressQuality = saveCompressQuality;
    this.mOrgWidth = 0;
    this.mOrgHeight = 0;
  }

  BitmapCroppingWorkerTask(
          CropImageView cropImageView,
          Uri uri,
          float[] cropPoints,
          int degreesRotated,
          int orgWidth,
          int orgHeight,
          boolean fixAspectRatio,
          int aspectRatioX,
          int aspectRatioY,
          int reqWidth,
          int reqHeight,
          boolean flipHorizontally,
          boolean flipVertically,
          CropImageView.RequestSizeOptions options,
          Uri saveUri,
          Bitmap.CompressFormat saveCompressFormat,
          int saveCompressQuality) {

    this.mCropImageViewReference = new WeakReference<>(cropImageView);
    this.mAppContext = cropImageView.getContext().getApplicationContext();
    this.mUri = uri;
    this.mCropPoints = cropPoints;
    this.mDegreesRotated = degreesRotated;
    this.mFixAspectRatio = fixAspectRatio;
    this.mAspectRatioX = aspectRatioX;
    this.mAspectRatioY = aspectRatioY;
    this.mOrgWidth = orgWidth;
    this.mOrgHeight = orgHeight;
    this.mReqWidth = reqWidth;
    this.mReqHeight = reqHeight;
    this.mFlipHorizontally = flipHorizontally;
    this.mFlipVertically = flipVertically;
    this.mReqSizeOptions = options;
    this.mSaveUri = saveUri;
    this.mSaveCompressFormat = saveCompressFormat;
    this.mSaveCompressQuality = saveCompressQuality;
    this.mBitmap = null;
  }

  public Uri getUri() {
    return mUri;
  }

  /** Starts background execution */
  public void start() {
    mRunningTask = EXECUTOR.submit(this);
  }

  /** Cancels background execution safely */
  public void cancel() {
    mIsCancelled = true;
    if (mRunningTask != null) {
      mRunningTask.cancel(true);
    }
  }

  @Override
  public void run() {
    if (mIsCancelled) return;

    Result result;
    try {
      BitmapUtils.BitmapSampled bitmapSampled;
      if (mUri != null) {
        bitmapSampled = BitmapUtils.cropBitmap(
                mAppContext,
                mUri,
                mCropPoints,
                mDegreesRotated,
                mOrgWidth,
                mOrgHeight,
                mFixAspectRatio,
                mAspectRatioX,
                mAspectRatioY,
                mReqWidth,
                mReqHeight,
                mFlipHorizontally,
                mFlipVertically);
      } else if (mBitmap != null) {
        bitmapSampled = BitmapUtils.cropBitmapObjectHandleOOM(
                mBitmap,
                mCropPoints,
                mDegreesRotated,
                mFixAspectRatio,
                mAspectRatioX,
                mAspectRatioY,
                mFlipHorizontally,
                mFlipVertically);
      } else {
        result = new Result((Bitmap) null, 1);
        postResult(result);
        return;
      }

      if (mIsCancelled) {
        if (bitmapSampled != null && bitmapSampled.bitmap != null) {
          bitmapSampled.bitmap.recycle();
        }
        return;
      }

      Bitmap bitmap = BitmapUtils.resizeBitmap(bitmapSampled.bitmap, mReqWidth, mReqHeight, mReqSizeOptions);

      if (mSaveUri == null) {
        result = new Result(bitmap, bitmapSampled.sampleSize);
      } else {
        BitmapUtils.writeBitmapToUri(mAppContext, bitmap, mSaveUri, mSaveCompressFormat, mSaveCompressQuality);
        if (bitmap != null) {
          bitmap.recycle();
        }
        result = new Result(mSaveUri, bitmapSampled.sampleSize);
      }
    } catch (Exception e) {
      result = new Result(e, mSaveUri != null);
    }

    final Result finalResult = result;
    postResult(finalResult);
  }

  private void postResult(Result result) {
    MAIN_HANDLER.post(() -> {
      boolean completeCalled = false;
      if (!mIsCancelled) {
        CropImageView cropImageView = mCropImageViewReference.get();
        if (cropImageView != null) {
          completeCalled = true;
          cropImageView.onImageCroppingAsyncComplete(result);
        }
      }
      if (!completeCalled && result != null && result.bitmap != null) {
        // Prevent memory leaks if View is destroyed before task completes
        result.bitmap.recycle();
      }
    });
  }

  // region: Result Class

  static final class Result {
    public final Bitmap bitmap;
    public final Uri uri;
    final Exception error;
    final boolean isSave;
    final int sampleSize;

    Result(Bitmap bitmap, int sampleSize) {
      this.bitmap = bitmap;
      this.uri = null;
      this.error = null;
      this.isSave = false;
      this.sampleSize = sampleSize;
    }

    Result(Uri uri, int sampleSize) {
      this.bitmap = null;
      this.uri = uri;
      this.error = null;
      this.isSave = true;
      this.sampleSize = sampleSize;
    }

    Result(Exception error, boolean isSave) {
      this.bitmap = null;
      this.uri = null;
      this.error = error;
      this.isSave = isSave;
      this.sampleSize = 1;
    }

    public Exception getError() {
      return error;
    }
  }
  // endregion
}