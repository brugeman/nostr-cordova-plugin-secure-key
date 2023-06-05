package com.nostr.band;

// Helper function for storing keys to internal storage.

import static com.nostr.band.Constants.SKS_FILENAME;
import static com.nostr.band.Constants.TAG;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public final class KeyStorage {

  public KeyStorage() {
  }

  public static void writeValues(Context context, String keyAlias, byte[] vals) {
    try {
      FileOutputStream fos = context.openFileOutput(SKS_FILENAME + keyAlias, Context.MODE_PRIVATE);
      fos.write(vals);
      fos.close();
    } catch (Exception e) {
      Log.e(TAG, "Exception: " + e.getMessage());
    }
  }

  public static byte[] readValues(Context context, String keyAlias) {
    try {
      FileInputStream fis = context.openFileInput(SKS_FILENAME + keyAlias);
      byte[] buffer = new byte[8192];
      int bytesRead;
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      while ((bytesRead = fis.read(buffer)) != -1) {
        bos.write(buffer, 0, bytesRead);
      }
      byte[] cipherText = bos.toByteArray();
      fis.close();
      return cipherText;
    } catch (Exception e) {
      Log.e(TAG, "Exception: " + e.getMessage());
      return new byte[0];
    }
  }

}