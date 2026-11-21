"""Home Assistant controls for Flick Panel tablets."""

from homeassistant.config_entries import ConfigEntry
from homeassistant.const import Platform
from homeassistant.core import HomeAssistant, callback
from homeassistant.exceptions import ConfigEntryNotReady
from homeassistant.helpers import device_registry as dr, entity_registry as er
from homeassistant.helpers.event import async_track_entity_registry_updated_event
from homeassistant.helpers.helper_integration import async_remove_helper_devices
from homeassistant.helpers.typing import ConfigType

from .panel import CONF_LEGACY_DEVICE_ID, CONF_PANEL, DOMAIN, Panel, linked_panels
from .services import register_actions

PLATFORMS = (Platform.SWITCH,)
type PanelEntry = ConfigEntry[Panel]


async def async_setup(hass: HomeAssistant, config: ConfigType) -> bool:
    """Register Home Assistant actions for linked tablets."""
    register_actions(hass)
    return True


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
    """Preserve the display switch and attach it to the tablet's device."""
    if entry.version != 1:
        return False
    if entry.minor_version < 2:
        registry = er.async_get(hass)
        unique_id = f"{entry.data[CONF_PANEL]}_keep_awake"
        if entity_id := registry.async_get_entity_id("switch", DOMAIN, unique_id):
            registry.async_remove(entity_id)
    if entry.minor_version < 3:
        devices = dr.async_get(hass)
        tablet_id = entry.data[CONF_PANEL]
        old_device = devices.async_get_device_by_identifier((DOMAIN, tablet_id), entry.entry_id)
        data = dict(entry.data)
        if old_device is not None:
            data[CONF_LEGACY_DEVICE_ID] = old_device.id
        async_remove_helper_devices(
            hass, helper_config_entry_id=entry.entry_id, source_device_id=tablet_id,
        )
        hass.config_entries.async_update_entry(entry, data=data, minor_version=3)
    return True
