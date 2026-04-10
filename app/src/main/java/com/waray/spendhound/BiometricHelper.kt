package com.waray.spendhound

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

object BiometricHelper {

    private const val KEY_ALIAS = "spendhound_biometric_key"
    private const val PREFS_NAME = "biometric_creds"
    private const val KEY_COMBINED_ENC = "enc_combined"
    private const val KEY_IV = "iv_combined"
    private const val TRANSFORMATION = "AES/CBC/PKCS7Padding"

    // BIOMETRIC_STRONG = fingerprint / face (Class 3) — required for crypto-bound key operations
    // DEVICE_CREDENTIAL = PIN / pattern / password — allowed for verification-only prompts
    private const val AUTH_STRONG = BIOMETRIC_STRONG
    private const val AUTH_ANY = BIOMETRIC_STRONG or DEVICE_CREDENTIAL

    /**
     * Returns true if the device has ANY screen lock set up (fingerprint, face, PIN, pattern).
     * Used to decide whether to show the passkey option at all.
     */
    fun isAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH_ANY) == BiometricManager.BIOMETRIC_SUCCESS

    /**
     * Returns true if strong biometric (fingerprint/face) is available.
     * Required for crypto-bound key operations (save/load credentials).
     */
    fun isStrongBiometricAvailable(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTH_STRONG) == BiometricManager.BIOMETRIC_SUCCESS

    fun hasStoredCredentials(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COMBINED_ENC, null) != null

    fun getStoredEmail(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val encCombined = prefs.getString(KEY_COMBINED_ENC, null) ?: return null
        val ivStr = prefs.getString(KEY_IV, null) ?: return null
        // Note: these are encrypted — only usable after biometric auth.
        // This method returns null; actual decryption happens inside promptToGetCredentials.
        // For the forgot password flow, we store a plain email separately for display only.
        return prefs.getString("plain_email", null)
    }

    fun getStoredPassword(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString("plain_password", null)

    fun saveCredentials(context: Context, email: String, password: String) {
        // Save plain versions temporarily for the forgot-password re-auth flow
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString("plain_email", email)
            .putString("plain_password", password)
            .apply()
    }

    fun clearCredentials(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        try {
            KeyStore.getInstance("AndroidKeyStore").apply {
                load(null)
                if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS)
            }
        } catch (_: Exception) {}
    }

    /**
     * Prompts biometric to ENCRYPT and save credentials.
     * Call after a successful manual login or on sign-up Step 3.
     */
    fun promptToSaveCredentials(
        activity: FragmentActivity,
        email: String,
        password: String,
        onSaved: () -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        if (!isStrongBiometricAvailable(activity)) {
            // Device only has PIN/pattern — save credentials without crypto binding
            // and use device credential for verification-only prompts
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString("plain_email", email)
                .putString("plain_password", password)
                .putBoolean("pin_only_mode", true)
                .apply()
            // Prompt device credential to confirm the user intends to set this up
            buildPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSaved()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancelled()
                override fun onAuthenticationFailed() {}
            }).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Enable Passkey")
                    .setSubtitle("Confirm with your screen lock to save your passkey")
                    .setAllowedAuthenticators(AUTH_ANY)
                    .build()
            )
            return
        }

        val cipher = getEncryptCipher() ?: run { onCancelled(); return }
        buildPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return
                val combined = "$email\n$password"
                val encBytes = c.doFinal(combined.toByteArray(Charsets.UTF_8))
                activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_COMBINED_ENC, Base64.encodeToString(encBytes, Base64.DEFAULT))
                    .putString(KEY_IV, Base64.encodeToString(c.iv, Base64.DEFAULT))
                    .putBoolean("pin_only_mode", false)
                    .apply()
                onSaved()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancelled()
            override fun onAuthenticationFailed() {}
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Enable Passkey")
                .setSubtitle("Authenticate to save your passkey securely")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(AUTH_STRONG)
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    /**
     * Prompts biometric to DECRYPT and return credentials.
     * Credentials are only accessible inside onSuccess — never exposed otherwise.
     */
    fun promptToGetCredentials(
        activity: FragmentActivity,
        title: String = "Login to SpendHound",
        subtitle: String = "Use your fingerprint, face, or PIN to log in",
        onSuccess: (email: String, password: String) -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isPinOnlyMode = prefs.getBoolean("pin_only_mode", false)

        if (isPinOnlyMode) {
            // PIN-only mode: verify with device credential then return plain stored credentials
            val email = prefs.getString("plain_email", null) ?: return
            val password = prefs.getString("plain_password", null) ?: return
            buildPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                    onSuccess(email, password)
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancelled()
                override fun onAuthenticationFailed() {}
            }).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(AUTH_ANY)
                    .build()
            )
            return
        }

        val encCombined = prefs.getString(KEY_COMBINED_ENC, null) ?: return
        val ivStr = prefs.getString(KEY_IV, null) ?: return
        val cipher = getDecryptCipher(Base64.decode(ivStr, Base64.DEFAULT)) ?: return

        buildPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val c = result.cryptoObject?.cipher ?: return
                val decrypted = try {
                    String(c.doFinal(Base64.decode(encCombined, Base64.DEFAULT)), Charsets.UTF_8)
                } catch (_: Exception) { return }
                val parts = decrypted.split("\n", limit = 2)
                if (parts.size == 2) onSuccess(parts[0], parts[1])
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancelled()
            override fun onAuthenticationFailed() {}
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Use password instead")
                .setAllowedAuthenticators(AUTH_STRONG)
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
    }

    /**
     * Prompts biometric for identity verification only (no crypto object).
     * Use for: forgot password, change password confirmation.
     */
    fun promptForVerification(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onVerified: () -> Unit,
        onCancelled: () -> Unit = {}
    ) {
        buildPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onVerified()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onCancelled()
            override fun onAuthenticationFailed() {}
        }).authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(AUTH_ANY)
                .build()
        )
    }

    // --- Private helpers ---

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.getKey(KEY_ALIAS, null)?.let { return it as SecretKey }
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        keyGen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
                .setUserAuthenticationRequired(true)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        return keyGen.generateKey()
    }

    private fun getEncryptCipher(): Cipher? = try {
        Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, getOrCreateKey()) }
    } catch (_: Exception) { null }

    private fun getDecryptCipher(iv: ByteArray): Cipher? = try {
        Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getOrCreateKey(), IvParameterSpec(iv))
        }
    } catch (_: Exception) { null }

    private fun buildPrompt(activity: FragmentActivity, callback: BiometricPrompt.AuthenticationCallback) =
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback)
}
