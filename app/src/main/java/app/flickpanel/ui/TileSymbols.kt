package app.flickpanel.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Balcony
import androidx.compose.material.icons.rounded.Bathtub
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Bed
import androidx.compose.material.icons.rounded.Blender
import androidx.compose.material.icons.rounded.Blinds
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Chair
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.CoffeeMaker
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Countertops
import androidx.compose.material.icons.rounded.Crib
import androidx.compose.material.icons.rounded.Deck
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.DoorBack
import androidx.compose.material.icons.rounded.DoorFront
import androidx.compose.material.icons.rounded.Doorbell
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Dry
import androidx.compose.material.icons.rounded.ElectricBolt
import androidx.compose.material.icons.rounded.ElectricalServices
import androidx.compose.material.icons.rounded.EmojiObjects
import androidx.compose.material.icons.rounded.EnergySavingsLeaf
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fence
import androidx.compose.material.icons.rounded.Fireplace
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flood
import androidx.compose.material.icons.rounded.Garage
import androidx.compose.material.icons.rounded.GasMeter
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HeatPump
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Hub
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Iron
import androidx.compose.material.icons.rounded.Kitchen
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MeetingRoom
import androidx.compose.material.icons.rounded.Microwave
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Opacity
import androidx.compose.material.icons.rounded.OutdoorGrill
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Recycling
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.Shower
import androidx.compose.material.icons.rounded.SmokeFree
import androidx.compose.material.icons.rounded.SolarPower
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.SpeakerGroup
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material.icons.rounded.TableRestaurant
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Theaters
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Yard
import androidx.compose.ui.graphics.vector.ImageVector
import app.flickpanel.config.TileIcon

internal val TileIcon.symbol: ImageVector
    get() = when (this) {
        TileIcon.LIGHT -> Icons.Rounded.Lightbulb
        TileIcon.SCENE -> Icons.Rounded.AutoAwesome
        TileIcon.FIREPLACE -> Icons.Rounded.Fireplace
        TileIcon.MOVIE -> Icons.Rounded.Movie
        TileIcon.WATER -> Icons.Rounded.WaterDrop
        TileIcon.TIMER -> Icons.Rounded.Timer
        TileIcon.POWER -> Icons.Rounded.PowerSettingsNew
        TileIcon.FAN -> Icons.Rounded.Air
        TileIcon.TEMPERATURE -> Icons.Rounded.Thermostat
        TileIcon.GATE -> Icons.Rounded.Fence
        TileIcon.BLINDS -> Icons.Rounded.Blinds
        TileIcon.MUSIC -> Icons.Rounded.MusicNote
        TileIcon.SPEAKER -> Icons.Rounded.Speaker
        TileIcon.CLEANING -> Icons.Rounded.CleaningServices
        TileIcon.SHOPPING -> Icons.Rounded.ShoppingCart
        TileIcon.CALENDAR -> Icons.Rounded.CalendarMonth
        TileIcon.GRAPH -> Icons.AutoMirrored.Rounded.ShowChart
        TileIcon.BELL -> Icons.Rounded.Notifications

        TileIcon.HOME -> Icons.Rounded.Home
        TileIcon.ROOM -> Icons.Rounded.MeetingRoom
        TileIcon.KITCHEN -> Icons.Rounded.Kitchen
        TileIcon.SOFA -> Icons.Rounded.Weekend
        TileIcon.BED -> Icons.Rounded.Bed
        TileIcon.BATH -> Icons.Rounded.Bathtub
        TileIcon.SHOWER -> Icons.Rounded.Shower
        TileIcon.GARAGE -> Icons.Rounded.Garage
        TileIcon.BALCONY -> Icons.Rounded.Balcony
        TileIcon.GARDEN -> Icons.Rounded.Yard
        TileIcon.CHAIR -> Icons.Rounded.Chair
        TileIcon.DINING -> Icons.Rounded.TableRestaurant
        TileIcon.WORK -> Icons.Rounded.Work
        TileIcon.SCHOOL -> Icons.Rounded.School
        TileIcon.PET -> Icons.Rounded.Pets
        TileIcon.NURSERY -> Icons.Rounded.Crib
        TileIcon.PATIO -> Icons.Rounded.Deck
        TileIcon.TREE -> Icons.Rounded.Park
        TileIcon.FLOWER -> Icons.Rounded.LocalFlorist
        TileIcon.SUN -> Icons.Rounded.WbSunny
        TileIcon.NIGHT -> Icons.Rounded.NightsStay

        TileIcon.COOLING -> Icons.Rounded.AcUnit
        TileIcon.HEAT_PUMP -> Icons.Rounded.HeatPump
        TileIcon.FIRE -> Icons.Rounded.LocalFireDepartment
        TileIcon.HUMIDITY -> Icons.Rounded.Opacity
        TileIcon.RAIN -> Icons.Rounded.Umbrella
        TileIcon.THUNDER -> Icons.Rounded.Thunderstorm
        TileIcon.CLOUD -> Icons.Rounded.Cloud
        TileIcon.SUNSET -> Icons.Rounded.WbTwilight
        TileIcon.FLOOD -> Icons.Rounded.Flood
        TileIcon.BRIGHTNESS -> Icons.Rounded.Brightness6
        TileIcon.LAMP -> Icons.Rounded.EmojiObjects
        TileIcon.MICROWAVE -> Icons.Rounded.Microwave
        TileIcon.COFFEE_MAKER -> Icons.Rounded.CoffeeMaker

        TileIcon.COFFEE -> Icons.Rounded.Coffee
        TileIcon.BLENDER -> Icons.Rounded.Blender
        TileIcon.WASHING_MACHINE -> Icons.Rounded.LocalLaundryService
        TileIcon.DRYER -> Icons.Rounded.Dry
        TileIcon.IRON -> Icons.Rounded.Iron
        TileIcon.WORKTOP -> Icons.Rounded.Countertops
        TileIcon.BARBECUE -> Icons.Rounded.OutdoorGrill
        TileIcon.PLUG -> Icons.Rounded.Power
        TileIcon.ELECTRICITY -> Icons.Rounded.ElectricalServices
        TileIcon.BATTERY -> Icons.Rounded.BatteryChargingFull
        TileIcon.EV_CHARGING -> Icons.Rounded.EvStation
        TileIcon.SOLAR -> Icons.Rounded.SolarPower
        TileIcon.ENERGY -> Icons.Rounded.ElectricBolt
        TileIcon.ENERGY_SAVING -> Icons.Rounded.EnergySavingsLeaf
        TileIcon.SPEED -> Icons.Rounded.Speed
        TileIcon.POOL -> Icons.Rounded.Pool
        TileIcon.GAS -> Icons.Rounded.GasMeter
        TileIcon.RECYCLING -> Icons.Rounded.Recycling
        TileIcon.FRONT_DOOR -> Icons.Rounded.DoorFront
        TileIcon.BACK_DOOR -> Icons.Rounded.DoorBack

        TileIcon.LOCK -> Icons.Rounded.Lock
        TileIcon.UNLOCK -> Icons.Rounded.LockOpen
        TileIcon.KEY -> Icons.Rounded.VpnKey
        TileIcon.VIDEO_CAMERA -> Icons.Rounded.Videocam
        TileIcon.CAMERA -> Icons.Rounded.CameraAlt
        TileIcon.DOORBELL -> Icons.Rounded.Doorbell
        TileIcon.SENSOR -> Icons.Rounded.Sensors
        TileIcon.WIFI -> Icons.Rounded.Wifi
        TileIcon.NETWORK -> Icons.Rounded.Lan
        TileIcon.ROUTER -> Icons.Rounded.Router
        TileIcon.SHIELD -> Icons.Rounded.Shield
        TileIcon.SECURITY -> Icons.Rounded.Security
        TileIcon.PERSON -> Icons.Rounded.Person
        TileIcon.FAMILY -> Icons.Rounded.Groups
        TileIcon.LOCATION -> Icons.Rounded.LocationOn
        TileIcon.WARNING -> Icons.Rounded.WarningAmber
        TileIcon.FLAG -> Icons.Rounded.Flag
        TileIcon.HUB -> Icons.Rounded.Hub
        TileIcon.SMOKE -> Icons.Rounded.SmokeFree
        TileIcon.TV -> Icons.Rounded.LiveTv
        TileIcon.CINEMA -> Icons.Rounded.Theaters

        TileIcon.HEADPHONES -> Icons.Rounded.Headphones
        TileIcon.SPEAKER_GROUP -> Icons.Rounded.SpeakerGroup
        TileIcon.SURROUND_SOUND -> Icons.Rounded.SurroundSound
        TileIcon.ALBUM -> Icons.Rounded.Album
        TileIcon.RADIO -> Icons.Rounded.Radio
        TileIcon.PLAYLIST -> Icons.AutoMirrored.Rounded.QueueMusic
        TileIcon.GAMES -> Icons.Rounded.SportsEsports
        TileIcon.ART -> Icons.Rounded.Palette
        TileIcon.BOOK -> Icons.Rounded.Book
        TileIcon.READING -> Icons.Rounded.AutoStories
        TileIcon.BRUSH -> Icons.Rounded.Brush
        TileIcon.PHOTO -> Icons.Rounded.Photo
        TileIcon.FITNESS -> Icons.Rounded.FitnessCenter
        TileIcon.CYCLING -> Icons.AutoMirrored.Rounded.DirectionsBike
        TileIcon.FOOTBALL -> Icons.Rounded.SportsSoccer
        TileIcon.CLOCK -> Icons.Rounded.AccessTime
        TileIcon.ALARM -> Icons.Rounded.Alarm

        TileIcon.TASKS -> Icons.Rounded.Checklist
        TileIcon.LIST -> Icons.AutoMirrored.Rounded.List
        TileIcon.NOTES -> Icons.AutoMirrored.Rounded.Notes
        TileIcon.TOOLS -> Icons.Rounded.Build
        TileIcon.PHONE -> Icons.Rounded.Phone
        TileIcon.TABLET -> Icons.Rounded.TabletAndroid
        TileIcon.MONITOR -> Icons.Rounded.DesktopWindows
        TileIcon.COMPUTER -> Icons.Rounded.Computer
        TileIcon.DOWNLOAD -> Icons.Rounded.Download
        TileIcon.INFORMATION -> Icons.Rounded.Info
        TileIcon.FAVOURITE -> Icons.Rounded.Favorite
        TileIcon.STAR -> Icons.Rounded.Star
        TileIcon.CHECK -> Icons.Rounded.CheckCircle
    }
