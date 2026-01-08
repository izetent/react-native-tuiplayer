package com.txplayer.rnuiplayer.view;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Simple FrameLayout that keeps its child at a target aspect ratio.
 * resizeMode: FIT = contain (letterbox), FILL = cover (center-crop).
 */
public class AspectRatioFrameLayout extends FrameLayout {

  public static final int RESIZE_MODE_FIT = 0;
  public static final int RESIZE_MODE_FILL = 1;

  private float aspectRatio = 0f;
  private int resizeMode = RESIZE_MODE_FIT;

  public AspectRatioFrameLayout(@NonNull Context context) {
    super(context);
  }

  public AspectRatioFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
    super(context, attrs);
  }

  public void setAspectRatio(float aspectRatio) {
    float safeRatio = aspectRatio > 0 ? aspectRatio : 0f;
    if (Float.isNaN(safeRatio)) {
      safeRatio = 0f;
    }
    if (Math.abs(this.aspectRatio - safeRatio) > 0.001f) {
      this.aspectRatio = safeRatio;
      requestLayout();
    }
  }

  public void resetAspectRatio() {
    setAspectRatio(0f);
  }

  public void setResizeMode(int resizeMode) {
    if (this.resizeMode != resizeMode) {
      this.resizeMode = resizeMode;
      requestLayout();
    }
  }

  public int getResizeMode() {
    return resizeMode;
  }

  @Override
  protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    if (getChildCount() == 0) {
      return;
    }
    int measuredWidth = getMeasuredWidth();
    int measuredHeight = getMeasuredHeight();
    int paddingLeft = getPaddingLeft();
    int paddingRight = getPaddingRight();
    int paddingTop = getPaddingTop();
    int paddingBottom = getPaddingBottom();
    int availableWidth = Math.max(0, measuredWidth - paddingLeft - paddingRight);
    int availableHeight = Math.max(0, measuredHeight - paddingTop - paddingBottom);
    int childWidth = availableWidth;
    int childHeight = availableHeight;
    if (aspectRatio > 0f && availableWidth > 0 && availableHeight > 0) {
      float viewRatio = (float) availableWidth / (float) availableHeight;
      float diff = aspectRatio / viewRatio - 1f;
      if (resizeMode == RESIZE_MODE_FIT) {
        if (diff > 0) {
          childHeight = (int) (availableWidth / aspectRatio);
        } else {
          childWidth = (int) (availableHeight * aspectRatio);
        }
      } else {
        if (diff > 0) {
          childWidth = (int) (availableHeight * aspectRatio);
        } else {
          childHeight = (int) (availableWidth / aspectRatio);
        }
      }
    }
    int childWidthSpec =
        MeasureSpec.makeMeasureSpec(Math.max(0, childWidth), MeasureSpec.EXACTLY);
    int childHeightSpec =
        MeasureSpec.makeMeasureSpec(Math.max(0, childHeight), MeasureSpec.EXACTLY);
    for (int i = 0; i < getChildCount(); i++) {
      getChildAt(i).measure(childWidthSpec, childHeightSpec);
    }
  }

  @Override
  protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
    if (getChildCount() == 0) {
      return;
    }
    int parentWidth = right - left;
    int parentHeight = bottom - top;
    int paddingLeft = getPaddingLeft();
    int paddingRight = getPaddingRight();
    int paddingTop = getPaddingTop();
    int paddingBottom = getPaddingBottom();
    int availableWidth = Math.max(0, parentWidth - paddingLeft - paddingRight);
    int availableHeight = Math.max(0, parentHeight - paddingTop - paddingBottom);
    for (int i = 0; i < getChildCount(); i++) {
      android.view.View child = getChildAt(i);
      int cw = child.getMeasuredWidth();
      int ch = child.getMeasuredHeight();
      int cl = paddingLeft + (availableWidth - cw) / 2;
      int ct = paddingTop + (availableHeight - ch) / 2;
      child.layout(cl, ct, cl + cw, ct + ch);
    }
  }
}
