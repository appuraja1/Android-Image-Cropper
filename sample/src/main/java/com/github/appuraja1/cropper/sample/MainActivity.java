package com.github.appuraja1.cropper.sample;

import android.Manifest;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Pair;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;

import com.github.appuraja1.cropper.CropImage;
import com.github.appuraja1.cropper.CropImageView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

  private DrawerLayout mDrawerLayout;
  private ActionBarDrawerToggle mDrawerToggle;
  private MainFragment mCurrentFragment;
  private Uri mCropImageUri;
  private CropImageViewOptions mCropImageViewOptions = new CropImageViewOptions();

  // Modern Activity Result Launchers
  private ActivityResultLauncher<Intent> mPickImageLauncher;
  private ActivityResultLauncher<String[]> mStoragePermissionLauncher;
  private ActivityResultLauncher<String> mCameraPermissionLauncher;

  public void setCurrentFragment(MainFragment fragment) {
    mCurrentFragment = fragment;
  }

  public void setCurrentOptions(CropImageViewOptions options) {
    mCropImageViewOptions = options;
    updateDrawerTogglesByOptions(options);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    initActivityLaunchers();

    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setHomeButtonEnabled(true);
    }

    mDrawerLayout = findViewById(R.id.drawer_layout);
    mDrawerToggle = new ActionBarDrawerToggle(
            this, mDrawerLayout, R.string.main_drawer_open, R.string.main_drawer_close);
    mDrawerToggle.setDrawerIndicatorEnabled(true);
    mDrawerLayout.addDrawerListener(mDrawerToggle);

    if (savedInstanceState == null) {
      setMainFragmentByPreset(CropDemoPreset.RECT);
    }
  }

  private void initActivityLaunchers() {
    // Image Picker Launcher
    mPickImageLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
              if (result.getResultCode() == RESULT_OK) {
                Uri imageUri = CropImage.getPickImageResultUri(this, result.getData());
                if (CropImage.isReadExternalStoragePermissionsRequired(this, imageUri)) {
                  mCropImageUri = imageUri;
                  requestProperStoragePermission();
                } else if (mCurrentFragment != null) {
                  mCurrentFragment.setImageUri(imageUri);
                }
              }
            });

    // Storage Permission Launcher (Targeting Android 13/14+)
    mStoragePermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
              boolean granted = result.values().stream().anyMatch(Boolean::booleanValue);
              if (granted) {
                if (mCropImageUri != null && mCurrentFragment != null) {
                  mCurrentFragment.setImageUri(mCropImageUri);
                }
              } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
              }
            });

    // Camera Permission Launcher
    mCameraPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
              if (isGranted) {
                launchImagePicker();
              } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
              }
            });
  }

  private void requestProperStoragePermission() {
    List<String> permissions = new ArrayList<>();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // Android 14+
      permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
      permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED);
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13
      permissions.add(Manifest.permission.READ_MEDIA_IMAGES);
    } else {
      permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
    }
    mStoragePermissionLauncher.launch(permissions.toArray(new String[0]));
  }

  private void launchImagePicker() {
    Intent intent = CropImage.getPickImageChooserIntent(this);
    mPickImageLauncher.launch(intent);
  }

  @Override
  protected void onPostCreate(Bundle savedInstanceState) {
    super.onPostCreate(savedInstanceState);
    mDrawerToggle.syncState();
    if (mCurrentFragment != null) {
      mCurrentFragment.updateCurrentCropViewOptions();
    }
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(@NonNull MenuItem item) {
    if (mDrawerToggle.onOptionsItemSelected(item)) {
      return true;
    }
    if (mCurrentFragment != null && mCurrentFragment.onOptionsItemSelected(item)) {
      return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void onDrawerOptionClicked(View view) {
    int id = view.getId();
    if (id == R.id.drawer_option_load) {
      if (CropImage.isExplicitCameraPermissionRequired(this)) {
        mCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
      } else {
        launchImagePicker();
      }
      mDrawerLayout.closeDrawers();
    } else if (id == R.id.drawer_option_oval) {
      setMainFragmentByPreset(CropDemoPreset.CIRCULAR);
      mDrawerLayout.closeDrawers();
    } else if (id == R.id.drawer_option_rect) {
      setMainFragmentByPreset(CropDemoPreset.RECT);
      mDrawerLayout.closeDrawers();
    } else if (id == R.id.drawer_option_customized_overlay) {
      setMainFragmentByPreset(CropDemoPreset.CUSTOMIZED_OVERLAY);
      mDrawerLayout.closeDrawers();
    } else if (id == R.id.drawer_option_min_max_override) {
      setMainFragmentByPreset(CropDemoPreset.MIN_MAX_OVERRIDE);
      mDrawerLayout.closeDrawers();
    } else if (id == R.id.drawer_option_scale_center) {
      setMainFragmentByPreset(CropDemoPreset.SCALE_CENTER_INSIDE);
      mDrawerLayout.closeDrawers();
    } else if (id == R.id.drawer_option_toggle_scale) {
      mCropImageViewOptions.scaleType =
              mCropImageViewOptions.scaleType == CropImageView.ScaleType.FIT_CENTER
                      ? CropImageView.ScaleType.CENTER_INSIDE
                      : mCropImageViewOptions.scaleType == CropImageView.ScaleType.CENTER_INSIDE
                        ? CropImageView.ScaleType.CENTER
                        : mCropImageViewOptions.scaleType == CropImageView.ScaleType.CENTER
                          ? CropImageView.ScaleType.CENTER_CROP
                          : CropImageView.ScaleType.FIT_CENTER;
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else if (id == R.id.drawer_option_toggle_shape) {
      mCropImageViewOptions.cropShape =
              mCropImageViewOptions.cropShape == CropImageView.CropShape.RECTANGLE
                      ? CropImageView.CropShape.OVAL
                      : CropImageView.CropShape.RECTANGLE;
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else if (id == R.id.drawer_option_toggle_guidelines) {
      mCropImageViewOptions.guidelines =
              mCropImageViewOptions.guidelines == CropImageView.Guidelines.OFF
                      ? CropImageView.Guidelines.ON
                      : mCropImageViewOptions.guidelines == CropImageView.Guidelines.ON
                        ? CropImageView.Guidelines.ON_TOUCH
                        : CropImageView.Guidelines.OFF;
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else if (id == R.id.drawer_option_toggle_aspect_ratio) {
      if (!mCropImageViewOptions.fixAspectRatio) {
        mCropImageViewOptions.fixAspectRatio = true;
        mCropImageViewOptions.aspectRatio = new Pair<>(1, 1);
      } else {
        if (mCropImageViewOptions.aspectRatio.first == 1 && mCropImageViewOptions.aspectRatio.second == 1) {
          mCropImageViewOptions.aspectRatio = new Pair<>(4, 3);
        } else if (mCropImageViewOptions.aspectRatio.first == 4 && mCropImageViewOptions.aspectRatio.second == 3) {
          mCropImageViewOptions.aspectRatio = new Pair<>(16, 9);
        } else if (mCropImageViewOptions.aspectRatio.first == 16 && mCropImageViewOptions.aspectRatio.second == 9) {
          mCropImageViewOptions.aspectRatio = new Pair<>(9, 16);
        } else {
          mCropImageViewOptions.fixAspectRatio = false;
        }
      }
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else if (id == R.id.drawer_option_toggle_auto_zoom) {
      mCropImageViewOptions.autoZoomEnabled = !mCropImageViewOptions.autoZoomEnabled;
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else if (id == R.id.drawer_option_toggle_max_zoom) {
      mCropImageViewOptions.maxZoomLevel =
              mCropImageViewOptions.maxZoomLevel == 4
                      ? 8
                      : mCropImageViewOptions.maxZoomLevel == 8 ? 2 : 4;
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else if (id == R.id.drawer_option_set_initial_crop_rect) {
      mCurrentFragment.setInitialCropRect();
      mDrawerLayout.closeDrawers();
    } else if (id == R.id.drawer_option_reset_crop_rect) {
      mCurrentFragment.resetCropRect();
      mDrawerLayout.closeDrawers();
    } else if (id == R.id.drawer_option_toggle_multitouch) {
      mCropImageViewOptions.multitouch = !mCropImageViewOptions.multitouch;
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else if (id == R.id.drawer_option_toggle_show_overlay) {
      mCropImageViewOptions.showCropOverlay = !mCropImageViewOptions.showCropOverlay;
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else if (id == R.id.drawer_option_toggle_show_progress_bar) {
      mCropImageViewOptions.showProgressBar = !mCropImageViewOptions.showProgressBar;
      mCurrentFragment.setCropImageViewOptions(mCropImageViewOptions);
      updateDrawerTogglesByOptions(mCropImageViewOptions);
    } else {
      Toast.makeText(this, "Unknown drawer option clicked", Toast.LENGTH_SHORT).show();
    }
  }

  private void setMainFragmentByPreset(CropDemoPreset demoPreset) {
    FragmentManager fragmentManager = getSupportFragmentManager();
    fragmentManager
            .beginTransaction()
            .replace(R.id.container, MainFragment.newInstance(demoPreset))
            .commit();
  }

  private void updateDrawerTogglesByOptions(CropImageViewOptions options) {
    ((TextView) findViewById(R.id.drawer_option_toggle_scale))
            .setText(getString(R.string.drawer_option_toggle_scale, options.scaleType.name()));
    ((TextView) findViewById(R.id.drawer_option_toggle_shape))
            .setText(getString(R.string.drawer_option_toggle_shape, options.cropShape.name()));
    ((TextView) findViewById(R.id.drawer_option_toggle_guidelines))
            .setText(getString(R.string.drawer_option_toggle_guidelines, options.guidelines.name()));
    ((TextView) findViewById(R.id.drawer_option_toggle_multitouch))
            .setText(getString(R.string.drawer_option_toggle_multitouch, Boolean.toString(options.multitouch)));
    ((TextView) findViewById(R.id.drawer_option_toggle_show_overlay))
            .setText(getString(R.string.drawer_option_toggle_show_overlay, Boolean.toString(options.showCropOverlay)));
    ((TextView) findViewById(R.id.drawer_option_toggle_show_progress_bar))
            .setText(getString(R.string.drawer_option_toggle_show_progress_bar, Boolean.toString(options.showProgressBar)));

    String aspectRatio = "FREE";
    if (options.fixAspectRatio && options.aspectRatio != null) {
      aspectRatio = options.aspectRatio.first + ":" + options.aspectRatio.second;
    }
    ((TextView) findViewById(R.id.drawer_option_toggle_aspect_ratio))
            .setText(getString(R.string.drawer_option_toggle_aspect_ratio, aspectRatio));

    ((TextView) findViewById(R.id.drawer_option_toggle_auto_zoom))
            .setText(getString(R.string.drawer_option_toggle_auto_zoom, options.autoZoomEnabled ? "Enabled" : "Disabled"));
    ((TextView) findViewById(R.id.drawer_option_toggle_max_zoom))
            .setText(getString(R.string.drawer_option_toggle_max_zoom, options.maxZoomLevel));
  }
}