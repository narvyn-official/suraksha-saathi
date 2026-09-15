# AR rendering and spatial placement — 0.5.4 candidate

## Implemented changes

The equipment and room renderers now upload each immutable mesh to a GPU vertex buffer once per OpenGL context. Subsequent frames reuse that buffer. Flame instances share the same flame/core geometry. Shapes, material shaders, animations and learning tolerances are unchanged. Buffer bindings are cleared after drawing so the camera-background pass can continue using its own vertex arrays. A recreated context starts with an empty buffer cache; Android releases the old context's resources when it is destroyed.

Gas-mission green markers must now sit wholly on the outside/front side of the barrier, clear of the simulated opening. Previously the radial distance check accepted a marker behind the barrier, so the mission could label an attendant inside the hazard area as outside. The shared placement policy uses the hazard's actual facing rotation and preserves the existing minimum spacing. The live preview and anchor-commit path use the same decision; fresh camera frames, tracked planes, tracked existing anchors and lifecycle revision checks remain required. The green placement preview shows the full 45cm virtual marker radius. These dimensions describe simulation geometry, not operational safety distances or real-floor clearance.

While dragging the gas attendant, offscreen guidance now directs the learner toward the green destination. Previously it continued pointing back to the source equipment station.

## Fire-sweep investigation

The failing screen sweep was profiled without changing the 150ms continuity requirement. Owner-thread sample work took approximately 0–3ms, while callback gaps often exceeded 150ms. A sampled main-thread stack was dominated by `HardwareRenderer.nSyncAndDrawFrame`, alongside normal message-queue waiting.

The QA emulator had been launched with `-gpu swiftshader_indirect`, a software graphics backend. Switching the same emulator to `-gpu host` enabled its hardware graphics backend. The unmodified complete fire and gas interaction tests then passed together in 26.513 seconds. The new mesh cache alone did not eliminate the software-emulator failure. Asynchronous callback scheduling and disabling hardware acceleration for the native UI were unsuccessful diagnostic experiments and were reverted. Neither the mission engine nor its evidence thresholds were relaxed, and the original test driver was retained.

Record the emulator graphics mode alongside timing results. Hardware-backed emulator passes verify simulated screen interactions; they do not verify physical ARCore tracking, real-world anchoring, or phone thermal performance. [Android emulator graphics guidance](https://developer.android.com/studio/run/emulator-acceleration).

## Final candidate validation

- Final debug APK and test APK build succeeded; **93 JVM tests passed**, with zero failures, errors or skipped tests. Six new tests cover translated/rotated gas placement, full marker clearance, previous spacing constraints and invalid input.
- **Eight Android test methods passed** on the API 36 arm64 emulator with host graphics. The combined complete room fire/gas missions, all five equipment identification flows, changed-angle/recreation rendering and prior spatial procedure flow passed in 85.390 seconds (four methods). Critical-heat and interrupted-discharge retry checks passed in 9.307 seconds (two methods); Hindi at 200% system font in portrait/landscape passed in 6.129 seconds; camera-denial/recreation passed in 3.847 seconds.
- Inspected captured fire, gas, changed-angle equipment, large-Hindi portrait/landscape and thermal-recovery screens. Equipment and scene geometry remain visible; primary controls remain reachable. The large-Hindi portrait title truncates, so this is not a claim of complete accessibility coverage.
- Thermal overrides, font scaling and camera permission were restored after their checks. Test fixtures ran only on the emulator. Screenshots are local under `artifacts/ar-054-qa/`; they are simulation/recovery evidence, not physical camera AR.
- APK: `artifacts/suraksha-saathi-0.5.4-debug.apk` (version code 14). SHA-256: `f84347013c21c92d1c02e739f8d5daeb860adc3c111569dbe85ae98b9efbc39b`.

## Physical test status

No physical phone was connected during this increment. The earlier Samsung test showed a live camera feed but no usable tracking/placement, together with delayed sensor poses and severe/critical thermal throttling. The cause of that real-device failure remains unconfirmed. Install this candidate on the reconnected phone and complete the physical acceptance checks in [AR recovery validation](ar-recovery-validation.md) before claiming that AR works.

The public release remains 0.5.0; this candidate does not update the public APK or claim practical certification.
