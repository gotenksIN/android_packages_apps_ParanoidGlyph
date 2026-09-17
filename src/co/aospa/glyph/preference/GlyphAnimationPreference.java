/*
 * Copyright (C) 2023-2024 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.aospa.glyph.preference;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import co.aospa.glyph.R;
import co.aospa.glyph.utils.AnimationUtils;
import co.aospa.glyph.utils.Constants;
import co.aospa.glyph.utils.ResourceUtils;

public class GlyphAnimationPreference extends Preference {

    private static final String TAG = "GlyphAnimationPreference";
    private static final boolean DEBUG = true;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;
    private static final int CONTENT_FRAME_RATE = 60;
    private static final String PREVIEW_ZONE_MAP_RESOURCE_PREFIX =
            "glyph_settings_animations_preview_zone_map_";

    private String animationName;
    private boolean animationTerminated = true;
    private boolean animationPaused = true;
    private int animationTimeBetween;
    private String[] animationSlugs;
    private ImageView[] animationImgs;
    private float[] mLastZoneValues;

    private AnimationUtils.Animation mAnimation;
    private int mLoadGeneration;
    private int mLoadingGeneration = -1;
    private long mAnimationStartNanos = -1;
    private boolean mFrameCallbackPosted;
    private int mPreviewZoneMapFrameWidth = -1;
    private int[] mPreviewZoneMap;
    private final Set<Integer> mLoggedInvalidPreviewZoneMaps = new HashSet<>();

    private View mRootView;
    private final View.OnClickListener mClickListener = v -> performClick(v);
    private final Choreographer.FrameCallback mFrameCallback = this::doFrame;
    private final ViewTreeObserver.OnWindowVisibilityChangeListener
            mWindowVisibilityChangeListener = visibility -> updateCallbackState();
    private final ViewTreeObserver.OnGlobalLayoutListener mGlobalLayoutListener =
            this::updateCallbackState;
    private final View.OnAttachStateChangeListener mAttachStateChangeListener =
            new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View view) {
                    ViewTreeObserver observer = view.getViewTreeObserver();
                    observer.addOnWindowVisibilityChangeListener(
                            mWindowVisibilityChangeListener);
                    observer.addOnGlobalLayoutListener(mGlobalLayoutListener);
                    updateCallbackState();
                }

                @Override
                public void onViewDetachedFromWindow(View view) {
                    mLoadGeneration++;
                    mLoadingGeneration = -1;
                    ViewTreeObserver observer = view.getViewTreeObserver();
                    if (observer.isAlive()) {
                        observer.removeOnWindowVisibilityChangeListener(
                                mWindowVisibilityChangeListener);
                        observer.removeOnGlobalLayoutListener(mGlobalLayoutListener);
                    }
                    removeFrameCallback();
                }
            };

    public GlyphAnimationPreference(Context context) {
        super(context);
        setLayout(R.layout.glyph_settings_preview);
    }

    public GlyphAnimationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayout(R.layout.glyph_settings_preview);
    }

    public GlyphAnimationPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setLayout(R.layout.glyph_settings_preview);
    }

    public GlyphAnimationPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr);
        setLayout(defStyleRes);
    }

    private void setLayout(int layoutResource) {
        setLayoutResource(R.layout.glyph_settings_preview_frame);
        mRootView = LayoutInflater.from(getContext())
                .inflate(layoutResource, null, false);
        mRootView.addOnAttachStateChangeListener(mAttachStateChangeListener);
        setShouldDisableView(false);
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        holder.itemView.setOnClickListener(mClickListener);

        holder.itemView.setFocusable(isSelectable());
        holder.itemView.setClickable(isSelectable());

        FrameLayout layout = (FrameLayout) holder.itemView;
        layout.removeAllViews();
        ViewGroup parent = (ViewGroup) mRootView.getParent();
        if (parent != null) {
            parent.removeView(mRootView);
        }
        layout.addView(mRootView);
    }

    @Override
    public void onAttached() {
        super.onAttached();
        if (DEBUG) Log.d(TAG, "onAttached");
        startAnimation();
    }

    @Override
    public void onDetached() {
        super.onDetached();
        if (DEBUG) Log.d(TAG, "onDetached");
        stopAnimation();
    }

    private void startAnimation() {
        animationTerminated = false;
        animationSlugs = ResourceUtils.getStringArray("glyph_settings_animations_slugs");
        animationImgs = new ImageView[animationSlugs.length];
        mLastZoneValues = new float[animationSlugs.length];
        Arrays.fill(mLastZoneValues, Float.NaN);
        for (int i = 0; i < animationSlugs.length; i++) {
            animationImgs[i] = (ImageView) mRootView.findViewById(
                    ResourceUtils.getIdentifier("preview_device_" + animationSlugs[i], "id"));
        }
        updateCallbackState();
    }

    private void stopAnimation() {
        animationTerminated = true;
        animationPaused = true;
        mLoadGeneration++;
        mAnimation = null;
        removeFrameCallback();
        clearGlyphs();
    }

    public void updateAnimation(boolean play) {
        updateAnimation(play, animationName, 0);
    }

    public void updateAnimation(boolean play, String name) {
        updateAnimation(play, name, 0);
    }

    public void updateAnimation(boolean play, String name, int time) {
        boolean animationChanged = !Objects.equals(animationName, name);
        animationTimeBetween = time;
        animationName = name;
        animationPaused = !play;
        mLoadGeneration++;
        mAnimationStartNanos = -1;
        if (animationChanged) {
            mAnimation = null;
        }
        if (animationPaused) {
            removeFrameCallback();
            clearGlyphs();
        } else {
            updateCallbackState();
        }
    }

    private void updateCallbackState() {
        if (!shouldRunAnimation()) {
            removeFrameCallback();
            return;
        }
        if (mAnimation == null) {
            loadAnimation();
            return;
        }
        if (!mFrameCallbackPosted) {
            mFrameCallbackPosted = true;
            Choreographer.getInstance().postFrameCallback(mFrameCallback);
        }
    }

    private boolean shouldRunAnimation() {
        return !animationTerminated && !animationPaused && animationName != null
                && mRootView.isAttachedToWindow()
                && mRootView.getWindowVisibility() == View.VISIBLE
                && mRootView.isShown();
    }

    private void loadAnimation() {
        int generation = mLoadGeneration;
        if (mLoadingGeneration == generation) return;
        mLoadingGeneration = generation;
        String name = animationName;
        if (DEBUG) Log.d(TAG, "Loading animation | name: " + name);
        AnimationUtils.load(name, AnimationUtils.Category.ANIMATION)
                .thenAccept(animation -> mRootView.post(() -> {
                    if (generation != mLoadGeneration || animationTerminated
                            || !Objects.equals(name, animationName)
                            || !mRootView.isAttachedToWindow()) {
                        return;
                    }
                    mLoadingGeneration = -1;
                    if (animation == null) {
                        animationPaused = true;
                        clearGlyphs();
                        return;
                    }
                    mAnimation = animation;
                    mAnimationStartNanos = -1;
                    updateCallbackState();
                }));
    }

    private void doFrame(long frameTimeNanos) {
        mFrameCallbackPosted = false;
        if (!shouldRunAnimation() || mAnimation == null) return;

        if (mAnimationStartNanos < 0) {
            mAnimationStartNanos = frameTimeNanos;
        }
        long contentDurationNanos = mAnimation.getFrameCount()
                * NANOS_PER_SECOND / CONTENT_FRAME_RATE;
        long cycleDurationNanos = contentDurationNanos
                + animationTimeBetween * 1_000_000L;
        long cycleTimeNanos = (frameTimeNanos - mAnimationStartNanos)
                % cycleDurationNanos;
        double framePosition = cycleTimeNanos * CONTENT_FRAME_RATE
                / (double) NANOS_PER_SECOND;
        int frameIndex = cycleTimeNanos >= contentDurationNanos
                ? mAnimation.getFrameCount() - 1
                : (int) framePosition;
        int nextFrameIndex = Math.min(frameIndex + 1, mAnimation.getFrameCount() - 1);
        float frameFraction = cycleTimeNanos >= contentDurationNanos
                ? 0f : (float) (framePosition - frameIndex);
        if (!applyFrame(mAnimation.getFrame(frameIndex),
                mAnimation.getFrame(nextFrameIndex), frameFraction)) {
            animationPaused = true;
            clearGlyphs();
            return;
        }
        updateCallbackState();
    }

    private boolean applyFrame(int[] frame, int[] nextFrame, float fraction) {
        if (!isPreviewFrameSupported(frame) || !isPreviewFrameSupported(nextFrame)) {
            if (DEBUG) Log.d(TAG, "Animation frame length mismatch | name: "
                    + animationName + " | lengths: " + frame.length + ", " + nextFrame.length);
            return false;
        }
        int[] zoneMap = getPreviewZoneMap(frame.length);
        int[] nextZoneMap = frame.length == nextFrame.length
                ? zoneMap : getPreviewZoneMap(nextFrame.length);
        if (zoneMap == null || nextZoneMap == null) return false;

        for (int i = 0; i < animationSlugs.length; i++) {
            int brightness = getZoneValue(frame, zoneMap, i);
            int nextBrightness = getZoneValue(nextFrame, nextZoneMap, i);
            float interpolatedBrightness = brightness
                    + (nextBrightness - brightness) * fraction;
            setZoneValue(i, interpolatedBrightness);
        }
        return true;
    }

    private boolean isPreviewFrameSupported(int[] frame) {
        for (int patternLength : Constants.getSupportedAnimationPatternLengths()) {
            if (frame.length == patternLength) return true;
        }
        return false;
    }

    private int[] getPreviewZoneMap(int frameWidth) {
        if (mPreviewZoneMapFrameWidth == frameWidth) return mPreviewZoneMap;

        mPreviewZoneMapFrameWidth = frameWidth;
        mPreviewZoneMap = null;
        int[] zoneMap = ResourceUtils.getIntArray(
                PREVIEW_ZONE_MAP_RESOURCE_PREFIX + frameWidth);
        if (zoneMap.length == 0) {
            logInvalidPreviewZoneMap(frameWidth, "resource is missing");
            return null;
        }
        if (zoneMap.length != animationSlugs.length) {
            logInvalidPreviewZoneMap(frameWidth, "zone count is " + zoneMap.length
                    + ", expected " + animationSlugs.length);
            return null;
        }
        for (int channel : zoneMap) {
            if (channel < 0 || channel >= frameWidth) {
                logInvalidPreviewZoneMap(frameWidth, "channel " + channel
                        + " is outside frame width " + frameWidth);
                return null;
            }
        }
        mPreviewZoneMap = zoneMap;
        return mPreviewZoneMap;
    }

    private void logInvalidPreviewZoneMap(int frameWidth, String reason) {
        if (mLoggedInvalidPreviewZoneMaps.add(frameWidth)) {
            Log.w(TAG, "Preview zone map is invalid | width: " + frameWidth
                    + " | " + reason);
        }
    }

    private int getZoneValue(int[] frame, int[] zoneMap, int zone) {
        return frame[zoneMap[zone]];
    }

    private void setZoneValue(int zone, float brightness) {
        if (Float.compare(mLastZoneValues[zone], brightness) == 0) return;
        mLastZoneValues[zone] = brightness;
        setGlyphsDrawable(animationImgs[zone], brightness);
    }

    private void clearGlyphs() {
        if (animationImgs == null) return;
        for (int i = 0; i < animationImgs.length; i++) {
            setZoneValue(i, 0);
        }
    }

    private void removeFrameCallback() {
        if (!mFrameCallbackPosted) return;
        Choreographer.getInstance().removeFrameCallback(mFrameCallback);
        mFrameCallbackPosted = false;
    }

    private void setGlyphsDrawable(ImageView imageView, float brightness) {
        if (brightness <= 0) {
            imageView.setAlpha(0.3f);
        } else {
            float brightnessFactor = (float) (0.4 + 0.6
                    * (brightness / (double) Constants.getMaxBrightness()));
            imageView.setAlpha(brightnessFactor);
        }
    }
}
