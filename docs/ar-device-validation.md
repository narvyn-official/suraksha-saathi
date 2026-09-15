# Physical-device AR validation protocol

Status: camera validation pending. A Samsung SM-S921B running Android 16/API 36 connected during the 0.5.1 work; AR services 1.56.262080393 were installed and an update from app 0.5.0 succeeded. These are installation checks only. Record results only after observing the actual device; never mark a camera check passed from a screen fallback.

## Prepare

1. Connect the phone by USB, enable Developer options / USB debugging, and approve this computer's debugging prompt. Use Android's [hardware device instructions](https://developer.android.com/studio/run/device).
2. Check `adb devices -l` and select the actual phone explicitly for every command when the emulator is also connected. Do not uninstall or clear the user's app data.
3. Record the model, Android version, app version and installed Google Play Services for AR version. Runtime ARCore availability and successful session creation determine whether camera testing can proceed; Android version alone is insufficient.
4. Install the matching debug pilot APK as an update. On a signature mismatch, stop and preserve existing data. Use a test learner profile. Work at a clear, textured tabletop away from real operations and hazards.

## Observe both modules

| Check | Evidence to capture | Pass condition |
| --- | --- | --- |
| Camera startup | App camera permission and AR service result | Live camera starts or a truthful recoverable unavailable state appears |
| Surface placement | Place once; move phone slowly sideways and around the tabletop | Equipment remains anchored rather than following the screen |
| Repositioning | Try an untracked area, then another tracked surface | Miss retains the prior anchor; successful placement replaces it |
| Fire targeting | Reach the four spatial fire steps; select locations using camera aiming and hold | Each accepted event contains camera attribution and a replay-valid spatial record; text/button actions have no spatial object |
| Gas positioning | Select the outside miniature-attendant position; in a separate independent trial select the inside position | Correct choice advances; unsafe choice stops without crediting the safe state |
| Tracking interruption | Obscure/move away from the surface during an incomplete hold | No completion from the lost/stale frames; a fresh continuous hold is needed after recovery |
| Lifecycle | Background, resume, rotate/recreate and switch learner during incomplete work | No stale action is saved; previous saved evidence and learner ownership are preserved |
| Alternatives | Deny/revoke permission; choose screen/text; use large text | Alternatives remain reachable and are attributed accurately |
| Device quality | Observe frame times, temperature, battery and responsiveness over a sustained run | Record measured values and failures; do not infer mid-range performance from a short successful placement |

Keep actual camera evidence distinct from screen recordings. Record observed results, device conditions and unresolved failures in `validation.md`. Do not include personal surroundings or learner identifiers in public artifacts. Production, industry content approval, and practical competency assessment remain separate gates.
