package com.spendtracker.pro;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.core.content.FileProvider;
import java.io.*;
import java.security.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * P4: Backup and restore manager.
 *
 * Backup: copies the Room SQLite database file into a ZIP archive encrypted
 * with AES-256-GCM. The ZIP is placed in external files dir and shared via
 * FileProvider.
 *
 * Restore: user picks a .stpbak file, it is decrypted, and the DB file is
 * replaced. The app must be restarted for Room to pick up the new file.
 *
 * Security notes:
 *  - AES/GCM/NoPadding — authenticated encryption, detects file tampering.
 *  - IV (12 bytes) prepended to the ciphertext in the ZIP entry.
 *  - Key derived with PBKDF2WithHmacSHA256 (100,000 iterations) from
 *    ANDROID_ID + a 16-byte random salt stored in private SharedPreferences.
 *    The salt is generated once and persisted; the same salt is reused for
 *    all future backups so that older backups can always be restored on the
 *    same device.  Upgrading from the old SHA-256 derivation is handled
 *    transparently: if no salt is stored yet, a new one is generated and
 *    existing backups created with the old scheme become unrestorable (they
 *    were already device-bound and not portable, so this is an acceptable
 *    one-time migration cost).
 */
public class BackupManager {

    private static final String BACKUP_DIR  = "SpendTracker";
    // Must match the name passed to Room.databaseBuilder() in AppDatabase.
    private static final String DB_NAME     = "spendtracker.db";
    private static final String ENTRY_NAME  = "spendtracker.db";
    private static final String EXT         = ".stpbak";
    private static final String ALGO        = "AES/GCM/NoPadding";
    private static final int    IV_LEN      = 12;
    private static final int    TAG_LEN     = 128; // GCM auth-tag bits

    // PBKDF2 parameters
    private static final int    KDF_ITERATIONS = 100_000;
    private static final int    KEY_LEN_BITS   = 256;
    private static final int    SALT_LEN       = 16;
    private static final String KDF_ALGO       = "PBKDF2WithHmacSHA256";
    private static final String PREFS_NAME     = "stp_backup_prefs";
    private static final String PREF_SALT_KEY  = "backup_kdf_salt";

    public interface Callback {
        void onSuccess(String message);
        void onError(String message);
    }

    // ── Backup ────────────────────────────────────────────────────

    public static void backup(Context ctx, Callback cb) {
        AppExecutors.io().execute(() -> {
            try {
                File dbFile = ctx.getDatabasePath(DB_NAME);
                if (!dbFile.exists()) { cb.onError("No data to backup yet."); return; }

                // Force Room WAL checkpoint so all data is in the main DB file.
                AppDatabase.getInstance(ctx).getOpenHelper().getWritableDatabase()
                        .execSQL("PRAGMA wal_checkpoint(FULL)");

                // Output file
                File outDir = new File(ctx.getExternalFilesDir(null), BACKUP_DIR);
                outDir.mkdirs();
                String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        .format(new Date());
                File outFile = new File(outDir, "stp_backup_" + ts + EXT);

                // Encrypt + zip
                SecretKey key = deriveKey(ctx);
                byte[] iv = new byte[IV_LEN];
                new SecureRandom().nextBytes(iv);

                Cipher cipher = Cipher.getInstance(ALGO);
                cipher.init(Cipher.ENCRYPT_MODE, key,
                        new GCMParameterSpec(TAG_LEN, iv));

                try (FileInputStream fis = new FileInputStream(dbFile);
                     FileOutputStream fos = new FileOutputStream(outFile);
                     ZipOutputStream zos = new ZipOutputStream(fos)) {

                    zos.putNextEntry(new ZipEntry(ENTRY_NAME));
                    zos.write(iv); // prepend IV

                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = fis.read(buf)) != -1) {
                        byte[] encrypted = cipher.update(buf, 0, n);
                        if (encrypted != null) zos.write(encrypted);
                    }
                    byte[] finalBytes = cipher.doFinal();
                    if (finalBytes != null) zos.write(finalBytes);
                    zos.closeEntry();
                }

                // Share via FileProvider
                Uri uri = FileProvider.getUriForFile(ctx,
                        ctx.getPackageName() + ".fileprovider", outFile);

                cb.onSuccess("backup_uri:" + uri.toString() + "|" + outFile.getName());

            } catch (Exception e) {
                cb.onError("Backup failed: " + e.getMessage());
            }
        });
    }

    // ── Restore ───────────────────────────────────────────────────

    public static void restore(Context ctx, Uri backupUri, Callback cb) {
        AppExecutors.io().execute(() -> {
            try {
                File dbFile = ctx.getDatabasePath(DB_NAME);

                // Close Room DB before replacing the file.
                AppDatabase.getInstance(ctx).close();

                SecretKey key = deriveKey(ctx);

                try (InputStream is = ctx.getContentResolver().openInputStream(backupUri);
                     ZipInputStream zis = new ZipInputStream(is)) {

                    ZipEntry entry;
                    boolean found = false;
                    while ((entry = zis.getNextEntry()) != null) {
                        if (!ENTRY_NAME.equals(entry.getName())) continue;
                        found = true;

                        // Read IV
                        byte[] iv = new byte[IV_LEN];
                        int read = 0;
                        while (read < IV_LEN) {
                            int r = zis.read(iv, read, IV_LEN - read);
                            if (r == -1) break;
                            read += r;
                        }

                        Cipher cipher = Cipher.getInstance(ALGO);
                        cipher.init(Cipher.DECRYPT_MODE, key,
                                new GCMParameterSpec(TAG_LEN, iv));

                        // Read all remaining ciphertext
                        ByteArrayOutputStream cipherBuf = new ByteArrayOutputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = zis.read(buf)) != -1) cipherBuf.write(buf, 0, n);

                        byte[] plaintext = cipher.doFinal(cipherBuf.toByteArray());

                        // Write decrypted DB, replacing the old file.
                        dbFile.getParentFile().mkdirs();
                        try (FileOutputStream fos = new FileOutputStream(dbFile)) {
                            fos.write(plaintext);
                        }
                        break;
                    }

                    if (!found) { cb.onError("Invalid backup file."); return; }
                }

                cb.onSuccess("Restore successful. Please restart the app.");

            } catch (BadPaddingException e) {
                cb.onError("Backup file is corrupted or from a different device.");
            } catch (Exception e) {
                cb.onError("Restore failed: " + e.getMessage());
            }
        });
    }

    // ── Key derivation ────────────────────────────────────────────

    /**
     * Derives a 256-bit AES key using PBKDF2WithHmacSHA256.
     *
     * Password material: ANDROID_ID (semi-stable device identifier).
     * Salt: 16 random bytes generated on first call and persisted in private
     *       SharedPreferences so that the same key is reproduced on every
     *       subsequent call on the same device.
     * Iterations: 100,000 — keeps brute-force cost high while staying under
     *             ~200 ms on a mid-range device.
     */
    private static SecretKey deriveKey(Context ctx) throws Exception {
        String androidId = android.provider.Settings.Secure.getString(
                ctx.getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);
        char[] password = (androidId != null ? androidId : "default").toCharArray();

        byte[] salt = getOrCreateSalt(ctx);

        SecretKeyFactory factory = SecretKeyFactory.getInstance(KDF_ALGO);
        PBEKeySpec spec = new PBEKeySpec(password, salt, KDF_ITERATIONS, KEY_LEN_BITS);
        try {
            byte[] keyBytes = factory.generateSecret(spec).getEncoded();
            return new SecretKeySpec(keyBytes, "AES");
        } finally {
            spec.clearPassword();
        }
    }

    /** Returns the stored salt, creating and persisting a fresh one if absent. */
    private static byte[] getOrCreateSalt(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encoded = prefs.getString(PREF_SALT_KEY, null);
        if (encoded != null) {
            return Base64.decode(encoded, Base64.NO_WRAP);
        }
        // First run — generate and persist a new salt.
        byte[] salt = new byte[SALT_LEN];
        new SecureRandom().nextBytes(salt);
        prefs.edit()
             .putString(PREF_SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
             .apply();
        return salt;
    }
}
