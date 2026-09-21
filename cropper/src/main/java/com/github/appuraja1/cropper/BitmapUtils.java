package com.github.appuraja1.cropper;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapRegionDecoder;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.exifinterface.media.ExifInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Modern, memory-efficient utility class for Bitmap transformations, decoding, and EXIF handling.
 */
final class BitmapUtils {

  static final Rect EMPTY_RECT = new Rect();
  static final RectF EMPTY_RECT_F = new RectF();
  static final RectF RECT = new RectF();
  static final float[] POINTS = new float[6];
  static final float[] POINTS2 = new float[6];

  private static int mMaxTextureSize;
  public static Pair<String, WeakReference<Bitmap>> mStateBitmap;

  private BitmapUtils() {}

  /**
   * Rotate image by Exif orientation with automatic safe stream disposal.
   */
  public static RotateBitmapResult rotateBitmapByExif(Bitmap bitmap, Context context, Uri uri) {
    try (InputStream is = context.getContentResolver().openInputStream(uri)) {
      if (is != null) {
        ExifInterface ei = new ExifInterface(is);
        return rotateBitmapByExif(bitmap, ei);
      }
    } catch (Exception ignored) {
    }
    return new RotateBitmapResult(bitmap, 0);
  }

  public static RotateBitmapResult rotateBitmapByExif(Bitmap bitmap, ExifInterface exif) {
    int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
    int degrees;
    switch (orientation) {
      case ExifInterface.ORIENTATION_ROTATE_90:
        degrees = 90;
        break;
      case ExifInterface.ORIENTATION_ROTATE_180:
        degrees = 180;
        break;
      case ExifInterface.ORIENTATION_ROTATE_270:
        degrees = 270;
        break;
      default:
        degrees = 0;
        break;
    }
    return new RotateBitmapResult(bitmap, degrees);
  }

  public static BitmapSampled decodeSampledBitmap(Context context, Uri uri, int reqWidth, int reqHeight) {
    try {
      ContentResolver resolver = context.getContentResolver();
      BitmapFactory.Options options = decodeImageForOption(resolver, uri);

      if (options.outWidth == -1 && options.outHeight == -1) {
        throw new RuntimeException("File is not a valid picture");
      }

      options.inSampleSize = Math.max(
              calculateInSampleSizeByReqestedSize(options.outWidth, options.outHeight, reqWidth, reqHeight),
              calculateInSampleSizeByMaxTextureSize(options.outWidth, options.outHeight)
      );

      Bitmap bitmap = decodeImage(resolver, uri, options);
      return new BitmapSampled(bitmap, options.inSampleSize);
    } catch (Exception e) {
      throw new RuntimeException("Failed to load sampled bitmap: " + uri + "\r\n" + e.getMessage(), e);
    }
  }

  public static BitmapSampled cropBitmapObjectHandleOOM(
          Bitmap bitmap,
          float[] points,
          int degreesRotated,
          boolean fixAspectRatio,
          int aspectRatioX,
          int aspectRatioY,
          boolean flipHorizontally,
          boolean flipVertically) {
    int scale = 1;
    while (true) {
      try {
        Bitmap cropBitmap = cropBitmapObjectWithScale(
                bitmap, points, degreesRotated, fixAspectRatio, aspectRatioX, aspectRatioY,
                1 / (float) scale, flipHorizontally, flipVertically);
        return new BitmapSampled(cropBitmap, scale);
      } catch (OutOfMemoryError e) {
        scale *= 2;
        if (scale > 8) {
          throw e;
        }
      }
    }
  }

  private static Bitmap cropBitmapObjectWithScale(
          Bitmap bitmap,
          float[] points,
          int degreesRotated,
          boolean fixAspectRatio,
          int aspectRatioX,
          int aspectRatioY,
          float scale,
          boolean flipHorizontally,
          boolean flipVertically) {

    Rect rect = getRectFromPoints(points, bitmap.getWidth(), bitmap.getHeight(), fixAspectRatio, aspectRatioX, aspectRatioY);

    Matrix matrix = new Matrix();
    matrix.setRotate(degreesRotated, bitmap.getWidth() / 2f, bitmap.getHeight() / 2f);
    matrix.postScale(flipHorizontally ? -scale : scale, flipVertically ? -scale : scale);

    Bitmap result = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height(), matrix, true);

    if (result == bitmap) {
      result = bitmap.copy(bitmap.getConfig() != null ? bitmap.getConfig() : Bitmap.Config.ARGB_8888, false);
    }

    if (degreesRotated % 90 != 0) {
      result = cropForRotatedImage(result, points, rect, degreesRotated, fixAspectRatio, aspectRatioX, aspectRatioY);
    }

    return result;
  }

  public static BitmapSampled cropBitmap(
          Context context,
          Uri loadedImageUri,
          float[] points,
          int degreesRotated,
          int orgWidth,
          int orgHeight,
          boolean fixAspectRatio,
          int aspectRatioX,
          int aspectRatioY,
          int reqWidth,
          int reqHeight,
          boolean flipHorizontally,
          boolean flipVertically) {
    int sampleMulti = 1;
    while (true) {
      try {
        return cropBitmap(
                context, loadedImageUri, points, degreesRotated, orgWidth, orgHeight,
                fixAspectRatio, aspectRatioX, aspectRatioY, reqWidth, reqHeight,
                flipHorizontally, flipVertically, sampleMulti);
      } catch (OutOfMemoryError e) {
        sampleMulti *= 2;
        if (sampleMulti > 16) {
          throw new RuntimeException("Failed to handle OOM by sampling (" + sampleMulti + "): " + loadedImageUri, e);
        }
      }
    }
  }

  public static float getRectLeft(float[] points) {
    return Math.min(Math.min(Math.min(points[0], points[2]), points[4]), points[6]);
  }

  public static float getRectTop(float[] points) {
    return Math.min(Math.min(Math.min(points[1], points[3]), points[5]), points[7]);
  }

  public static float getRectRight(float[] points) {
    return Math.max(Math.max(Math.max(points[0], points[2]), points[4]), points[6]);
  }

  public static float getRectBottom(float[] points) {
    return Math.max(Math.max(Math.max(points[1], points[3]), points[5]), points[7]);
  }

  public static float getRectWidth(float[] points) {
    return getRectRight(points) - getRectLeft(points);
  }

  public static float getRectHeight(float[] points) {
    return getRectBottom(points) - getRectTop(points);
  }

  public static float getRectCenterX(float[] points) {
    return (getRectRight(points) + getRectLeft(points)) / 2f;
  }

  public static float getRectCenterY(float[] points) {
    return (getRectBottom(points) + getRectTop(points)) / 2f;
  }

  public static Rect getRectFromPoints(
          float[] points,
          int imageWidth,
          int imageHeight,
          boolean fixAspectRatio,
          int aspectRatioX,
          int aspectRatioY) {
    int left = Math.round(Math.max(0, getRectLeft(points)));
    int top = Math.round(Math.max(0, getRectTop(points)));
    int right = Math.round(Math.min(imageWidth, getRectRight(points)));
    int bottom = Math.round(Math.min(imageHeight, getRectBottom(points)));

    Rect rect = new Rect(left, top, right, bottom);
    if (fixAspectRatio) {
      fixRectForAspectRatio(rect, aspectRatioX, aspectRatioY);
    }
    return rect;
  }

  private static void fixRectForAspectRatio(Rect rect, int aspectRatioX, int aspectRatioY) {
    if (aspectRatioX == aspectRatioY && rect.width() != rect.height()) {
      if (rect.height() > rect.width()) {
        rect.bottom -= rect.height() - rect.width();
      } else {
        rect.right -= rect.width() - rect.height();
      }
    }
  }

  public static Uri writeTempStateStoreBitmap(Context context, Bitmap bitmap, @Nullable Uri uri) {
    try {
      if (uri == null) {
        File tempFile = File.createTempFile("aic_state_store_temp", ".jpg", context.getCacheDir());
        uri = Uri.fromFile(tempFile);
      }
      writeBitmapToUri(context, bitmap, uri, Bitmap.CompressFormat.JPEG, 95);
      return uri;
    } catch (Exception e) {
      Log.w("AIC", "Failed to write bitmap to temp cache", e);
      return null;
    }
  }

  public static void writeBitmapToUri(
          Context context,
          Bitmap bitmap,
          Uri uri,
          Bitmap.CompressFormat compressFormat,
          int compressQuality)
          throws FileNotFoundException {
    try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
      if (outputStream != null) {
        bitmap.compress(compressFormat, compressQuality, outputStream);
      }
    } catch (IOException e) {
      throw new FileNotFoundException("Failed to write to URI: " + uri);
    }
  }

  public static Bitmap resizeBitmap(
          Bitmap bitmap, int reqWidth, int reqHeight, CropImageView.RequestSizeOptions options) {
    try {
      if (reqWidth > 0 && reqHeight > 0 &&
              (options == CropImageView.RequestSizeOptions.RESIZE_FIT
                      || options == CropImageView.RequestSizeOptions.RESIZE_INSIDE
                      || options == CropImageView.RequestSizeOptions.RESIZE_EXACT)) {

        Bitmap resized = null;
        if (options == CropImageView.RequestSizeOptions.RESIZE_EXACT) {
          resized = Bitmap.createScaledBitmap(bitmap, reqWidth, reqHeight, false);
        } else {
          int width = bitmap.getWidth();
          int height = bitmap.getHeight();
          float scale = Math.max(width / (float) reqWidth, height / (float) reqHeight);
          if (scale > 1 || options == CropImageView.RequestSizeOptions.RESIZE_FIT) {
            resized = Bitmap.createScaledBitmap(bitmap, (int) (width / scale), (int) (height / scale), false);
          }
        }
        if (resized != null) {
          if (resized != bitmap) {
            bitmap.recycle();
          }
          return resized;
        }
      }
    } catch (Exception e) {
      Log.w("AIC", "Failed to resize cropped image", e);
    }
    return bitmap;
  }

  private static BitmapSampled cropBitmap(
          Context context,
          Uri loadedImageUri,
          float[] points,
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
          int sampleMulti) {

    Rect rect = getRectFromPoints(points, orgWidth, orgHeight, fixAspectRatio, aspectRatioX, aspectRatioY);
    int width = reqWidth > 0 ? reqWidth : rect.width();
    int height = reqHeight > 0 ? reqHeight : rect.height();

    Bitmap result = null;
    int sampleSize = 1;
    try {
      BitmapSampled bitmapSampled = decodeSampledBitmapRegion(context, loadedImageUri, rect, width, height, sampleMulti);
      result = bitmapSampled.bitmap;
      sampleSize = bitmapSampled.sampleSize;
    } catch (Exception ignored) {
    }

    if (result != null) {
      try {
        result = rotateAndFlipBitmapInt(result, degreesRotated, flipHorizontally, flipVertically);
        if (degreesRotated % 90 != 0) {
          result = cropForRotatedImage(result, points, rect, degreesRotated, fixAspectRatio, aspectRatioX, aspectRatioY);
        }
      } catch (OutOfMemoryError e) {
        result.recycle();
        throw e;
      }
      return new BitmapSampled(result, sampleSize);
    } else {
      return cropBitmap(context, loadedImageUri, points, degreesRotated, fixAspectRatio, aspectRatioX,
              aspectRatioY, sampleMulti, rect, width, height, flipHorizontally, flipVertically);
    }
  }

  private static BitmapSampled cropBitmap(
          Context context,
          Uri loadedImageUri,
          float[] points,
          int degreesRotated,
          boolean fixAspectRatio,
          int aspectRatioX,
          int aspectRatioY,
          int sampleMulti,
          Rect rect,
          int width,
          int height,
          boolean flipHorizontally,
          boolean flipVertically) {
    Bitmap result = null;
    int sampleSize;
    try {
      BitmapFactory.Options options = new BitmapFactory.Options();
      sampleSize = sampleMulti * calculateInSampleSizeByReqestedSize(rect.width(), rect.height(), width, height);
      options.inSampleSize = sampleSize;

      Bitmap fullBitmap = decodeImage(context.getContentResolver(), loadedImageUri, options);
      if (fullBitmap != null) {
        try {
          float[] points2 = new float[points.length];
          System.arraycopy(points, 0, points2, 0, points.length);
          for (int i = 0; i < points2.length; i++) {
            points2[i] = points2[i] / options.inSampleSize;
          }

          result = cropBitmapObjectWithScale(
                  fullBitmap, points2, degreesRotated, fixAspectRatio, aspectRatioX, aspectRatioY,
                  1, flipHorizontally, flipVertically);
        } finally {
          if (result != fullBitmap) {
            fullBitmap.recycle();
          }
        }
      }
    } catch (OutOfMemoryError e) {
      if (result != null) {
        result.recycle();
      }
      throw e;
    } catch (Exception e) {
      throw new RuntimeException("Failed to load sampled bitmap: " + loadedImageUri, e);
    }
    return new BitmapSampled(result, sampleSize);
  }

  private static BitmapFactory.Options decodeImageForOption(ContentResolver resolver, Uri uri)
          throws FileNotFoundException {
    try (InputStream stream = resolver.openInputStream(uri)) {
      BitmapFactory.Options options = new BitmapFactory.Options();
      options.inJustDecodeBounds = true;
      BitmapFactory.decodeStream(stream, EMPTY_RECT, options);
      options.inJustDecodeBounds = false;
      return options;
    } catch (IOException e) {
      throw new FileNotFoundException("Unable to read options from URI: " + uri);
    }
  }

  private static Bitmap decodeImage(ContentResolver resolver, Uri uri, BitmapFactory.Options options)
          throws FileNotFoundException {
    do {
      try (InputStream stream = resolver.openInputStream(uri)) {
        return BitmapFactory.decodeStream(stream, EMPTY_RECT, options);
      } catch (OutOfMemoryError e) {
        options.inSampleSize *= 2;
      } catch (IOException e) {
        throw new FileNotFoundException("Unable to decode stream: " + uri);
      }
    } while (options.inSampleSize <= 512);
    throw new RuntimeException("Failed to decode image: " + uri);
  }

  private static BitmapSampled decodeSampledBitmapRegion(
          Context context, Uri uri, Rect rect, int reqWidth, int reqHeight, int sampleMulti) {
    int sampleSize = sampleMulti * calculateInSampleSizeByReqestedSize(rect.width(), rect.height(), reqWidth, reqHeight);
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inSampleSize = sampleSize;

    while (options.inSampleSize <= 512) {
      BitmapRegionDecoder decoder = null;
      try (InputStream stream = context.getContentResolver().openInputStream(uri)) {
        if (stream == null) return new BitmapSampled(null, 1);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
          decoder = BitmapRegionDecoder.newInstance(stream);
        } else {
          decoder = BitmapRegionDecoder.newInstance(stream, false);
        }

        Bitmap bitmap = decoder.decodeRegion(rect, options);
        return new BitmapSampled(bitmap, options.inSampleSize);
      } catch (OutOfMemoryError e) {
        options.inSampleSize *= 2;
      } catch (Exception e) {
        throw new RuntimeException("Failed to load sampled bitmap region: " + uri, e);
      } finally {
        if (decoder != null) {
          decoder.recycle();
        }
      }
    }
    return new BitmapSampled(null, 1);
  }

  private static Bitmap cropForRotatedImage(
          Bitmap bitmap,
          float[] points,
          Rect rect,
          int degreesRotated,
          boolean fixAspectRatio,
          int aspectRatioX,
          int aspectRatioY) {
    if (degreesRotated % 90 != 0) {
      int adjLeft = 0, adjTop = 0, width = 0, height = 0;
      double rads = Math.toRadians(degreesRotated);
      int compareTo = degreesRotated < 90 || (degreesRotated > 180 && degreesRotated < 270)
              ? rect.left
              : rect.right;
      for (int i = 0; i < points.length; i += 2) {
        if (points[i] >= compareTo - 1 && points[i] <= compareTo + 1) {
          adjLeft = (int) Math.abs(Math.sin(rads) * (rect.bottom - points[i + 1]));
          adjTop = (int) Math.abs(Math.cos(rads) * (points[i + 1] - rect.top));
          width = (int) Math.abs((points[i + 1] - rect.top) / Math.sin(rads));
          height = (int) Math.abs((rect.bottom - points[i + 1]) / Math.cos(rads));
          break;
        }
      }

      rect.set(adjLeft, adjTop, adjLeft + width, adjTop + height);
      if (fixAspectRatio) {
        fixRectForAspectRatio(rect, aspectRatioX, aspectRatioY);
      }

      Bitmap bitmapTmp = bitmap;
      bitmap = Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height());
      if (bitmapTmp != bitmap) {
        bitmapTmp.recycle();
      }
    }
    return bitmap;
  }

  private static int calculateInSampleSizeByReqestedSize(int width, int height, int reqWidth, int reqHeight) {
    int inSampleSize = 1;
    if (height > reqHeight || width > reqWidth) {
      while ((height / 2 / inSampleSize) > reqHeight && (width / 2 / inSampleSize) > reqWidth) {
        inSampleSize *= 2;
      }
    }
    return inSampleSize;
  }

  private static int calculateInSampleSizeByMaxTextureSize(int width, int height) {
    int inSampleSize = 1;
    if (mMaxTextureSize == 0) {
      mMaxTextureSize = getMaxTextureSize();
    }
    if (mMaxTextureSize > 0) {
      while ((height / inSampleSize) > mMaxTextureSize || (width / inSampleSize) > mMaxTextureSize) {
        inSampleSize *= 2;
      }
    }
    return inSampleSize;
  }

  private static Bitmap rotateAndFlipBitmapInt(
          Bitmap bitmap, int degrees, boolean flipHorizontally, boolean flipVertically) {
    if (degrees > 0 || flipHorizontally || flipVertically) {
      Matrix matrix = new Matrix();
      matrix.setRotate(degrees);
      matrix.postScale(flipHorizontally ? -1 : 1, flipVertically ? -1 : 1);
      Bitmap newBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
      if (newBitmap != bitmap) {
        bitmap.recycle();
      }
      return newBitmap;
    }
    return bitmap;
  }

  private static int getMaxTextureSize() {
    final int defaultDimension = 2048;
    try {
      EGL10 egl = (EGL10) EGLContext.getEGL();
      EGLDisplay display = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
      int[] version = new int[2];
      egl.eglInitialize(display, version);

      int[] totalConfigs = new int[1];
      egl.eglGetConfigs(display, null, 0, totalConfigs);

      EGLConfig[] configs = new EGLConfig[totalConfigs[0]];
      egl.eglGetConfigs(display, configs, totalConfigs[0], totalConfigs);

      int[] textureSize = new int[1];
      int maxTexture = 0;
      for (int i = 0; i < totalConfigs[0]; i++) {
        egl.eglGetConfigAttrib(display, configs[i], EGL10.EGL_MAX_PBUFFER_WIDTH, textureSize);
        if (maxTexture < textureSize[0]) {
          maxTexture = textureSize[0];
        }
      }
      egl.eglTerminate(display);
      return Math.max(maxTexture, defaultDimension);
    } catch (Exception e) {
      return defaultDimension;
    }
  }

  public static final class BitmapSampled {
    public final Bitmap bitmap;
    public final int sampleSize;

    public BitmapSampled(Bitmap bitmap, int sampleSize) {
      this.bitmap = bitmap;
      this.sampleSize = sampleSize;
    }
  }

  public static final class RotateBitmapResult {
    public final Bitmap bitmap;
    public final int degrees;

    public RotateBitmapResult(Bitmap bitmap, int degrees) {
      this.bitmap = bitmap;
      this.degrees = degrees;
    }
  }
}