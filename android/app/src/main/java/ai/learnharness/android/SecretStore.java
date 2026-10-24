package ai.learnharness.android;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Keys are excluded from learning exports and encrypted with an Android Keystore key. */
public final class SecretStore {
    private final Context context;
    public SecretStore(Context context) { this.context = context; }
    private javax.crypto.SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        String alias = "learnharness-provider";
        if (!store.containsAlias(alias)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return (javax.crypto.SecretKey) store.getKey(alias, null);
    }
    public void set(String provider, String value) throws Exception {
        var prefs = context.getSharedPreferences("secrets", Context.MODE_PRIVATE);
        if (value.trim().isEmpty()) { if (!prefs.edit().remove(provider).commit()) throw new java.io.IOException("Could not remove the API key"); return; }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key());
        String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + ":" + Base64.encodeToString(cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
        if (!prefs.edit().putString(provider, encoded).commit()) throw new java.io.IOException("Could not save the API key");
    }
    public String get(String provider) throws Exception {
        String encoded = context.getSharedPreferences("secrets", Context.MODE_PRIVATE).getString(provider, "");
        if (encoded.isEmpty()) return "";
        String[] pieces = encoded.split(":");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(pieces[0], Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(pieces[1], Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
}
