"""Link Panel controls to an existing Flick Panel mobile registration."""

from dataclasses import dataclass
from typing import Any

from homeassistant.components.mobile_app.const import ATTR_APP_ID, DOMAIN as MOBILE_APP
from homeassistant.components.mobile_app.util import get_notify_service
from homeassistant.const import CONF_WEBHOOK_ID
from homeassistant.core import HomeAssistant, callback
from homeassistant.exceptions import HomeAssistantError
from homeassistant.helpers import device_registry as dr, entity_registry as er
from homeassistant.helpers.entity import DeviceInfo

DOMAIN = "flick_panel"
CONF_PANEL = "panel"
APP_ID = "app.flickpanel"


@dataclass(frozen=True)
class Panel:
    """A tablet and the display telemetry it already publishes."""

    hass: HomeAssistant
    device: dr.DeviceEntry
    webhook_id: str
    display_entity_id: str

    @property
    def device_info(self) -> DeviceInfo:
        return DeviceInfo(
            identifiers=self.device.identifiers | {(DOMAIN, self.device.id)},
            name=self.device.name,
        )

    async def async_command(self, command: str, **data: Any) -> None:
        """Send a command to this tablet through its registered notify action."""
        try:
            service = get_notify_service(self.hass, self.webhook_id)
        except KeyError:
            service = None
        if not service or not self.hass.services.has_service("notify", service):
            raise HomeAssistantError("The Flick Panel tablet link is unavailable")
        try:
            await self.hass.services.async_call(
                "notify", service,
                {"message": "command_flick_panel", "data": {"command": command, **data}},
                blocking=True,
            )
        except HomeAssistantError:
            raise HomeAssistantError("Flick Panel is not connected to Home Assistant") from None


@callback
def linked_panels(hass: HomeAssistant) -> dict[str, Panel]:
    """Find Flick Panel devices by registration and exact sensor identity."""
    devices = dr.async_get(hass)
    entities = er.async_get(hass)
    panels = {}
    for entry in hass.config_entries.async_entries(MOBILE_APP):
        if entry.disabled_by or entry.data.get(ATTR_APP_ID) != APP_ID:
            continue
        webhook_id = entry.data[CONF_WEBHOOK_ID]
        display = entities.async_get_entity_id("sensor", MOBILE_APP, f"{webhook_id}_display_mode")
        if not display or not (sensor := entities.async_get(display)) or not sensor.device_id:
            continue
        if device := devices.async_get(sensor.device_id):
            panels[device.id] = Panel(hass, device, webhook_id, display)
    return panels
