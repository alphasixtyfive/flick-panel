"""Choose a tablet registered through Flick Panel's Tablet link."""

import probatio as vol

from homeassistant.config_entries import ConfigFlow
from homeassistant.helpers.selector import SelectSelector, SelectSelectorConfig

from .panel import CONF_PANEL, DOMAIN, linked_panels


class FlickPanelConfigFlow(ConfigFlow, domain=DOMAIN):
    """Add controls to a linked Flick Panel device."""

    VERSION = 1
    MINOR_VERSION = 2

    async def async_step_user(self, user_input=None):
        existing = {entry.unique_id for entry in self._async_current_entries()}
        available = {key: panel for key, panel in linked_panels(self.hass).items() if key not in existing}
        if not available:
            return self.async_abort(reason="no_linked_panels")

        errors = {}
        if user_input is not None:
            device_id = user_input.get(CONF_PANEL)
            if panel := available.get(device_id):
                await self.async_set_unique_id(device_id)
                self._abort_if_unique_id_configured()
                return self.async_create_entry(
                    title=panel.device.name_by_user or panel.device.name or "Flick Panel",
                    data={CONF_PANEL: device_id},
                )
            errors["base"] = "panel_unavailable"

        options = [
            {"value": key, "label": panel.device.name_by_user or panel.device.name or "Flick Panel"}
            for key, panel in available.items()
        ]
        return self.async_show_form(step_id="user", data_schema=vol.Schema({
            vol.Required(CONF_PANEL, default=options[0]["value"]): SelectSelector(SelectSelectorConfig(options=options)),
        }), errors=errors)
