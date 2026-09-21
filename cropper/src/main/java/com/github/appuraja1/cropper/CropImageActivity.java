package com.github.appuraja1.cropper;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.io.IOException;

/**
 * Lightweight, crash-proof Crop Activity with 100% programmatic Pure Black theme.
 * Requires zero extra theme/style declarations in the consuming app.
 */
public class CropImageActivity extends AppCompatActivity
        implements CropImageView.OnSetImageUriCompleteListener,
        CropImageView.OnCropImageCompleteListener {

  private CropImageView mCropImageView;
  private Uri mCropImageUri;
  private CropImageOptions mOptions;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    // 1. Programmatic pure black system bars
    Window window = getWindow();
    window.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
    window.setStatusBarColor(Color.BLACK);
    window.setNavigationBarColor(Color.BLACK);

    WindowInsetsControllerCompat controller =
            WindowCompat.getInsetsController(window, window.getDecorView());
    controller.setAppearanceLightStatusBars(false);
    controller.setAppearanceLightNavigationBars(false);

    setContentView(R.layout.crop_image_activity);
    mCropImageView = findViewById(R.id.cropImageView);

    Bundle bundle = getIntent().getBundleExtra(CropImage.CROP_IMAGE_EXTRA_BUNDLE);
    if (bundle != null) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        mCropImageUri = bundle.getParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE, Uri.class);
        mOptions = bundle.getParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS, CropImageOptions.class);
      } else {
        mCropImageUri = bundle.getParcelable(CropImage.CROP_IMAGE_EXTRA_SOURCE);
        mOptions = bundle.getParcelable(CropImage.CROP_IMAGE_EXTRA_OPTIONS);
      }
    }

    if (mOptions == null) {
      mOptions = new CropImageOptions();
    }

    if (savedInstanceState == null) {
      if (mCropImageUri == null || mCropImageUri.equals(Uri.EMPTY)) {
        if (CropImage.isExplicitCameraPermissionRequired(this)) {
          requestPermissions(
                  new String[] {Manifest.permission.CAMERA},
                  CropImage.CAMERA_CAPTURE_PERMISSIONS_REQUEST_CODE);
        } else {
          CropImage.startPickImageActivity(this);
        }
      } else if (CropImage.isReadExternalStoragePermissionsRequired(this, mCropImageUri)) {
        String permission =
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? Manifest.permission.READ_MEDIA_IMAGES
                        : Manifest.permission.READ_EXTERNAL_STORAGE;
        requestPermissions(
                new String[] {permission},
                CropImage.PICK_IMAGE_PERMISSIONS_REQUEST_CODE);
      } else {
        mCropImageView.setImageUriAsync(mCropImageUri);
      }
    }

    // 2. Programmatic pure black action bar
    ActionBar actionBar = getSupportActionBar();
    if (actionBar != null) {
      actionBar.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
      CharSequence titleText =
              mOptions.activityTitle != null && mOptions.activityTitle.length() > 0
                      ? mOptions.activityTitle
                      : getString(R.string.crop_image_activity_title);

      // Force title text color to white
      SpannableString spanTitle = new SpannableString(titleText);
      spanTitle.setSpan(new ForegroundColorSpan(Color.WHITE), 0, spanTitle.length(), 0);
      actionBar.setTitle(spanTitle);
      actionBar.setDisplayHomeAsUpEnabled(true);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    mCropImageView.setOnSetImageUriCompleteListener(this);
    mCropImageView.setOnCropImageCompleteListener(this);
  }

  @Override
  protected void onStop() {
    super.onStop();
    mCropImageView.setOnSetImageUriCompleteListener(null);
    mCropImageView.setOnCropImageCompleteListener(null);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.crop_image_menu, menu);

    if (!mOptions.allowRotation) {
      menu.removeItem(R.id.crop_image_menu_rotate_left);
      menu.removeItem(R.id.crop_image_menu_rotate_right);
    } else if (mOptions.allowCounterRotation) {
      MenuItem rotateLeft = menu.findItem(R.id.crop_image_menu_rotate_left);
      if (rotateLeft != null) {
        rotateLeft.setVisible(true);
      }
    }

    if (!mOptions.allowFlipping) {
      menu.removeItem(R.id.crop_image_menu_flip);
    }

    // Right-most Crop button: Build programmatically to guarantee safe edge padding
    MenuItem cropItem = menu.findItem(R.id.crop_image_menu_crop);
    if (cropItem != null) {
      CharSequence title =
              mOptions.cropMenuCropButtonTitle != null
                      ? mOptions.cropMenuCropButtonTitle
                      : getString(R.string.crop_image_menu_crop);

      TextView cropBtn = new TextView(this);
      cropBtn.setText(title);
      cropBtn.setTextColor(Color.WHITE);
      cropBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
      cropBtn.setGravity(Gravity.CENTER);
      cropBtn.setTypeface(null, android.graphics.Typeface.BOLD);

      // 16dp right padding so text never sticks to the edge
      int padH = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
      cropBtn.setPadding(padH / 2, 0, padH, 0);

      TypedValue outValue = new TypedValue();
      if (getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)) {
        cropBtn.setBackgroundResource(outValue.resourceId);
      }

      cropBtn.setOnClickListener(v -> onOptionsItemSelected(cropItem));
      cropItem.setActionView(cropBtn);
    }

    // Ensure menu icons remain white / match accent
    int iconColor = mOptions.activityMenuIconColor != 0 ? mOptions.activityMenuIconColor : Color.WHITE;
    updateMenuItemIconColor(menu, R.id.crop_image_menu_rotate_left, iconColor);
    updateMenuItemIconColor(menu, R.id.crop_image_menu_rotate_right, iconColor);
    updateMenuItemIconColor(menu, R.id.crop_image_menu_flip, iconColor);

    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    int id = item.getItemId();
    if (id == R.id.crop_image_menu_crop) {
      cropImage();
      return true;
    }
    if (id == R.id.crop_image_menu_rotate_left) {
      rotateImage(-mOptions.rotationDegrees);
      return true;
    }
    if (id == R.id.crop_image_menu_rotate_right) {
      rotateImage(mOptions.rotationDegrees);
      return true;
    }
    if (id == R.id.crop_image_menu_flip_horizontally) {
      mCropImageView.flipImageHorizontally();
      return true;
    }
    if (id == R.id.crop_image_menu_flip_vertically) {
      mCropImageView.flipImageVertically();
      return true;
    }
    if (id == android.R.id.home) {
      setResultCancel();
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  @Override
  public void onBackPressed() {
    super.onBackPressed();
    setResultCancel();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == CropImage.PICK_IMAGE_CHOOSER_REQUEST_CODE) {
      if (resultCode == Activity.RESULT_CANCELED) {
        setResultCancel();
        return;
      }

      if (resultCode == Activity.RESULT_OK) {
        mCropImageUri = CropImage.getPickImageResultUri(this, data);

        if (CropImage.isReadExternalStoragePermissionsRequired(this, mCropImageUri)) {
          String permission =
                  Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                          ? Manifest.permission.READ_MEDIA_IMAGES
                          : Manifest.permission.READ_EXTERNAL_STORAGE;
          requestPermissions(new String[] {permission}, CropImage.PICK_IMAGE_PERMISSIONS_REQUEST_CODE);
        } else {
          mCropImageView.setImageUriAsync(mCropImageUri);
        }
      }
    }
  }

  @Override
  public void onRequestPermissionsResult(
          int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == CropImage.PICK_IMAGE_PERMISSIONS_REQUEST_CODE) {
      if (mCropImageUri != null
              && grantResults.length > 0
              && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        mCropImageView.setImageUriAsync(mCropImageUri);
      } else {
        Toast.makeText(this, R.string.crop_image_activity_no_permissions, Toast.LENGTH_LONG).show();
        setResultCancel();
      }
    }

    if (requestCode == CropImage.CAMERA_CAPTURE_PERMISSIONS_REQUEST_CODE) {
      CropImage.startPickImageActivity(this);
    }
  }

  @Override
  public void onSetImageUriComplete(CropImageView view, Uri uri, Exception error) {
    if (error == null) {
      if (mOptions.initialCropWindowRectangle != null) {
        mCropImageView.setCropRect(mOptions.initialCropWindowRectangle);
      }
      if (mOptions.initialRotation > -1) {
        mCropImageView.setRotatedDegrees(mOptions.initialRotation);
      }
    } else {
      setResult(null, error, 1);
    }
  }

  @Override
  public void onCropImageComplete(CropImageView view, CropImageView.CropResult result) {
    setResult(result.getUri(), result.getError(), result.getSampleSize());
  }

  protected void cropImage() {
    if (mOptions.noOutputImage) {
      setResult(null, null, 1);
    } else {
      Uri outputUri = getOutputUri();
      mCropImageView.saveCroppedImageAsync(
              outputUri,
              mOptions.outputCompressFormat,
              mOptions.outputCompressQuality,
              mOptions.outputRequestWidth,
              mOptions.outputRequestHeight,
              mOptions.outputRequestSizeOptions);
    }
  }

  protected void rotateImage(int degrees) {
    mCropImageView.rotateImage(degrees);
  }

  protected Uri getOutputUri() {
    Uri outputUri = mOptions.outputUri;
    if (outputUri == null || outputUri.equals(Uri.EMPTY)) {
      try {
        String ext =
                mOptions.outputCompressFormat == Bitmap.CompressFormat.PNG
                        ? ".png"
                        : mOptions.outputCompressFormat == Bitmap.CompressFormat.WEBP
                          ? ".webp"
                          : ".jpg";
        File tempFile = File.createTempFile("cropped", ext, getCacheDir());
        outputUri = FileProvider.getUriForFile(
                this, getPackageName() + ".cropper.fileprovider", tempFile);
      } catch (Exception e) {
        try {
          outputUri = Uri.fromFile(File.createTempFile("cropped", ".jpg", getCacheDir()));
        } catch (IOException ioException) {
          throw new RuntimeException("Failed to create temporary output crop file", ioException);
        }
      }
    }
    return outputUri;
  }

  protected void setResult(Uri uri, Exception error, int sampleSize) {
    int resultCode = error == null ? RESULT_OK : CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE;
    setResult(resultCode, getResultIntent(uri, error, sampleSize));
    finish();
  }

  protected void setResultCancel() {
    setResult(RESULT_CANCELED);
    finish();
  }

  protected Intent getResultIntent(Uri uri, Exception error, int sampleSize) {
    CropImage.ActivityResult result =
            new CropImage.ActivityResult(
                    mCropImageView.getImageUri(),
                    uri,
                    error,
                    mCropImageView.getCropPoints(),
                    mCropImageView.getCropRect(),
                    mCropImageView.getRotatedDegrees(),
                    mCropImageView.getWholeImageRect(),
                    sampleSize);
    Intent intent = new Intent();
    intent.putExtras(getIntent());
    intent.putExtra(CropImage.CROP_IMAGE_EXTRA_RESULT, result);
    return intent;
  }

  private void updateMenuItemIconColor(Menu menu, int itemId, int color) {
    MenuItem menuItem = menu.findItem(itemId);
    if (menuItem != null) {
      Drawable menuItemIcon = menuItem.getIcon();
      if (menuItemIcon != null) {
        try {
          menuItemIcon.mutate();
          menuItemIcon.setColorFilter(color, PorterDuff.Mode.SRC_ATOP);
          menuItem.setIcon(menuItemIcon);
        } catch (Exception e) {
          Log.w("AIC", "Failed to update menu item color", e);
        }
      }
    }
  }
}