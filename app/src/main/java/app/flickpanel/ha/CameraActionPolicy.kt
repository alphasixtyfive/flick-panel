package app.flickpanel.ha

import app.flickpanel.config.CameraActionKind
import app.flickpanel.config.cameraActionKind

internal val Entity.cameraActionKind: CameraActionKind?
    get() = cameraActionKind(id)?.takeUnless { it == CameraActionKind.OPEN_GATE && !gateWithOpenControl }

internal val Entity.cameraActionReady: Boolean
    get() = available && when (cameraActionKind) {
        CameraActionKind.OPEN_GATE -> state == "closed"
        CameraActionKind.TOGGLE, CameraActionKind.RUN -> true
        null -> false
    }
