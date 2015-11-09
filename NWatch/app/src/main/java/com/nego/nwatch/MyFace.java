/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.nego.nwatch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.Time;
import android.view.SurfaceHolder;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Analog watch face with a ticking second hand. In ambient mode, the second hand isn't shown. On
 * devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient mode.
 */
public class MyFace extends CanvasWatchFaceService {
    /**
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        Paint mBackgroundPaint;
        Paint mCirclePaint;
        Paint mHandPaintHr;
        Paint mHandPaintMin;
        Paint mHandPaintSec;
        Paint mCirclePaintBack;
        Paint cPoints;
        Paint mTextPaint;

        float[] angles = new float[] {0f, (float) Math.PI / 4, (float) Math.PI / 2, (float) Math.PI / 4 * 3, (float) Math.PI, (float) Math.PI + (float) Math.PI /4, (float) Math.PI + (float) Math.PI / 2, (float) Math.PI * 2 - (float) Math.PI / 4};

        boolean mAmbient;
        Time mTime;

        final Handler mUpdateTimeHandler = new EngineHandler(this);

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mTime.clear(intent.getStringExtra("time-zone"));
                mTime.setToNow();
            }
        };
        boolean mRegisteredTimeZoneReceiver = false;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(MyFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = MyFace.this.getResources();

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.analog_background));

            mCirclePaint = new Paint();
            mCirclePaint.setColor(resources.getColor(R.color.analog_hands));
            mCirclePaint.setShadowLayer(5f, 0, 0, resources.getColor(android.R.color.black));
            mCirclePaint.setAntiAlias(true);

            mCirclePaintBack = new Paint();
            mCirclePaintBack.setColor(resources.getColor(R.color.analog_background_dark));
            mCirclePaintBack.setAntiAlias(true);

            mHandPaintHr = new Paint();
            mHandPaintHr.setColor(resources.getColor(R.color.analog_hands));
            mHandPaintHr.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaintHr.setAntiAlias(true);
            mHandPaintHr.setStrokeCap(Paint.Cap.ROUND);

            mHandPaintMin = new Paint();
            mHandPaintMin.setColor(resources.getColor(R.color.analog_hands));
            mHandPaintMin.setStrokeWidth(resources.getDimension(R.dimen.analog_hand_stroke));
            mHandPaintMin.setAntiAlias(true);
            mHandPaintMin.setStrokeCap(Paint.Cap.ROUND);

            mHandPaintSec = new Paint();
            mHandPaintSec.setColor(resources.getColor(R.color.analog_background_dark));
            mHandPaintSec.setAntiAlias(true);

            cPoints = new Paint();
            cPoints.setColor(resources.getColor(R.color.analog_hands));
            cPoints.setAntiAlias(true);

            mTextPaint = new Paint();
            mTextPaint.setColor(resources.getColor(R.color.analog_background_dark));
            mTextPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            mTextPaint.setAntiAlias(true);
            mTextPaint.setTextSize(resources.getDimension(R.dimen.date_text_size));
            mTextPaint.setTextAlign(Paint.Align.CENTER);

            mTime = new Time();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mHandPaintHr.setAntiAlias(!inAmbientMode);
                    mHandPaintMin.setAntiAlias(!inAmbientMode);
                    mHandPaintSec.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            mTime.setToNow();

            int width = bounds.width();
            int height = bounds.height();

            // Draw the background.
            canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), mBackgroundPaint);


            float centerX = width / 2f;
            float centerY = height / 2f;

            if (!mAmbient) {
                Calendar c = Calendar.getInstance();
                String text = String.format("%d %s", c.get(Calendar.DAY_OF_MONTH), c.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()));
                canvas.drawText(text, bounds.centerX(), bounds.height() - getResources().getDimension(R.dimen.date_offset_y), mTextPaint);
            }


            float secRot = mTime.second / 30f * (float) Math.PI;
            int minutes = mTime.minute;
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((mTime.hour + (minutes / 60f)) / 6f) * (float) Math.PI;

            float minLength = centerX - 50;
            float hrLength = centerX - 100;

            canvas.drawCircle(centerX, centerY, 20, mCirclePaintBack);

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, mHandPaintMin);

            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, mHandPaintHr);

            canvas.drawCircle(centerX, centerY, 10, mCirclePaint);

            if (!mAmbient) {
                float posCX = (float) Math.sin(secRot) * (centerX - 20);
                float posCY = (float) -Math.cos(secRot) * (centerY - 20);
                canvas.drawCircle(centerX + posCX, centerY + posCY, 6, mHandPaintSec);

                for (float a : angles) {
                    float posX = (float) Math.sin(a) * (centerX - 20);
                    float posY = (float) -Math.cos(a) * (centerY - 20);
                    canvas.drawCircle(centerX + posX, centerY + posY, 6, cPoints);
                }
            }

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mTime.clear(TimeZone.getDefault().getID());
                mTime.setToNow();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            MyFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            MyFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<MyFace.Engine> mWeakReference;

        public EngineHandler(MyFace.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            MyFace.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }
}
