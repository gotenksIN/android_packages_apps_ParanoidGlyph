/*
 * Copyright (C) 2026 Paranoid Android
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

package co.aospa.glyph.utils;

import android.util.Log;

import com.android.internal.util.ArrayUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AnimationUtils {

    private static final String TAG = "GlyphAnimationUtils";

    public static final int MAX_SAMPLE_BRIGHTNESS = 4095;

    public enum Category {
        ANIMATION,
        CALL
    }

    public static final class Animation {
        private final int[][] mFrames;

        private Animation(int[][] frames) {
            mFrames = frames;
        }

        public int getFrameCount() {
            return mFrames.length;
        }

        public int[] getFrame(int index) {
            return mFrames[index];
        }
    }

    private static final class CacheKey {
        private final String mName;
        private final Category mCategory;

        private CacheKey(String name, Category category) {
            mName = name;
            mCategory = category;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof CacheKey)) return false;
            CacheKey other = (CacheKey) object;
            return mName.equals(other.mName) && mCategory == other.mCategory;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mName, mCategory);
        }
    }

    private static final ExecutorService DECODER_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "GlyphAnimationDecoder");
                thread.setDaemon(true);
                return thread;
            });
    private static final ConcurrentHashMap<CacheKey, CompletableFuture<Animation>> CACHE =
            new ConcurrentHashMap<>();

    private AnimationUtils() {}

    public static CompletableFuture<Animation> load(String name, Category category) {
        CacheKey key = new CacheKey(name, category);
        return CACHE.computeIfAbsent(key, unused -> CompletableFuture.supplyAsync(
                () -> decode(name, category), DECODER_EXECUTOR));
    }

    private static Animation decode(String name, Category category) {
        try (InputStream stream = category == Category.CALL
                ? ResourceUtils.getCallAnimation(name)
                : ResourceUtils.getAnimation(name);
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            int[][] frames = reader.lines()
                    .map(String::trim)
                    .map(AnimationUtils::decodeFrame)
                    .toArray(int[][]::new);
            if (frames.length == 0) {
                throw new IllegalArgumentException("animation is empty");
            }
            return new Animation(frames);
        } catch (IOException | UncheckedIOException | IllegalArgumentException e) {
            Log.w(TAG, "Animation unavailable | category: " + category
                    + " | name: " + name, e);
            return null;
        }
    }

    private static int[] decodeFrame(String line) {
        if (line.endsWith(",")) {
            line = line.substring(0, line.length() - 1).trim();
        }
        String[] values = line.split(",", -1);
        if (!ArrayUtils.contains(Constants.getSupportedAnimationPatternLengths(), values.length)) {
            throw new IllegalArgumentException("unsupported frame width: " + values.length);
        }
        int[] frame = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            frame[i] = Integer.parseInt(values[i].trim());
            if (frame[i] < 0 || frame[i] > MAX_SAMPLE_BRIGHTNESS) {
                throw new IllegalArgumentException("brightness outside sample range: " + frame[i]);
            }
        }
        return frame;
    }
}
