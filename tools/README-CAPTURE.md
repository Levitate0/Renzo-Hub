# Play-listing capture for Renzo Hub

Ported from `tv-native/tools/` as part of the decommission. Two scripts:

| | |
|---|---|
| `gen-demo-placeholders.sh` | Regenerates the `demo` build's placeholder plates. **Now renders from the Hub's own launcher foreground**, not the old standalone Renzo mark — re-run it whenever the Hub's icon changes or the plates keep showing the previous brand. Already run once; `feature-renzo/src/demo/assets/placeholder/` now carries Hub art. |
| `capture-play-shots.ps1` | Sweeps form factors × pages on a Windows emulator and saves one PNG each. |

## One change is needed in the Hub before phone/tablet capture works

`HubActivity` starts with `target = null` on anything that isn't leanback, so
**every `am start` lands on the launch picker**, not the page the capture asked
for. TV is unaffected (`target` starts `Renzo`), so TV screenshots work today —
phone, 7" and 10" do not.

The script therefore passes `-e app renzo`. For that to do anything,
`HubActivity.onCreate` needs to seed the target from the intent:

```kotlin
var target by rememberSaveable {
    mutableStateOf(
        when {
            isTv -> HubTarget.Renzo
            // Launch straight into one half: used by the listing-capture
            // harness, and equivalent to tapping a picker tile.
            intent?.getStringExtra("app")?.lowercase() == "renzo" -> HubTarget.Renzo
            intent?.getStringExtra("app")?.lowercase() == "shiori" -> HubTarget.Shiori
            else -> null
        },
    )
}
```

It's a launch convenience, not a security boundary — the extra only picks which
half is shown, exactly as tapping a tile does — so it's safe in release builds
and doubles as the basis for launcher shortcuts. I did not apply it: it's your
activity and you're actively working in this tree.

Until it lands, capture TV with the script and shoot the phone/tablet form
factors by hand after tapping through the picker.

## Everything else carried over unchanged

The `-e page` values, `-e tv 1`, `-e theme`, `-e query`, `-e id`, `-e ep`
extras all still work — `RenzoEntry` calls `DemoMode.readIntent` /
`applyWindow` / `applyNav` exactly as `MainActivity` used to.

Form factors, the animation-disabling, the status-bar demo mode, and the
`screencap`-then-`pull` capture (never `exec-out >`, which corrupts the PNG in
PowerShell) are all as documented in `tv-native/DEMO-BUILD.md`, which is still
accurate for everything except the activity name and the picker.
