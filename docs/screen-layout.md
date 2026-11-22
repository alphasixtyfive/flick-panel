# Flick Panel screen layout

Use these rules when adding or updating a screen.

## Headers

- Use one line for the screen title and controls. Align the title with the leading edge of its content. Ellipsize long names when space runs out.
- Put **Close** in a 48 dp circular button at the trailing edge of the header it dismisses. Put **Back** at the leading edge when it moves up a level, such as inside Settings or a music folder. Android Back follows the same hierarchy.
- Keep status and secondary actions on the same row. Leave 16 dp between inline status and Close, and at least 8 dp between adjacent header actions.
- Use 8 dp of breathing room above standard app bars after accounting for system insets. The dashboard uses larger spacing for its clock and tabs. Video controls sit on a scrim with their own insets so the feed can fill the screen.
- Reuse `ViewerTopAppBar` and `ViewerCloseButton` in `ui/ViewerChrome.kt`. Let the parent layout apply safe-area and keyboard insets once. [Material app bars](https://developer.android.com/develop/ui/compose/components/app-bars) · [Compose insets](https://developer.android.com/develop/ui/compose/system/insets-ui)

## Footers

- Give a persistent primary action its own footer outside the scrollable content. **Add item**, **Clean**, and **Save changes** use `PanelActionFooter` with 8 dp above, 16 dp below, a content-aligned gutter, and controls at least 48 dp high.
- Place the primary action at the trailing edge. It can fill the width of a narrow supporting pane when it is the only action. A count or short status can sit on the leading edge.
- Show a footer when the screen has an action to offer. Settings shows **Save changes** when there are edits. Read-only views let the content use the available height.
- Keep playback and intercom controls in their media docks. In dialogs and sheets, keep confirmation and dismissal in the Material component's action slots. [Compose Scaffold](https://developer.android.com/develop/ui/compose/components/scaffold) · [Touch targets](https://developer.android.com/develop/ui/compose/accessibility/api-defaults)

## How the screens fit

| Screen | Header and footer |
| --- | --- |
| Favourites, Rooms and Cameras | Home, the clock, and dashboard tabs share the dashboard grid. Room names head the room content. Favourites uses page controls when its tiles need more space. |
| Vacuum | Title, status and Close share one row. The map and checklist stack below 600 dp, split evenly from 600 to 839 dp, and use a 70/30 split at 840 dp and above. The Rooms heading and Clean footer align with the checklist's outer edge. Clean stays visible while the room list scrolls. Selection changes the checkbox and fill colour while each row keeps its shape. |
| Shopping list and tasks | Refresh and Close stay in the app bar. Writable lists put Add item in the footer. The item editor uses a dialog. |
| Calendar and reader | A single app bar sits above width-capped, scrollable content. Calendar Refresh belongs in the app bar. |
| Person map | Name, location status and Close sit over the full-screen map on the same scrim as the camera viewer. Map tools and attribution stay at the bottom. |
| Music player and library | A pinned title and Close sit above playback or browsing. An idle player uses the artwork space for its status and one supported action. Back goes up one folder and returns to the player from the library root. |
| Live camera and intercom | Camera name leads. Speaker, status and Close share a 48 dp-high row. Connecting and Live keep the same pill height, and Live uses the Flick TV green dot. Intercom and camera actions sit in the lower dock. |
| Settings and editors | Close stays at the far trailing edge of the Settings bar on both compact and wide screens. Nested editors add Back with space before the title. Wider windows keep the category pane beside the editor. Save changes stays below the editor and above compact navigation. |
| Connection, PIN, prompts and light controls | First-run connection uses a bounded form. Connection settings use the existing screen title. PIN, confirmation, folder picking, and light controls use their dialog or sheet layout. |
| Photo clock and blank display | The clock gives its space to the time, date, and optional message. Touch wakes the dashboard. |

For a UI change, check the same view at phone and tablet widths. Include long titles, larger text, offline or loading status, and the keyboard where relevant. Confirm that Close, Android Back, and the primary action work at each level. [Android adaptive layouts](https://developer.android.com/develop/adaptive-apps/guides/canonical-layouts)
