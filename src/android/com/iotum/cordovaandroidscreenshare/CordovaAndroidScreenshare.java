package com.iotum.cordovaandroidscreenshare;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Base64;
import android.view.Display;
import android.view.OrientationEventListener;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaWebView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class CordovaAndroidScreenshare extends CordovaPlugin {
  private static final String TAG = CordovaAndroidScreenshare.class.getName();
  private static final int REQUEST_CODE = 100;
  private static final String SCREENCAP_NAME = "screencap";
  private static final int VIRTUAL_DISPLAY_FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
      | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
  private static MediaProjection sMediaProjection;

  private MediaProjectionManager mProjectionManager;
  private ImageReader mImageReader;
  private Handler mHandler;
  private Display mDisplay;
  private VirtualDisplay mVirtualDisplay;
  private int mDensity;
  private int mWidth;
  private int mHeight;
  private int mRotation;
  private OrientationChangeCallback mOrientationChangeCallback;

  private CallbackContext mCallbackContext;

  @Override
  public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
    mCallbackContext = callbackContext;
    if (action.equals("startProjection")) {
      startProjection();
      return true;
    } else if (action.equals("stopProjection")) {
      stopProjection();
      return true;
    }
    callbackContext.error("action not found");
    return false;
  }

  private class ImageAvailableListener implements ImageReader.OnImageAvailableListener {
    @Override
    public void onImageAvailable(ImageReader reader) {
      Image image = null;
      Bitmap bitmap = null;

      try {
        image = reader.acquireLatestImage();
        if (image != null) {
          Image.Plane[] planes = image.getPlanes();
          ByteBuffer buffer = planes[0].getBuffer();
          int pixelStride = planes[0].getPixelStride();
          int rowStride = planes[0].getRowStride();
          int rowPadding = rowStride - pixelStride * mWidth;

          // create bitmap
          bitmap = Bitmap.createBitmap(mWidth + rowPadding / pixelStride, mHeight, Bitmap.Config.ARGB_8888);
          bitmap.copyPixelsFromBuffer(buffer);

          // convert bitmap to jpeg based64 URI
          ByteArrayOutputStream jpeg_data = new ByteArrayOutputStream();
          if (bitmap.compress(CompressFormat.JPEG, 100, jpeg_data)) {
            byte[] code = jpeg_data.toByteArray();
            byte[] output = Base64.encode(code, Base64.NO_WRAP);
            String js_out = new String(output);
            js_out = "data:image/jpeg;base64," + js_out;
            JSONObject jsonRes = new JSONObject();
            jsonRes.put("URI", js_out);
            PluginResult result = new PluginResult(PluginResult.Status.OK, jsonRes);
            mCallbackContext.sendPluginResult(result);

            js_out = null;
            output = null;
            code = null;
          } else {
            mCallbackContext.error("Unable to convert bitmap");
          }

          jpeg_data = null;

          Log.e(TAG, "captured image");
        }

      } catch (Exception e) {
        e.printStackTrace();
      } finally {
        if (bitmap != null) {
          bitmap.recycle();
        }

        if (image != null) {
          image.close();
        }
      }
    }
  }

  private class OrientationChangeCallback extends OrientationEventListener {

    OrientationChangeCallback(Context context) {
      super(context);
    }

    @Override
    public void onOrientationChanged(int orientation) {
      final int rotation = mDisplay.getRotation();
      if (rotation != mRotation) {
        mRotation = rotation;
        try {
          // clean up
          if (mVirtualDisplay != null)
            mVirtualDisplay.release();
          if (mImageReader != null)
            mImageReader.setOnImageAvailableListener(null, null);

          // re-create virtual display depending on device width / height
          createVirtualDisplay();
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
  }

  private class MediaProjectionStopCallback extends MediaProjection.Callback {
    @Override
    public void onStop() {
      Log.e("ScreenCapture", "stopping projection.");
      mHandler.post(new Runnable() {
        @Override
        public void run() {
          if (mVirtualDisplay != null)
            mVirtualDisplay.release();
          if (mImageReader != null)
            mImageReader.setOnImageAvailableListener(null, null);
          if (mOrientationChangeCallback != null)
            mOrientationChangeCallback.disable();
          sMediaProjection.unregisterCallback(MediaProjectionStopCallback.this);
          mCallbackContext.success("Stopped");
        }
      });
    }
  }

  /******************************************
   * Activity Lifecycle methods
   ************************/

  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);

    // call for the projection manager
    mProjectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);

    // start capture handling thread
    new Thread() {
      @Override
      public void run() {
        Looper.prepare();
        mHandler = new Handler();
        Looper.loop();
      }
    }.start();
  }

  // TODO: Add logic for rejected permissions
  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == REQUEST_CODE) {
      sMediaProjection = mProjectionManager.getMediaProjection(resultCode, data);

      if (sMediaProjection != null) {
        // display metrics
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        mDensity = metrics.densityDpi;
        mDisplay = getWindowManager().getDefaultDisplay();

        // create virtual display depending on device width / height
        createVirtualDisplay();

        // register orientation change callback
        mOrientationChangeCallback = new OrientationChangeCallback(this);
        if (mOrientationChangeCallback.canDetectOrientation()) {
          mOrientationChangeCallback.enable();
        }

        // register media projection stop callback
        sMediaProjection.registerCallback(new MediaProjectionStopCallback(), mHandler);
      }
    }
  }

  @Override
  public void onDestroy() {
    if (sMediaProjection != null) {
      sMediaProjection.stop();
    }
  }

  /******************************************
   * UI Widget Callbacks
   *******************************/
  private void startProjection() {
    cordova.setActivityResultCallback(this);
    cordova.startActivityForResult(this, mProjectionManager.createScreenCaptureIntent(), REQUEST_CODE);
  }

  private void stopProjection() {
    mHandler.post(new Runnable() {
      @Override
      public void run() {
        if (sMediaProjection != null) {
          sMediaProjection.stop();
        }
      }
    });
  }

  /******************************************
   * Factoring Virtual Display creation
   ****************/
  private void createVirtualDisplay() {
    // get width and height
    Point size = new Point();
    mDisplay.getSize(size);
    mWidth = size.x;
    mHeight = size.y;

    // start capture reader
    mImageReader = ImageReader.newInstance(mWidth, mHeight, PixelFormat.RGBA_8888, 2);
    mVirtualDisplay = sMediaProjection.createVirtualDisplay(SCREENCAP_NAME, mWidth, mHeight, mDensity,
        VIRTUAL_DISPLAY_FLAGS, mImageReader.getSurface(), null, mHandler);
    mImageReader.setOnImageAvailableListener(new ImageAvailableListener(), mHandler);
  }
}