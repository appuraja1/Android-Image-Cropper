package com.github.appuraja1.cropper;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Modern Helper class to simplify image picking, permissions, and cropping activity flows.
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public final class CropImage {

  public static final String CROP_IMAGE_EXTRA_SOURCE = "CROP_IMAGE_EXTRA_SOURCE";
  public static final String CROP_IMAGE_EXTRA_OPTIONS = "CROP_IMAGE_EXTRA_OPTIONS";
  public static final String CROP_IMAGE_EXTRA_BUNDLE = "CROP_IMAGE_EXTRA_BUNDLE";
  public static final String CROP_IMAGE_EXTRA_RESULT = "CROP_IMAGE_EXTRA_RESULT";

  public static final int PICK_IMAGE_CHOOSER_REQUEST_CODE = 200;
  public static final int PICK_IMAGE_PERMISSIONS_REQUEST_CODE = 201;
  public static final int CAMERA_CAPTURE_PERMISSIONS_REQUEST_CODE = 2011;
  public static final int CROP_IMAGE_ACTIVITY_REQUEST_CODE = 203;
  public static final int CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE = 204;

  private CropImage() {}

  /**
   * Create a new bitmap that has all pixels beyond the oval shape transparent.
   */
  public static Bitmap toOvalBitmap(@NonNull Bitmap bitmap) {
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

    Canvas canvas = new Canvas(output);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xff424242);

    canvas.drawARGB(0, 0, 0, 0);
    RectF rect = new RectF(0, 0, width, height);
    canvas.drawOval(rect, paint);
    paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
    canvas.drawBitmap(bitmap, 0, 0, paint);

    bitmap.recycle();
    return output;
  }

  public static void startPickImageActivity(@NonNull Activity activity) {
    activity.startActivityForResult(
            getPickImageChooserIntent(activity), PICK_IMAGE_CHOOSER_REQUEST_CODE);
  }

  public static void startPickImageActivity(@NonNull Context context, @NonNull Fragment fragment) {
    fragment.startActivityForResult(
            getPickImageChooserIntent(context), PICK_IMAGE_CHOOSER_REQUEST_CODE);
  }

  public static Intent getPickImageChooserIntent(@NonNull Context context) {
    return getPickImageChooserIntent(
            context, context.getString(R.string.pick_image_intent_chooser_title), false, true);
  }

  public static Intent getPickImageChooserIntent(
          @NonNull Context context,
          CharSequence title,
          boolean includeDocuments,
          boolean includeCamera) {

    List<Intent> allIntents = new ArrayList<>();
    PackageManager packageManager = context.getPackageManager();

    if (!isExplicitCameraPermissionRequired(context) && includeCamera) {
      allIntents.addAll(getCameraIntents(context, packageManager));
    }

    List<Intent> galleryIntents =
            getGalleryIntents(packageManager, Intent.ACTION_GET_CONTENT, includeDocuments);
    if (galleryIntents.isEmpty()) {
      galleryIntents = getGalleryIntents(packageManager, Intent.ACTION_PICK, includeDocuments);
    }
    allIntents.addAll(galleryIntents);

    Intent target;
    if (allIntents.isEmpty()) {
      target = new Intent();
    } else {
      target = allIntents.get(allIntents.size() - 1);
      allIntents.remove(allIntents.size() - 1);
    }

    Intent chooserIntent = Intent.createChooser(target, title);
    chooserIntent.putExtra(
            Intent.EXTRA_INITIAL_INTENTS, allIntents.toArray(new Parcelable[0]));

    return chooserIntent;
  }

  public static Intent getCameraIntent(@NonNull Context context, @Nullable Uri outputFileUri) {
    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    if (outputFileUri == null) {
      outputFileUri = getCaptureImageOutputUri(context);
    }
    intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
    return intent;
  }

  public static List<Intent> getCameraIntents(
          @NonNull Context context, @NonNull PackageManager packageManager) {

    List<Intent> allIntents = new ArrayList<>();
    Uri outputFileUri = getCaptureImageOutputUri(context);

    Intent captureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
    List<ResolveInfo> listCam = packageManager.queryIntentActivities(captureIntent, 0);
    for (ResolveInfo res : listCam) {
      Intent intent = new Intent(captureIntent);
      intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
      intent.setPackage(res.activityInfo.packageName);
      if (outputFileUri != null) {
        intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);
      }
      intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
      allIntents.add(intent);
    }

    return allIntents;
  }

  public static List<Intent> getGalleryIntents(
          @NonNull PackageManager packageManager, String action, boolean includeDocuments) {
    List<Intent> intents = new ArrayList<>();
    Intent galleryIntent =
            action.equals(Intent.ACTION_GET_CONTENT)
                    ? new Intent(action)
                    : new Intent(action, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
    galleryIntent.setType("image/*");
    List<ResolveInfo> listGallery = packageManager.queryIntentActivities(galleryIntent, 0);
    for (ResolveInfo res : listGallery) {
      Intent intent = new Intent(galleryIntent);
      intent.setComponent(new ComponentName(res.activityInfo.packageName, res.activityInfo.name));
      intent.setPackage(res.activityInfo.packageName);
      intents.add(intent);
    }

    if (!includeDocuments) {
      for (Intent intent : intents) {
        if (intent.getComponent() != null
                && intent.getComponent().getClassName().equals("com.android.documentsui.DocumentsActivity")) {
          intents.remove(intent);
          break;
        }
      }
    }
    return intents;
  }

  public static boolean isExplicitCameraPermissionRequired(@NonNull Context context) {
    return hasPermissionInManifest(context, Manifest.permission.CAMERA)
            && context.checkSelfPermission(Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED;
  }

  public static boolean hasPermissionInManifest(
          @NonNull Context context, @NonNull String permissionName) {
    String packageName = context.getPackageName();
    try {
      PackageInfo packageInfo =
              context.getPackageManager().getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
      final String[] declaredPermissions = packageInfo.requestedPermissions;
      if (declaredPermissions != null && declaredPermissions.length > 0) {
        for (String p : declaredPermissions) {
          if (p.equalsIgnoreCase(permissionName)) {
            return true;
          }
        }
      }
    } catch (PackageManager.NameNotFoundException ignored) {
    }
    return false;
  }

  /**
   * Get fallback URI to image received from capture by camera if not supplied by host app.
   */
  public static Uri getCaptureImageOutputUri(@NonNull Context context) {
    File getImage = context.getExternalCacheDir();
    if (getImage == null) {
      getImage = context.getCacheDir();
    }
    File file = new File(getImage, "pickImageResult.jpeg");
    return Uri.fromFile(file);
  }

  public static Uri getPickImageResultUri(@NonNull Context context, @Nullable Intent data) {
    boolean isCamera = true;
    if (data != null && data.getData() != null) {
      String action = data.getAction();
      isCamera = action != null && action.equals(MediaStore.ACTION_IMAGE_CAPTURE);
    }
    return isCamera || data.getData() == null ? getCaptureImageOutputUri(context) : data.getData();
  }

  public static boolean isReadExternalStoragePermissionsRequired(
          @NonNull Context context, @NonNull Uri uri) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
      return context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
              != PackageManager.PERMISSION_GRANTED
              && isUriRequiresPermissions(context, uri);
    } else {
      return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
              != PackageManager.PERMISSION_GRANTED
              && isUriRequiresPermissions(context, uri);
    }
  }

  public static boolean isUriRequiresPermissions(@NonNull Context context, @NonNull Uri uri) {
    try {
      ContentResolver resolver = context.getContentResolver();
      try (InputStream stream = resolver.openInputStream(uri)) {
        return stream == null;
      }
    } catch (Exception e) {
      return true;
    }
  }

  public static ActivityBuilder activity() {
    return new ActivityBuilder(null);
  }

  public static ActivityBuilder activity(@Nullable Uri uri) {
    return new ActivityBuilder(uri);
  }

  public static ActivityResult getActivityResult(@Nullable Intent data) {
    if (data == null) return null;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      return data.getParcelableExtra(CROP_IMAGE_EXTRA_RESULT, ActivityResult.class);
    } else {
      return data.getParcelableExtra(CROP_IMAGE_EXTRA_RESULT);
    }
  }

  // region: Inner class: ActivityBuilder

  public static final class ActivityBuilder {

    @Nullable private final Uri mSource;
    private final CropImageOptions mOptions;

    private ActivityBuilder(@Nullable Uri source) {
      this.mSource = source;
      this.mOptions = new CropImageOptions();
    }

    public Intent getIntent(@NonNull Context context) {
      return getIntent(context, CropImageActivity.class);
    }

    public Intent getIntent(@NonNull Context context, @Nullable Class<?> cls) {
      mOptions.validate();

      Intent intent = new Intent();
      intent.setClass(context, cls != null ? cls : CropImageActivity.class);
      Bundle bundle = new Bundle();
      bundle.putParcelable(CROP_IMAGE_EXTRA_SOURCE, mSource);
      bundle.putParcelable(CROP_IMAGE_EXTRA_OPTIONS, mOptions);
      intent.putExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE, bundle);
      return intent;
    }

    public void start(@NonNull Activity activity) {
      mOptions.validate();
      activity.startActivityForResult(getIntent(activity), CROP_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    public void start(@NonNull Activity activity, @Nullable Class<?> cls) {
      mOptions.validate();
      activity.startActivityForResult(getIntent(activity, cls), CROP_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    public void start(@NonNull Context context, @NonNull Fragment fragment) {
      fragment.startActivityForResult(getIntent(context), CROP_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    public void start(
            @NonNull Context context, @NonNull Fragment fragment, @Nullable Class<?> cls) {
      fragment.startActivityForResult(getIntent(context, cls), CROP_IMAGE_ACTIVITY_REQUEST_CODE);
    }

    public ActivityBuilder setCropShape(@NonNull CropImageView.CropShape cropShape) {
      mOptions.cropShape = cropShape;
      return this;
    }

    public ActivityBuilder setSnapRadius(float snapRadius) {
      mOptions.snapRadius = snapRadius;
      return this;
    }

    public ActivityBuilder setTouchRadius(float touchRadius) {
      mOptions.touchRadius = touchRadius;
      return this;
    }

    public ActivityBuilder setGuidelines(@NonNull CropImageView.Guidelines guidelines) {
      mOptions.guidelines = guidelines;
      return this;
    }

    public ActivityBuilder setScaleType(@NonNull CropImageView.ScaleType scaleType) {
      mOptions.scaleType = scaleType;
      return this;
    }

    public ActivityBuilder setShowCropOverlay(boolean showCropOverlay) {
      mOptions.showCropOverlay = showCropOverlay;
      return this;
    }

    public ActivityBuilder setAutoZoomEnabled(boolean autoZoomEnabled) {
      mOptions.autoZoomEnabled = autoZoomEnabled;
      return this;
    }

    public ActivityBuilder setMultiTouchEnabled(boolean multiTouchEnabled) {
      mOptions.multiTouchEnabled = multiTouchEnabled;
      return this;
    }

    public ActivityBuilder setMaxZoom(int maxZoom) {
      mOptions.maxZoom = maxZoom;
      return this;
    }

    public ActivityBuilder setInitialCropWindowPaddingRatio(float initialCropWindowPaddingRatio) {
      mOptions.initialCropWindowPaddingRatio = initialCropWindowPaddingRatio;
      return this;
    }

    public ActivityBuilder setFixAspectRatio(boolean fixAspectRatio) {
      mOptions.fixAspectRatio = fixAspectRatio;
      return this;
    }

    public ActivityBuilder setAspectRatio(int aspectRatioX, int aspectRatioY) {
      mOptions.aspectRatioX = aspectRatioX;
      mOptions.aspectRatioY = aspectRatioY;
      mOptions.fixAspectRatio = true;
      return this;
    }

    public ActivityBuilder setBorderLineThickness(float borderLineThickness) {
      mOptions.borderLineThickness = borderLineThickness;
      return this;
    }

    public ActivityBuilder setBorderLineColor(int borderLineColor) {
      mOptions.borderLineColor = borderLineColor;
      return this;
    }

    public ActivityBuilder setBorderCornerThickness(float borderCornerThickness) {
      mOptions.borderCornerThickness = borderCornerThickness;
      return this;
    }

    public ActivityBuilder setBorderCornerOffset(float borderCornerOffset) {
      mOptions.borderCornerOffset = borderCornerOffset;
      return this;
    }

    public ActivityBuilder setBorderCornerLength(float borderCornerLength) {
      mOptions.borderCornerLength = borderCornerLength;
      return this;
    }

    public ActivityBuilder setBorderCornerColor(int borderCornerColor) {
      mOptions.borderCornerColor = borderCornerColor;
      return this;
    }

    public ActivityBuilder setGuidelinesThickness(float guidelinesThickness) {
      mOptions.guidelinesThickness = guidelinesThickness;
      return this;
    }

    public ActivityBuilder setGuidelinesColor(int guidelinesColor) {
      mOptions.guidelinesColor = guidelinesColor;
      return this;
    }

    public ActivityBuilder setBackgroundColor(int backgroundColor) {
      mOptions.backgroundColor = backgroundColor;
      return this;
    }

    public ActivityBuilder setMinCropWindowSize(int minCropWindowWidth, int minCropWindowHeight) {
      mOptions.minCropWindowWidth = minCropWindowWidth;
      mOptions.minCropWindowHeight = minCropWindowHeight;
      return this;
    }

    public ActivityBuilder setMinCropResultSize(int minCropResultWidth, int minCropResultHeight) {
      mOptions.minCropResultWidth = minCropResultWidth;
      mOptions.minCropResultHeight = minCropResultHeight;
      return this;
    }

    public ActivityBuilder setMaxCropResultSize(int maxCropResultWidth, int maxCropResultHeight) {
      mOptions.maxCropResultWidth = maxCropResultWidth;
      mOptions.maxCropResultHeight = maxCropResultHeight;
      return this;
    }

    public ActivityBuilder setActivityTitle(CharSequence activityTitle) {
      mOptions.activityTitle = activityTitle;
      return this;
    }

    public ActivityBuilder setActivityMenuIconColor(int activityMenuIconColor) {
      mOptions.activityMenuIconColor = activityMenuIconColor;
      return this;
    }

    public ActivityBuilder setOutputUri(Uri outputUri) {
      mOptions.outputUri = outputUri;
      return this;
    }

    public ActivityBuilder setOutputCompressFormat(Bitmap.CompressFormat outputCompressFormat) {
      mOptions.outputCompressFormat = outputCompressFormat;
      return this;
    }

    public ActivityBuilder setOutputCompressQuality(int outputCompressQuality) {
      mOptions.outputCompressQuality = outputCompressQuality;
      return this;
    }

    public ActivityBuilder setRequestedSize(int reqWidth, int reqHeight) {
      return setRequestedSize(reqWidth, reqHeight, CropImageView.RequestSizeOptions.RESIZE_INSIDE);
    }

    public ActivityBuilder setRequestedSize(
            int reqWidth, int reqHeight, CropImageView.RequestSizeOptions options) {
      mOptions.outputRequestWidth = reqWidth;
      mOptions.outputRequestHeight = reqHeight;
      mOptions.outputRequestSizeOptions = options;
      return this;
    }

    public ActivityBuilder setNoOutputImage(boolean noOutputImage) {
      mOptions.noOutputImage = noOutputImage;
      return this;
    }

    public ActivityBuilder setInitialCropWindowRectangle(Rect initialCropWindowRectangle) {
      mOptions.initialCropWindowRectangle = initialCropWindowRectangle;
      return this;
    }

    public ActivityBuilder setInitialRotation(int initialRotation) {
      mOptions.initialRotation = (initialRotation + 360) % 360;
      return this;
    }

    public ActivityBuilder setAllowRotation(boolean allowRotation) {
      mOptions.allowRotation = allowRotation;
      return this;
    }

    public ActivityBuilder setAllowFlipping(boolean allowFlipping) {
      mOptions.allowFlipping = allowFlipping;
      return this;
    }

    public ActivityBuilder setAllowCounterRotation(boolean allowCounterRotation) {
      mOptions.allowCounterRotation = allowCounterRotation;
      return this;
    }

    public ActivityBuilder setRotationDegrees(int rotationDegrees) {
      mOptions.rotationDegrees = (rotationDegrees + 360) % 360;
      return this;
    }

    public ActivityBuilder setFlipHorizontally(boolean flipHorizontally) {
      mOptions.flipHorizontally = flipHorizontally;
      return this;
    }

    public ActivityBuilder setFlipVertically(boolean flipVertically) {
      mOptions.flipVertically = flipVertically;
      return this;
    }

    public ActivityBuilder setCropMenuCropButtonTitle(CharSequence title) {
      mOptions.cropMenuCropButtonTitle = title;
      return this;
    }

    public ActivityBuilder setCropMenuCropButtonIcon(@DrawableRes int drawableResource) {
      mOptions.cropMenuCropButtonIcon = drawableResource;
      return this;
    }
  }
  // endregion

  // region: Inner class: ActivityResult

  public static final class ActivityResult extends CropImageView.CropResult implements Parcelable {

    public static final Creator<ActivityResult> CREATOR =
            new Creator<>() {
              @Override
              public ActivityResult createFromParcel(Parcel in) {
                return new ActivityResult(in);
              }

              @Override
              public ActivityResult[] newArray(int size) {
                return new ActivityResult[size];
              }
            };

    public ActivityResult(
            Uri originalUri,
            Uri uri,
            Exception error,
            float[] cropPoints,
            Rect cropRect,
            int rotation,
            Rect wholeImageRect,
            int sampleSize) {
      super(
              null,
              originalUri,
              null,
              uri,
              error,
              cropPoints,
              cropRect,
              wholeImageRect,
              rotation,
              sampleSize);
    }

    protected ActivityResult(Parcel in) {
      super(
              null,
              in.readParcelable(Uri.class.getClassLoader()),
              null,
              in.readParcelable(Uri.class.getClassLoader()),
              (Exception) in.readSerializable(),
              in.createFloatArray(),
              in.readParcelable(Rect.class.getClassLoader()),
              in.readParcelable(Rect.class.getClassLoader()),
              in.readInt(),
              in.readInt());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
      dest.writeParcelable(getOriginalUri(), flags);
      dest.writeParcelable(getUri(), flags);
      dest.writeSerializable(getError());
      dest.writeFloatArray(getCropPoints());
      dest.writeParcelable(getCropRect(), flags);
      dest.writeParcelable(getWholeImageRect(), flags);
      dest.writeInt(getRotation());
      dest.writeInt(getSampleSize());
    }

    @Override
    public int describeContents() {
      return 0;
    }
  }
  // endregion
}