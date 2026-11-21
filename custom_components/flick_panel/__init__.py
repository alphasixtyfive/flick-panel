"""Home Assistant controls for Flick Panel tablets."""

import probatio as vol

from homeassistant.config_entries import ConfigEntry, ConfigEntryState
from homeassistant.const import ATTR_DEVICE_ID, Platform
from homeassistant.core import HomeAssistant, ServiceCall, callback
from homeassistant.exceptions import ConfigEntryNotReady, ServiceValidationError
from homeassistant.helpers import config_validation as cv
from homeassistant.helpers import device_registry as dr, entity_registry as er
from homeassistant.helpers.event import async_track_entity_registry_updated_event
from homeassistant.helpers.service import async_register_admin_service
from homeassistant.helpers.typing import ConfigType

from .panel import CONF_PANEL, DOMAIN, Panel, linked_panels

PLATFORMS = (Platform.SWITCH,)
CAMERA = "camera_entity_id"
SCREENSAVER_COMMANDS = {"start_screensaver": "show_clock", "stop_screensaver": "wake"}
DEVICE_FIELDS = {vol.Required(ATTR_DEVICE_ID): cv.string}
type PanelEntry = ConfigEntry[Panel]


async def async_setup(hass: HomeAssistant, config: ConfigType) -> bool:
    """Register actions for the screensaver and camera viewer."""
    async def open_camera(call: ServiceCall) -> None:
        camera = call.data[CAMERA]
        if not camera.startswith("camera.") or hass.states.get(camera) is None:
            raise ServiceValidationError("Choose a Home Assistant camera")
        await _target_panel(hass, call.data[ATTR_DEVICE_ID]).async_command("open_camera", camera=camera)

    async def screensaver(call: ServiceCall) -> None:
        await _target_panel(hass, call.data[ATTR_DEVICE_ID]).async_command(SCREENSAVER_COMMANDS[call.service])

    async_register_admin_service(
        hass, DOMAIN, "open_camera", open_camera, schema=vol.Schema(DEVICE_FIELDS | {vol.Required(CAMERA): cv.entity_id}),
    )
    device_schema = vol.Schema(DEVICE_FIELDS)
    for action in SCREENSAVER_COMMANDS:
        async_register_admin_service(hass, DOMAIN, action, screensaver, schema=device_schema)
    return True


def _target_panel(hass: HomeAssistant, device_id: str) -> Panel:
    """Resolve a configured tablet selected by a Home Assistant action."""
    if device := dr.async_get(hass).async_get(device_id):
        for entry in hass.config_entries.async_entries(DOMAIN):
            if entry.entry_id in device.config_entries and entry.state is ConfigEntryState.LOADED:
                return entry.runtime_data
    raise ServiceValidationError("Choose a tablet linked to Flick Panel")


async def async_setup_entry(hass: HomeAssistant, entry: PanelEntry) -> bool:
    """Attach panel controls to the tablet's existing device."""
    if not (panel := linked_panels(hass).get(entry.data[CONF_PANEL])):
        raise ConfigEntryNotReady("Link the tablet in Flick Panel and enable its Display mode sensor")
    entry.runtime_data = panel

    @callback
    def source_changed(event) -> None:
        if event.data.get("old_entity_id") == panel.display_entity_id:
            hass.config_entries.async_schedule_reload(entry.entry_id)

    entry.async_on_unload(async_track_entity_registry_updated_event(
        hass, panel.display_entity_id, source_changed,
    ))
    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: PanelEntry) -> bool:
    """Unload controls while leaving the mobile registration in place."""
    return await hass.config_entries.async_unload_platforms(entry, PLATFORMS)


async def async_migrate_entry(hass: HomeAssistant, entry: PanelEntry) -> bool:
    """Remove the Keep awake entity from the initial pilot."""
    if entry.version != 1:
        return False
    if entry.minor_version < 2:
        registry = er.async_get(hass)
        unique_id = f"{entry.data[CONF_PANEL]}_keep_awake"
        if entity_id := registry.async_get_entity_id("switch", DOMAIN, unique_id):
            registry.async_remove(entity_id)
        hass.config_entries.async_update_entry(entry, minor_version=2)
    return True
