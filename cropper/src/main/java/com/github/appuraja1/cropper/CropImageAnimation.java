package com.github.appuraja1.cropper;

import android.graphics.Matrix;
import android.graphics.RectF;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ImageView;

import androidx.annotation.NonNull;

/**
 * Modern animation class to handle smooth cropping image matrix transformation changes
 * specifically for auto-zoom and window adjustments without view flickering.
 */
final class CropImageAnimation extends Animation implements Animation.AnimationListener {

  private final ImageView mImageView;
  private final CropOverlayView mCropOverlayView;

  private final float[] mStartBoundPoints = new float[8];
  private final float[] mEndBoundPoints = new float[8];

  private final RectF mStartCropWindowRect = new RectF();
  private final RectF mEndCropWindowRect = new RectF();

  private final float[] mStartImageMatrix = new float[9];
  private final float[] mEndImageMatrix = new float[9];

  private final RectF mAnimRect = new RectF();
  private final float[] mAnimPoints = new float[8];
  private final float[] mAnimMatrix = new float[9];
  private final Matrix mWorkingMatrix = new Matrix();

  public CropImageAnimation(@NonNull ImageView cropImageView, @NonNull CropOverlayView cropOverlayView) {
    this.mImageView = cropImageView;
    this.mCropOverlayView = cropOverlayView;

    setDuration(300);
    setFillAfter(true);
    setInterpolator(new AccelerateDecelerateInterpolator());
    setAnimationListener(this);
  }

  public void setStartState(float[] boundPoints, Matrix imageMatrix) {
    reset();
    System.arraycopy(boundPoints, 0, mStartBoundPoints, 0, 8);
    mStartCropWindowRect.set(mCropOverlayView.getCropWindowRect());
    imageMatrix.getValues(mStartImageMatrix);
  }

  public void setEndState(float[] boundPoints, Matrix imageMatrix) {
    System.arraycopy(boundPoints, 0, mEndBoundPoints, 0, 8);
    mEndCropWindowRect.set(mCropOverlayView.getCropWindowRect());
    imageMatrix.getValues(mEndImageMatrix);
  }

  @Override
  protected void applyTransformation(float interpolatedTime, Transformation t) {
    // Interpolate Crop Window Rect
    mAnimRect.left =
            mStartCropWindowRect.left
                    + (mEndCropWindowRect.left - mStartCropWindowRect.left) * interpolatedTime;
    mAnimRect.top =
            mStartCropWindowRect.top
                    + (mEndCropWindowRect.top - mStartCropWindowRect.top) * interpolatedTime;
    mAnimRect.right =
            mStartCropWindowRect.right
                    + (mEndCropWindowRect.right - mStartCropWindowRect.right) * interpolatedTime;
    mAnimRect.bottom =
            mStartCropWindowRect.bottom
                    + (mEndCropWindowRect.bottom - mStartCropWindowRect.bottom) * interpolatedTime;
    mCropOverlayView.setCropWindowRect(mAnimRect);

    // Interpolate Bounds
    for (int i = 0; i < mAnimPoints.length; i++) {
      mAnimPoints[i] =
              mStartBoundPoints[i] + (mEndBoundPoints[i] - mStartBoundPoints[i]) * interpolatedTime;
    }
    mCropOverlayView.setBounds(mAnimPoints, mImageView.getWidth(), mImageView.getHeight());

    // Interpolate Matrix
    for (int i = 0; i < mAnimMatrix.length; i++) {
      mAnimMatrix[i] =
              mStartImageMatrix[i] + (mEndImageMatrix[i] - mStartImageMatrix[i]) * interpolatedTime;
    }
    mWorkingMatrix.setValues(mAnimMatrix);
    mImageView.setImageMatrix(mWorkingMatrix);

    mImageView.invalidate();
    mCropOverlayView.invalidate();
  }

  @Override
  public void onAnimationStart(Animation animation) {}

  @Override
  public void onAnimationEnd(Animation animation) {
    // Explicitly lock the end state to avoid flicker before clearing
    mWorkingMatrix.setValues(mEndImageMatrix);
    mImageView.setImageMatrix(mWorkingMatrix);
    mCropOverlayView.setCropWindowRect(mEndCropWindowRect);
    mCropOverlayView.setBounds(mEndBoundPoints, mImageView.getWidth(), mImageView.getHeight());
    mImageView.clearAnimation();
  }

  @Override
  public void onAnimationRepeat(Animation animation) {}
}