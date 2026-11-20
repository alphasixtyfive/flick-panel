package app.flickpanel.config

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.flickpanel.ha.HaCredentials
import app.flickpanel.ha.parseServer
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec

private const val LAYOUT = "layout"
private const val SERVER = "server"
private const val TOKEN_IV = "token_iv"
private const val TOKEN_DATA = "token_data"
private const val PIN_SALT = "pin_salt"
private const val PIN_HASH = "pin_hash"
private const val PIN_FAILURES = "pin_failures"
private const val PIN_WAIT = "pin_wait"
private const val TOKEN_CIPHER = "AES/GCM/NoPadding"

internal data class SavedPanel(val configured: Boolean, val config: PanelConfig, val credentials: HaCredentials?)

internal class PanelStore(context: Context) {
    private val prefs = context.getSharedPreferences("panel", Context.MODE_PRIVATE)

    @Synchronized
    fun read(): SavedPanel {
        val config = prefs.getString(LAYOUT, null)?.let(PanelConfigCodec::decode) ?: PanelConfig()
        return SavedPanel(prefs.contains(PIN_HASH), config, readCredentials())
    }

    private fun readCredentials(): HaCredentials? {
        val url = prefs.getString(SERVER, null) ?: return null
        return runCatching {
            val iv = prefs.getString(TOKEN_IV, null)?.let(::decode) ?: return null
            val data = prefs.getString(TOKEN_DATA, null)?.let(::decode) ?: return null
            val cipher = Cipher.getInstance(TOKEN_CIPHER)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            cipher.updateAAD(url.toByteArray())
            HaCredentials(parseServer(url), cipher.doFinal(data).toString(Charsets.UTF_8))
        }.getOrNull()
    }

    @Synchronized
    fun connect(credentials: HaCredentials, config: PanelConfig, newPin: String? = null) {
        val url = credentials.server.toString()
        val cipher = Cipher.getInstance(TOKEN_CIPHER)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        cipher.updateAAD(url.toByteArray())
        val sealed = cipher.doFinal(credentials.token.toByteArray())
        val edit = prefs.edit()
            .putString(SERVER, url)
            .putString(TOKEN_IV, encode(cipher.iv))
            .putString(TOKEN_DATA, encode(sealed))
            .putString(LAYOUT, PanelConfigCodec.encode(config))
        if (newPin != null) {
            require(validPin(newPin)) { "Choose a PIN with 4 to 8 digits" }
            val salt = ByteArray(16).also(SecureRandom()::nextBytes)
            edit.putString(PIN_SALT, encode(salt))
                .putString(PIN_HASH, encode(derive(newPin, salt)))
                .remove(PIN_FAILURES)
                .remove(PIN_WAIT)
        }
        check(edit.commit()) { "Couldn't save the connection" }
    }

    @Synchronized
    fun save(config: PanelConfig) {
        check(prefs.edit().putString(LAYOUT, PanelConfigCodec.encode(config)).commit()) { "Couldn't save the layout" }
    }

    @Synchronized
    fun verify(pin: String): Boolean {
        val now = System.currentTimeMillis()
        check(prefs.getLong(PIN_WAIT, 0) <= now) { "Too many attempts. Try again in a minute." }
        val salt = prefs.getString(PIN_SALT, null) ?: return false
        val hash = prefs.getString(PIN_HASH, null) ?: return false
        val correct = validPin(pin) && MessageDigest.isEqual(derive(pin, decode(salt)), decode(hash))
        val failures = if (correct) 0 else prefs.getInt(PIN_FAILURES, 0) + 1
        val delay = failures >= 5
        check(prefs.edit().putInt(PIN_FAILURES, if (delay) 0 else failures)
            .putLong(PIN_WAIT, if (delay) now + 60_000 else 0).commit()) { "Couldn't verify the PIN" }
        return correct
    }

    private fun key(): SecretKey {
        val name = "flick_panel_connection"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(name, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(name, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    private fun derive(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, 150_000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(value: String) = Base64.decode(value, Base64.NO_WRAP)
}

internal fun validPin(pin: String): Boolean = pin.length in 4..8 && pin.all { it in '0'..'9' }
