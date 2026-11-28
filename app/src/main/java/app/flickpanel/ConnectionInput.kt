package app.flickpanel

import app.flickpanel.config.validPin
import app.flickpanel.ha.HaProblem
import app.flickpanel.ha.parseServer
import okhttp3.HttpUrl

internal enum class ConnectionField { ADDRESS, TOKEN, PIN, CONFIRMATION }

internal data class ConnectionInputValidation(val server: HttpUrl?, val errors: Map<ConnectionField, String>)

internal fun validateConnectionInput(
    address: String, token: String, pin: String, confirmation: String, needsPin: Boolean,
): ConnectionInputValidation {
    val errors = linkedMapOf<ConnectionField, String>()
    val server = try {
        parseServer(address)
    } catch (error: HaProblem) {
        errors[ConnectionField.ADDRESS] = requireNotNull(error.message)
        null
    } catch (error: IllegalArgumentException) {
        errors[ConnectionField.ADDRESS] = requireNotNull(error.message)
        null
    }
    val secret = token.trim()
    if (secret.length !in 16..4096 || secret.any(Char::isWhitespace)) {
        errors[ConnectionField.TOKEN] = "Paste a Home Assistant long-lived access token"
    }
    if (needsPin) {
        if (!validPin(pin)) errors[ConnectionField.PIN] = "Enter a 4 to 8 digit PIN"
        if (confirmation.isEmpty() || pin != confirmation) {
            errors[ConnectionField.CONFIRMATION] =
                if (confirmation.isEmpty()) "Enter the PIN again" else "The PINs don't match"
        }
    }
    return ConnectionInputValidation(server, errors)
}
