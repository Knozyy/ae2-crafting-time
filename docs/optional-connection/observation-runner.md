# Connected observation runner

Use the existing prepared launch, bundle, and sealed dedicated source for the
target. The report directory must be new. `-ObservationMode` on
`scripts/run-connected-dedicated-ui-smoke.ps1` selects `-InstallationMode` from
`both`, `server-only`, `client-only`, or `neither` and requires a stopped native
AE2 world passed as `-NativeWorldDirectory`. The runner copies it into a
disposable server; it never modifies the source.

The world root must contain `.ae2-crafting-time-native-world.json`:

```json
{
  "target": "1.20.1-forge",
  "stopped": true,
  "driverRegistryClean": true,
  "levelDatSha256": "SHA-256 of level.dat"
}
```

Create this attestation only after a clean server stop and a review that the
world uses native AE2 registry content, without test-driver blocks, items, or
fluids. The runner compares its target and level hash before copying.

For each cell, the operator completes a real craft and captures English native
plan and status screens under `<report>/client/evidence/`. Record `observedAt`
at the final screenshot. For a cell with Crafting Time on the server, disconnect
the client gracefully and wait for the server observer to flush that connection's
receipt. Then write `<report>/manual-checkpoints.json` with the matching
target/mode, the recorded UTC timestamp, and the two screenshot filenames:

```json
{
  "target": "1.20.1-forge",
  "mode": "server-only",
  "login": true,
  "craft": true,
  "clientProcessId": 1234,
  "connectionOrdinal": 1,
  "serverConnectionOrdinal": 1,
  "observedAt": "2026-09-26T12:00:00Z",
  "planScreenshot": "plan.png",
  "statusScreenshot": "status.png"
}
```

The runner waits at most `-ManualTimeoutSeconds` (default 1200), checks both
inventories, screenshots and installed-side packet receipts, and rejects a server
receipt last flushed before `observedAt`. It then requests a
clean RCON stop of the disposable server. Inspect `server-stop.txt` for `clean`
and compare the screenshots by eye. A manual receipt or packet count alone does
not prove correct UI or server-only history retention; restart the saved
disposable world with a supported client and inspect history separately.

For same-process transitions, pass one new `-ClientSessionDirectory` for the
installed-client sequence (`both`, `client-only`, `both`) and a separate new
directory for the native-client sequence (`server-only`, `neither`). Use
`-KeepClientAlive` on every cell except the final one, and increment
`-ConnectionOrdinal` from 1 for each connection. Each cell still needs its own
new report directory. The runner verifies the saved PID, process start time,
artifact hashes and target before reusing the client. After each clean server
stop, reconnect that same client through the multiplayer screen to the next
disposable server. Do not start another client. Read the PID from the session's
`session.json` when writing manual checkpoints. A failed cell stops its owned
client; begin a new clean sequence after correcting the failure.

The observer preserves startup and login traffic in ordinal 1 and advances
the client ordinal at disconnect. Client probes use the newly negotiated peer
support on each join, so the same installed client can observe either mode.
Retain the shared session directory alongside all cell reports until review.
