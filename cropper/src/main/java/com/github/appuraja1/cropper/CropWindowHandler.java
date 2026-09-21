package com.github.appuraja1.cropper;

import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Modern handler for tracking, bounding, and calculating crop window coordinates,
 * scaling factors, and touch handle hit testing.
 */
final class CropWindowHandler {

  private final RectF mEdges = new RectF();
  private final RectF mGetEdges = new RectF();

  private float mMinCropWindowWidth;
  private float mMinCropWindowHeight;
  private float mMaxCropWindowWidth;
  private float mMaxCropWindowHeight;

  private float mMinCropResultWidth;
  private float mMinCropResultHeight;
  private float mMaxCropResultWidth;
  private float mMaxCropResultHeight;

  private float mScaleFactorWidth = 1f;
  private float mScaleFactorHeight = 1f;

  public RectF getRect() {
    mGetEdges.set(mEdges);
    return mGetEdges;
  }

  public float getMinCropWidth() {
    float scale = mScaleFactorWidth > 0 ? mScaleFactorWidth : 1f;
    return Math.max(mMinCropWindowWidth, mMinCropResultWidth / scale);
  }

  public float getMinCropHeight() {
    float scale = mScaleFactorHeight > 0 ? mScaleFactorHeight : 1f;
    return Math.max(mMinCropWindowHeight, mMinCropResultHeight / scale);
  }

  public float getMaxCropWidth() {
    float scale = mScaleFactorWidth > 0 ? mScaleFactorWidth : 1f;
    return Math.min(mMaxCropWindowWidth, mMaxCropResultWidth / scale);
  }

  public float getMaxCropHeight() {
    float scale = mScaleFactorHeight > 0 ? mScaleFactorHeight : 1f;
    return Math.min(mMaxCropWindowHeight, mMaxCropResultHeight / scale);
  }

  public float getScaleFactorWidth() {
    return mScaleFactorWidth > 0 ? mScaleFactorWidth : 1f;
  }

  public float getScaleFactorHeight() {
    return mScaleFactorHeight > 0 ? mScaleFactorHeight : 1f;
  }

  public void setMinCropResultSize(int minCropResultWidth, int minCropResultHeight) {
    mMinCropResultWidth = minCropResultWidth;
    mMinCropResultHeight = minCropResultHeight;
  }

  public void setMaxCropResultSize(int maxCropResultWidth, int maxCropResultHeight) {
    mMaxCropResultWidth = maxCropResultWidth;
    mMaxCropResultHeight = maxCropResultHeight;
  }

  public void setCropWindowLimits(
          float maxWidth, float maxHeight, float scaleFactorWidth, float scaleFactorHeight) {
    mMaxCropWindowWidth = maxWidth;
    mMaxCropWindowHeight = maxHeight;
    mScaleFactorWidth = scaleFactorWidth > 0 ? scaleFactorWidth : 1f;
    mScaleFactorHeight = scaleFactorHeight > 0 ? scaleFactorHeight : 1f;
  }

  public void setInitialAttributeValues(@NonNull CropImageOptions options) {
    mMinCropWindowWidth = options.minCropWindowWidth;
    mMinCropWindowHeight = options.minCropWindowHeight;
    mMinCropResultWidth = options.minCropResultWidth;
    mMinCropResultHeight = options.minCropResultHeight;
    mMaxCropResultWidth = options.maxCropResultWidth;
    mMaxCropResultHeight = options.maxCropResultHeight;
  }

  public void setRect(@NonNull RectF rect) {
    mEdges.set(rect);
  }

  public boolean showGuidelines() {
    return !(mEdges.width() < 100 || mEdges.height() < 100);
  }

  @Nullable
  public CropWindowMoveHandler getMoveHandler(
          float x, float y, float targetRadius, @NonNull CropImageView.CropShape cropShape) {
    CropWindowMoveHandler.Type type =
            cropShape == CropImageView.CropShape.OVAL
                    ? getOvalPressedMoveType(x, y)
                    : getRectanglePressedMoveType(x, y, targetRadius);
    return type != null ? new CropWindowMoveHandler(type, this, x, y) : null;
  }

  @Nullable
  private CropWindowMoveHandler.Type getRectanglePressedMoveType(
          float x, float y, float targetRadius) {
    // 1. Corners (highest precedence)
    if (isInCornerTargetZone(x, y, mEdges.left, mEdges.top, targetRadius)) {
      return CropWindowMoveHandler.Type.TOP_LEFT;
    } else if (isInCornerTargetZone(x, y, mEdges.right, mEdges.top, targetRadius)) {
      return CropWindowMoveHandler.Type.TOP_RIGHT;
    } else if (isInCornerTargetZone(x, y, mEdges.left, mEdges.bottom, targetRadius)) {
      return CropWindowMoveHandler.Type.BOTTOM_LEFT;
    } else if (isInCornerTargetZone(x, y, mEdges.right, mEdges.bottom, targetRadius)) {
      return CropWindowMoveHandler.Type.BOTTOM_RIGHT;
    }

    // 2. Edges/Borders
    if (isInHorizontalTargetZone(x, y, mEdges.left, mEdges.right, mEdges.top, targetRadius)) {
      return CropWindowMoveHandler.Type.TOP;
    } else if (isInHorizontalTargetZone(x, y, mEdges.left, mEdges.right, mEdges.bottom, targetRadius)) {
      return CropWindowMoveHandler.Type.BOTTOM;
    } else if (isInVerticalTargetZone(x, y, mEdges.left, mEdges.top, mEdges.bottom, targetRadius)) {
      return CropWindowMoveHandler.Type.LEFT;
    } else if (isInVerticalTargetZone(x, y, mEdges.right, mEdges.top, mEdges.bottom, targetRadius)) {
      return CropWindowMoveHandler.Type.RIGHT;
    }

    // 3. Center/Drag Inside
    if (isInCenterTargetZone(x, y, mEdges.left, mEdges.top, mEdges.right, mEdges.bottom)) {
      return CropWindowMoveHandler.Type.CENTER;
    }

    return null;
  }

  @Nullable
  private CropWindowMoveHandler.Type getOvalPressedMoveType(float x, float y) {
    if (mEdges.width() <= 0 || mEdges.height() <= 0) {
      return null;
    }

    float cellLength = mEdges.width() / 6f;
    float leftCenter = mEdges.left + cellLength;
    float rightCenter = mEdges.left + (5f * cellLength);

    float cellHeight = mEdges.height() / 6f;
    float topCenter = mEdges.top + cellHeight;
    float bottomCenter = mEdges.top + (5f * cellHeight);

    if (x < leftCenter) {
      if (y < topCenter) {
        return CropWindowMoveHandler.Type.TOP_LEFT;
      } else if (y < bottomCenter) {
        return CropWindowMoveHandler.Type.LEFT;
      } else {
        return CropWindowMoveHandler.Type.BOTTOM_LEFT;
      }
    } else if (x < rightCenter) {
      if (y < topCenter) {
        return CropWindowMoveHandler.Type.TOP;
      } else if (y < bottomCenter) {
        return CropWindowMoveHandler.Type.CENTER;
      } else {
        return CropWindowMoveHandler.Type.BOTTOM;
      }
    } else {
      if (y < topCenter) {
        return CropWindowMoveHandler.Type.TOP_RIGHT;
      } else if (y < bottomCenter) {
        return CropWindowMoveHandler.Type.RIGHT;
      } else {
        return CropWindowMoveHandler.Type.BOTTOM_RIGHT;
      }
    }
  }

  private static boolean isInCornerTargetZone(
          float x, float y, float handleX, float handleY, float targetRadius) {
    return Math.abs(x - handleX) <= targetRadius && Math.abs(y - handleY) <= targetRadius;
  }

  private static boolean isInHorizontalTargetZone(
          float x, float y, float handleXStart, float handleXEnd, float handleY, float targetRadius) {
    return x > handleXStart && x < handleXEnd && Math.abs(y - handleY) <= targetRadius;
  }

  private static boolean isInVerticalTargetZone(
          float x, float y, float handleX, float handleYStart, float handleYEnd, float targetRadius) {
    return Math.abs(x - handleX) <= targetRadius && y > handleYStart && y < handleYEnd;
  }

  private static boolean isInCenterTargetZone(
          float x, float y, float left, float top, float right, float bottom) {
    return x > left && x < right && y > top && y < bottom;
  }
}