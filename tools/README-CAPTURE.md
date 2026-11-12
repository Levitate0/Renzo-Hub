# Play-listing capture for Renzo Hub

Ported from the retired standalone Renzo client (`tv-native`, removed
2026-10-09). Two scripts:

| | |
|---|---|
| `gen-demo-placeholders.sh` | Regenerates the `demo` build's placeholder plates. **Now renders from the Hub's own launcher foreground**, not the old standalone Renzo mark — re-run it whenever the Hub's icon changes or the plates keep showing the previous brand. Already run once; `feature-renzo/src/demo/assets/placeholder/` now carries Hub art. |
| `capture-play-shots.ps1` | Sweeps form factors × pages on a Windows emulator and saves one PNG each. |

## One change is needed in the Hub before phone/tablet capture works

`HubActivity` starts with `target = null` on every device, TV included (TV
shows the launch picker too), so **every `am start` lands on
the launch picker**, not the page the capture asked for.

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

Until it lands, shoot each form factor by hand after picking Renzo on the
picker; the demo extras below still apply once Renzo is open.

## Everything else carried over unchanged

The `-e page` values, `-e tv 1`, `-e theme`, `-e query`, `-e id`, `-e ep`
extras all still work once the Renzo half is open — `RenzoEntry` calls
`DemoMode.readIntent` / `applyWindow` / `applyNav` exactly as the old
standalone `MainActivity` used to.

Form factors, the animation-disabling, the status-bar demo mode, and the
`screencap`-then-`pull` capture (never `exec-out >`, which corrupts the PNG in
PowerShell) are all as documented in [`docs/DEMO-BUILD.md`](../docs/DEMO-BUILD.md).
