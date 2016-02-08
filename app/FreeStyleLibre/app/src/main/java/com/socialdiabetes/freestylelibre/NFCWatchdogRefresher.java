package com.socialdiabetes.freestylelibre;

import android.annotation.TargetApi;
import android.nfc.Tag;
import android.nfc.tech.TagTechnology;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Taken from https://gist.github.com/Thorbear/f7c48e90d3e71bde13cb
 * designed to fix https://code.google.com/p/android/issues/detail?id=58773
 *
 * modified to suit any tag technology just by changing {@link #TAG_TECHNOLOGY}
 *  by 4gra@github.
 *
 * <p>The purpose of this class is to keep the android system from
 * sending keep-alive commands to NFC tags.</p>
 * <p>This is necessary on some of the most common devices because
 * their implementation of keep-alive isn't according to the NFC
 * specification. The result of this is that a keep-alive command
 * can abort an authentication process that utilizes a
 * challenge-response mechanism, which Mifare DESFire does.</p>
 * <p>A common usage pattern will be to do some NFC communication,
 * call {@link #holdConnection(TagTechnology)}, communicate with a webservice,
 * call {@link #stopHoldingConnection()}, and do some more NFC
 * communication.</p>
 */
@TargetApi(Build.VERSION_CODES.GINGERBREAD_MR1)

public class NFCWatchdogRefresher {

    private static final String TAG = NFCWatchdogRefresher.class.getSimpleName();
    private static final int TAG_TECHNOLOGY = 5;
    // ISO_DEP = 3;
    // NFC_V = 5;

    /**
     * Used to ensure that this runnable self-stops after 30 seconds
     * if not stopped externally.  Default value.
     */
    private static final int RUNTIME_MAX = 30 * 1000;

    @Nullable
    private static HandlerThread sHandlerThread;
    @Nullable
    private static Handler sHandler;
    @Nullable
    private static WatchdogRefresher sRefresher;

    private static volatile boolean sIsRunning = false;

    /**
     * <p>Should be called as soon as possible after the last NFC communication.</p>
     * <p>If this method is called multiple times without any calls to
     * {@link #stopHoldingConnection()}, each subsequent call will automatically
     * cancel the previous one.</p>
     */
    public static void holdConnection(TagTechnology tagtech, int maxRuntime) {
        Log.v(TAG, "holdConnection()");
        if (sHandlerThread != null || sHandler != null || sRefresher != null) {
            Log.d(TAG, "holdConnection(): Existing background thread found, stopping!");
            stopHoldingConnection();
        }
        sHandlerThread = new HandlerThread("NFCWatchdogRefresherThread");
        try {
            sHandlerThread.start();
        } catch (IllegalThreadStateException e) {
            Log.d(TAG, "holdConnection(): Failed starting background thread!", e);
        }
        Looper looper = sHandlerThread.getLooper();
        if (looper != null) {
            sHandler = new Handler(looper);
        } else {
            Log.d(TAG, "holdConnection(): No looper on background thread!");
            sHandlerThread.quit();
            sHandler = new Handler();
        }
        sIsRunning = true;
        sRefresher = new WatchdogRefresher(sHandler, tagtech, maxRuntime);
        sHandler.post(sRefresher);
    }

    public static void holdConnection(TagTechnology tagtech) {
        holdConnection(tagtech, RUNTIME_MAX);
    }

    /**
     * Should be called before NFC communication is made if
     * {@link #holdConnection(android.nfc.tech.TagTechnology)} has been called since
     * the last communication.
     */
    public static void stopHoldingConnection() {
        Log.v(TAG, "stopHoldingConnection()");
        sIsRunning = false;
        if (sHandler != null) {
            if (sRefresher != null) {
                sHandler.removeCallbacks(sRefresher);
            }
            sHandler.removeCallbacksAndMessages(null);
            sHandler = null;
        }
        if (sRefresher != null) {
            sRefresher = null;
        }
        if (sHandlerThread != null) {
            sHandlerThread.quit();
            sHandlerThread = null;
        }
    }

    /**
     * Runnable that uses reflection to keep the NFC watchdog from
     * reaching its timeout and sending a keep-alive communication.
     * <p/>
     * This works by telling the TagService to connect, if the tag
     * is already connected, it will return the success status and reset
     * the timeout.
     * <p/>
     * The default timeout is 125ms, this runnable will call connect
     * every {@link #INTERVAL} (100ms). This runnable will self-terminate
     * after {@link #RUNTIME_MAX} has been reached (30 seconds) to avoid
     * accidentally leaking the thread.
     */
    // Only supported in API 19
    @SuppressWarnings("TryWithIdenticalCatches")
    private static class WatchdogRefresher implements Runnable {

        /**
         * Delay between each refresh in millis
         */
        private static final int INTERVAL = 100;

        private final WeakReference<Handler> mHandler;
        private final WeakReference<TagTechnology> mTagTech;
        private final int mMaxRuntime;
        private int mCurrentRuntime;

        private WatchdogRefresher(@NonNull Handler handler, @NonNull TagTechnology tagTech, int maxRuntime) {
            mHandler = new WeakReference<Handler>(handler);
            mTagTech = new WeakReference<TagTechnology>(tagTech);
            mCurrentRuntime = 0;
            mMaxRuntime = maxRuntime;
        }

        @Override
        public void run() {
            Tag tag = getTag();
            if (tag != null) {
                try {
                    Method getTagService = Tag.class.getMethod("getTagService");
                    Object tagService = getTagService.invoke(tag);
                    Method getServiceHandle = Tag.class.getMethod("getServiceHandle");
                    Object serviceHandle = getServiceHandle.invoke(tag);
                    Method connect = tagService.getClass().getMethod("connect", int.class, int.class);
                    /**
                     * int result = tag.getTagService().connect(tag.getServiceHandle(), selectedTechnology);
                     */
                    Object result = connect.invoke(tagService, serviceHandle, TAG_TECHNOLOGY);

                    Handler handler = getHandler();
                    if (result != null && result.equals(0) && handler != null && sIsRunning && mCurrentRuntime < RUNTIME_MAX) {
                        handler.postDelayed(this, INTERVAL);
                        mCurrentRuntime += INTERVAL;
                        Log.v(TAG, "Told NFC Watchdog to wait");
                    } else {
                        Log.d(TAG, "result: " + result);
                    }
                } catch (InvocationTargetException e) {
                    Log.d(TAG, "WatchdogRefresher.run()", e);
                } catch (NoSuchMethodException e) {
                    Log.d(TAG, "WatchdogRefresher.run()", e);
                } catch (IllegalAccessException e) {
                    Log.d(TAG, "WatchdogRefresher.run()", e);
                }
            }
        }

        @Nullable
        private Handler getHandler() {
            return mHandler.get();
        }

        @Nullable
        private Tag getTag() {
            TagTechnology tagTech = mTagTech.get();
            if (tagTech != null) {
                return tagTech.getTag();
            }
            return null;
        }
    }

}