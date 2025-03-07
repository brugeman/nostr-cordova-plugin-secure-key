package com.nostr.band;

import static com.nostr.band.KeyStorage.readValues;
import static com.nostr.band.KeyStorage.writeValues;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;
import android.security.KeyPairGeneratorSpec;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.spongycastle.util.encoders.Hex;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.security.auth.x500.X500Principal;

import kotlin.Triple;

public class nostr extends CordovaPlugin {

  private static final String DEFAULT_VAL = "NOSTR_PK";
  private static final String KEYSTORE_PROVIDER_1 = "AndroidKeyStore";
  private static final String KEYSTORE_PROVIDER_2 = "AndroidKeyStoreBCWorkaround";
  private static final String KEYSTORE_PROVIDER_3 = "AndroidOpenSSL";
  private static final String RSA_ALGORITHM = "RSA/ECB/PKCS1Padding";
  private static final String TAG = "NostrLogTag";

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

    if (action.equals("getPublicKey")) {

      String privateKey = getPrivateKey(DEFAULT_VAL);

      Log.i(TAG, "privateKey " + privateKey);

      if ("".equals(privateKey)) {

        prompt("Please enter your private key", "Private key", Collections.singletonList("save"), "nsec...", callbackContext);

        return true;
      }

      String publicKey = new String(generatePublicKey(privateKey), StandardCharsets.UTF_8);
      Log.i(TAG, "publicKey " + publicKey);

      callbackContext.success(initResponseJSONObject(publicKey));

      return true;
    } else if (action.equals("signEvent")) {

      String privateKey = getPrivateKey(DEFAULT_VAL);
      byte[] publicKey = Utils.pubkeyCreate(getBytePrivateKey(privateKey));
      JSONObject jsonObject = args.getJSONObject(0);
      int kind = jsonObject.getInt("kind");
      String content = jsonObject.getString("content");
      List<List<String>> tags = parseTags(jsonObject.getJSONArray("tags"));
      long createdAt = jsonObject.getLong("created_at");
      byte[] bytes = Utils.generateId(publicKey, createdAt, kind, tags, content);

      byte[] sign = Utils.sign(bytes, getBytePrivateKey(privateKey));
      String id = new String(Hex.encode(bytes), StandardCharsets.UTF_8);
      String signString = new String(Hex.encode(sign), StandardCharsets.UTF_8);
      String publicKeyString = new String(generatePublicKey(privateKey), StandardCharsets.UTF_8);

      jsonObject.put("id", id);
      jsonObject.put("pubkey", publicKeyString);
      jsonObject.put("sig", signString);

      callbackContext.success(jsonObject);
    }
    return false;
  }

  private List<List<String>> parseTags(JSONArray jsonArray) throws JSONException {
    List<List<String>> allTags = new ArrayList<>();
    for (int i = 0; i < jsonArray.length(); i++) {
      ArrayList<String> tags = new ArrayList<>();
      JSONArray tagsJsonArray = jsonArray.getJSONArray(i);
      for (int j = 0; j < tagsJsonArray.length(); j++) {
        tags.add(tagsJsonArray.getString(j));
      }
      allTags.add(tags);
    }
    return allTags;
  }

  private void savePrivateKey(String alias, String input) {

    try {

      KeyStore keyStore = KeyStore.getInstance(getKeyStore());
      keyStore.load(null);

      if (!keyStore.containsAlias(alias)) {
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 1);
        KeyPairGeneratorSpec spec = new KeyPairGeneratorSpec.Builder(getContext()).setAlias(alias)
                .setSubject(new X500Principal("CN=" + alias)).setSerialNumber(BigInteger.ONE)
                .setStartDate(start.getTime()).setEndDate(end.getTime()).build();

        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", getKeyStore());
        generator.initialize(spec);

        KeyPair keyPair = generator.generateKeyPair();

        Log.i(TAG, "created new key pairs");
      }

      PublicKey publicKey = keyStore.getCertificate(alias).getPublicKey();

      if (input.isEmpty()) {
        Log.d(TAG, "Exception: input text is empty");
        return;
      }

      Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, publicKey);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
      cipherOutputStream.write(input.getBytes(StandardCharsets.UTF_8));
      cipherOutputStream.close();
      byte[] vals = outputStream.toByteArray();

      writeValues(getContext(), alias, vals);
      Log.i(TAG, "key created and stored successfully");

    } catch (Exception e) {
      Log.e(TAG, "Exception: " + e.getMessage());
    }

  }

  private String getPrivateKey(String alias) {
    try {
      KeyStore keyStore = KeyStore.getInstance(getKeyStore());
      keyStore.load(null);
      PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias, null);

      Cipher output = Cipher.getInstance(RSA_ALGORITHM);
      output.init(Cipher.DECRYPT_MODE, privateKey);
      CipherInputStream cipherInputStream = new CipherInputStream(new ByteArrayInputStream(readValues(getContext(), alias)), output);

      ArrayList<Byte> values = new ArrayList<>();
      int nextByte;
      while ((nextByte = cipherInputStream.read()) != -1) {
        values.add((byte) nextByte);
      }
      byte[] bytes = new byte[values.size()];
      for (int i = 0; i < bytes.length; i++) {
        bytes[i] = values.get(i);
      }

      return new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);

    } catch (Exception e) {
      Log.e(TAG, "Exception: " + e.getMessage());
      return "";
    }
  }

  private Context getContext() {
    return cordova.getActivity().getApplicationContext();
  }

  private String getKeyStore() {
    try {
      KeyStore.getInstance(KEYSTORE_PROVIDER_1);
      return KEYSTORE_PROVIDER_1;
    } catch (Exception err) {
      try {
        KeyStore.getInstance(KEYSTORE_PROVIDER_2);
        return KEYSTORE_PROVIDER_2;
      } catch (Exception e) {
        return KEYSTORE_PROVIDER_3;
      }
    }
  }

  private synchronized void prompt(String message, String title, List<String> buttonLabels, String defaultText, final CallbackContext callbackContext) {

    Runnable runnable = () -> {
      final EditText promptInput = initInput(defaultText);
      AlertDialog.Builder alertDialog = initAlertDialog(promptInput, message, title);

      setNegativeButton(alertDialog, buttonLabels.get(0), promptInput, callbackContext);
      setOnCancelListener(alertDialog, callbackContext);
      changeTextDirection(alertDialog);
    };

    this.cordova.getActivity().runOnUiThread(runnable);
  }

  private EditText initInput(String defaultText) {
    final EditText promptInput = new EditText(cordova.getActivity());

    Resources resources = cordova.getActivity().getResources();
    int promptInputTextColor = resources.getColor(android.R.color.primary_text_light);
    promptInput.setTextColor(promptInputTextColor);
    promptInput.setText(defaultText);

    return promptInput;
  }

  private AlertDialog.Builder initAlertDialog(EditText input, String message, String title) {
    AlertDialog.Builder alertDialog = createDialog(cordova);
    alertDialog.setMessage(message);
    alertDialog.setTitle(title);
    alertDialog.setCancelable(true);
    alertDialog.setView(input);

    return alertDialog;
  }

  private void setNegativeButton(AlertDialog.Builder alertDialog, String buttonLabel, EditText promptInput, CallbackContext callbackContext) {
    alertDialog.setNegativeButton(buttonLabel,
            (dialog, which) -> {
              dialog.dismiss();
              if (promptInput.getText() != null && !promptInput.getText().toString().trim().isEmpty()) {
                String privateKey = promptInput.getText().toString();
                savePrivateKey(DEFAULT_VAL, privateKey);
                String publicKey = new String(generatePublicKey(privateKey), StandardCharsets.UTF_8);
                JSONObject result = initResponseJSONObject(publicKey);
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
              } else {
                callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR));
              }
            });
  }

  private void setOnCancelListener(AlertDialog.Builder alertDialog, CallbackContext callbackContext) {
    alertDialog.setOnCancelListener(dialog -> {
      dialog.dismiss();
      JSONObject result = initResponseJSONObject("");
      callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.OK, result));
    });
  }

  private JSONObject initResponseJSONObject(String response) {
    final JSONObject result = new JSONObject();
    try {
      result.put("pubKey", response);
    } catch (JSONException e) {
      Log.i("response", response);
      Log.e("JSONException", e.getMessage());
    }

    return result;
  }

  @SuppressLint("NewApi")
  private AlertDialog.Builder createDialog(CordovaInterface cordova) {
    int currentApiVersion = android.os.Build.VERSION.SDK_INT;
    if (currentApiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB) {
      return new AlertDialog.Builder(cordova.getActivity(), AlertDialog.THEME_DEVICE_DEFAULT_LIGHT);
    } else {
      return new AlertDialog.Builder(cordova.getActivity());
    }
  }

  @SuppressLint("NewApi")
  private void changeTextDirection(AlertDialog.Builder dlg) {
    int currentApiVersion = android.os.Build.VERSION.SDK_INT;
    dlg.create();
    AlertDialog dialog = dlg.show();
    if (currentApiVersion >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
      TextView messageView = dialog.findViewById(android.R.id.message);
      messageView.setTextDirection(android.view.View.TEXT_DIRECTION_LOCALE);
    }
  }

  private byte[] generatePublicKey(String privateKey) {
    byte[] bytes = Utils.pubkeyCreate(getBytePrivateKey(privateKey));
    return Hex.encode(bytes);
  }

  private byte[] getBytePrivateKey(String privateKey) {
    Triple<String, byte[], Bech32.Encoding> stringEncodingTriple = Bech32.decodeBytes(privateKey, false);
    return stringEncodingTriple.getSecond();
  }
}
