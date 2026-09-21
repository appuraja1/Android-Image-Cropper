package com.github.appuraja1.cropper;

import android.content.Context;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;

import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Lightweight, crash-free background task to load and decode bitmaps off the UI thread.
 * Replaces deprecated AsyncTask with ExecutorService and Handler.
 */
final class BitmapLoadingWorkerTask implements Runnable {

  private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
  private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

  /** Use a WeakReference to ensure the ImageView can be garbage collected */
  private final WeakReference<CropImageView> mCropImageViewReference;

  /** The Android URI of the image to load */
  private final Uri mUri;

  /** Safe application context to prevent Activity/Context leaks */
  private final Context mAppContext;

  /** required width of the cropping image after density adjustment */
  private final int mWidth;

  /** required height of the cropping image after density adjustment */
  private final int mHeight;

  private Future<?> mRunningTask;
  private volatile boolean mIsCancelled = false;

  public BitmapLoadingWorkerTask(CropImageView cropImageView, Uri uri) {
    this.mUri = uri;
    this.mCropImageViewReference = new WeakReference<>(cropImageView);
    this.mAppContext = cropImageView.getContext().getApplicationContext();

    DisplayMetrics metrics = cropImageView.getResources().getDisplayMetrics();
    double densityAdj = metrics.density > 1 ? 1 / metrics.density : 1;
    this.mWidth = (int) (metrics.widthPixels * densityAdj);
    this.mHeight = (int) (metrics.heightPixels * densityAdj);
  }

  /** The Android URI that this task is currently loading. */
  public Uri getUri() {
    return mUri;
  }

  /** Starts asynchronous decoding */
  public void start() {
    mRunningTask = EXECUTOR.submit(this);
  }

  /** Cancels execution safely */
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
      BitmapUtils.BitmapSampled decodeResult =
              BitmapUtils.decodeSampledBitmap(mAppContext, mUri, mWidth, mHeight);

      if (mIsCancelled) {
        if (decodeResult != null && decodeResult.bitmap != null) {
          decodeResult.bitmap.recycle();
        }
        return;
      }

      BitmapUtils.RotateBitmapResult rotateResult =
              BitmapUtils.rotateBitmapByExif(decodeResult.bitmap, mAppContext, mUri);

      if (mIsCancelled) {
        if (rotateResult != null && rotateResult.bitmap != null) {
          rotateResult.bitmap.recycle();
        }
        return;
      }

      result = new Result(
              mUri, rotateResult.bitmap, decodeResult.sampleSize, rotateResult.degrees);
    } catch (Exception e) {
      result = new Result(mUri, e);
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
          cropImageView.onSetImageUriAsyncComplete(result);
        }
      }
      if (!completeCalled && result != null && result.bitmap != null) {
        // Fast release of unused bitmap to avoid memory leaks
        result.bitmap.recycle();
      }
    });
  }

  // region: Inner class: Result

  /** The result of BitmapLoadingWorkerTask async loading. */
  public static final class Result {

    /** The Android URI of the image to load */
    public final Uri uri;

    /** The loaded bitmap */
    public final Bitmap bitmap;

    /** The sample size used to load the given bitmap */
    public final int loadSampleSize;

    /** The degrees the image was rotated */
    public final int degreesRotated;

    /** The error that occurred during async bitmap loading. */
    public final Exception error;

    Result(Uri uri, Bitmap bitmap, int loadSampleSize, int degreesRotated) {
      this.uri = uri;
      this.bitmap = bitmap;
      this.loadSampleSize = loadSampleSize;
      this.degreesRotated = degreesRotated;
      this.error = null;
    }

    Result(Uri uri, Exception error) {
      this.uri = uri;
      this.bitmap = null;
      this.loadSampleSize = 0;
      this.degreesRotated = 0;
      this.error = error;
    }

    public Exception getError() {
      return error;
    }
  }
  // endregion
}