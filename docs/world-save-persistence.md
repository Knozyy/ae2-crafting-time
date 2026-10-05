# Saving Crafting History With The World

Date: 2026-06-21

## Goal

Persist collected crafting times in the Minecraft world save:

- file path: `./data/ae2-crafting-time.dat`
- include all collected craft outputs and their crafting time
- load state from the saved file on world load
- save through Minecraft/Forge default saving mechanisms

This history preserves the learned throughput used by TTC estimates and
slow-craft diagnostics across restarts.

Tick flushes update only changed output histories in an indexed SavedData model.
Unchanged outputs retain their immutable sample records. Reset and overflow
removal are explicit deletions. The serializer takes a stable list at save time;
the version-1 output fields and file ID are unchanged. Changes remain pending
while Save History is off and reconcile on the next enabled flush, including
configuration trimming. Aggregate-stat caching has separate invalidation and
cannot consume persistence updates.

Provider records and statuses have separate dirty state. Notification polling
does not serialize them; the shared end-tick/save flush snapshots each changed
collection once. Repeated NO POWER/NO PROVIDER observations keep the episode's
original timestamp instead of rewriting an unused timestamp every tick. Waiting
and delayed timing, resolution, deletion and owner-bound click fallbacks retain
their existing meaning. Save History off leaves dirty changes pending.

## Does It Fit?

Yes. Minecraft's `SavedData` already handles this, so direct file IO would only
add work and risk.

Forge documents `SavedData` as the standard way to save level data. A `SavedData` instance is loaded or created through `DimensionDataStorage#computeIfAbsent(...)`; the name argument becomes the `.dat` file under that level's `data` folder. Local 1.20.1 bytecode confirms `DimensionDataStorage#getDataFile(name)` writes:

```text
dataFolder / (name + ".dat")
```

So the storage id must be:

```text
ae2-crafting-time
```

not `ae2-crafting-time.dat`, otherwise Minecraft would write `ae2-crafting-time.dat.dat`.

Attach the data to the overworld storage:

```java
server.overworld().getDataStorage().computeIfAbsent(factory, "ae2-crafting-time");
```

That produces:

```text
<world>/data/ae2-crafting-time.dat
```

This also covers singleplayer because an integrated server still has the same overworld `ServerLevel` and data storage.

## What To Persist

Persist the retained rolling samples, not only aggregate `ProfileStats`.

Current runtime behavior keeps retained completed crafts per output key. To keep
estimates stable after restart, save the same retained samples:

```text
output id
unit
samples:
  amount
  durationTicks
```

Do not persist pending crafts. Pending operations are in-flight runtime state; if the server shuts down mid-craft, those timings are not reliable.

## Remembered Statuses

Persist each output's last-known status (see
[issue #238](https://github.com/cTux/ae2-crafting-time/issues/238)) so rows
keep their badges after leaving and re-entering the world instead of falling
back to bare TTC:

```text
statuses: [
  {
    networkId: "minecraft:overworld|10,64,10"
    key: "minecraft:iron_ingot"
    kind: "delayed" | "waiting" | "no_provider" | "no_power"
    idleTicks: 300
    typicalTicks: 20.0
    acceptedAtTick: 18400
  }
]
```

Live dispatch data always wins: a new pending craft drops the remembered
status for its output, finishing or cancelling drops it, and a remembered
row only renders while nothing live contradicts it. `NO SPACE` stays
live-only because the client derives it from the open CPU screen each frame.
Bounded to 256 entries with tolerant reads and no save-version bump, same
as the other sections.

## NBT Shape

Use one top-level version and a list of output entries:

```text
version: 1
outputs: [
  {
    networkId: "minecraft:overworld|10,64,10"
    key: "minecraft:iron_ingot"
    unit: "item"
    samples: [
      { amount: 10, durationTicks: 220 },
      { amount: 10, durationTicks: 205 }
    ]
  },
  {
    networkId: "minecraft:overworld|10,64,10"
    key: "minecraft:water"
    unit: "millibucket"
    samples: [
      { amount: 1000, durationTicks: 40 }
    ]
  }
]
```

This is enough to reconstruct averages, throughput, latest duration, `TTC`, and colors.
Applied Botanics entries use the named `MANA` unit and raw mana amounts. Legacy
`botania:mana` entries recorded as `MILLIBUCKET` are converted from milli-pools
by multiplying representable positive amounts by 1,000. Other histories are
unchanged; precision already lost by the old rounding cannot be restored.
Controller-backed networks now persist a concrete `networkId` derived from the
controller anchor position.

## Runtime Ownership

Server owns:

- `CraftProfiler`
- persisted sample data
- load/save through `SavedData`
- network snapshots to clients

Client owns:

- display cache only
- no file access
- no persistence

## Loading

On server/world load:

1. Get overworld `DimensionDataStorage`.
2. `computeIfAbsent(Ae2CraftingTimeSavedData.factory(), "ae2-crafting-time")`.
3. `SavedData.load(...)` decodes NBT into persisted network/output samples.
4. `ProfilerBridge` installs or hydrates its server profiler from the loaded samples.

Lazy loading is fine as long as it happens before the first stats request or
sample write. The simpler option is to initialize once from a server lifecycle
event after the overworld exists.

## Saving

Do not manually write files.

`SavedData` is saved by the normal world save pipeline when dirty:

```java
savedData.setDirty();
```

Call `setDirty()` whenever a completed craft sample is recorded. Minecraft later calls `SavedData#save(...)`, wraps the returned tag under `data`, adds data version metadata, and writes the compressed NBT file.

## Code Shape

Shared module:

- add a small persisted sample DTO, e.g. `CraftSampleSnapshot`
- expose profiler import/export methods:
  - `List<PersistedOutputSamples> snapshotSamples()`
  - `void loadSamples(List<PersistedOutputSamples> samples)`

`versions/1.20.1-forge`:

- `Ae2CraftingTimeSavedData extends SavedData`
- NBT encode/decode
- bridge from `SavedData` to `ProfilerBridge`
- server lifecycle hook to initialize from overworld storage

## Dirty Tracking

Smallest reliable path:

1. `ProfilerBridge` owns a nullable `Ae2CraftingTimeSavedData`.
2. On load, saved data hydrates `CraftProfiler`.
3. When `CraftProfiler.complete(...)` records at least one completed sample, `ProfilerBridge` calls:

```java
savedData.replaceFrom(profiler.snapshotSamples());
savedData.setDirty();
```

This rewrites the retained sample snapshot in memory, but the disk write still happens through Minecraft's normal save. The retained window is only 10 samples per output, so this is simpler than incremental mutation and good enough.

## Existing Config Impact

Docs should state that retained samples persist in the world save.

The runtime should still respect:

```text
enabled = true
```

If disabled, do not record new samples. Loaded samples can remain in memory, but UI should continue to hide stats because existing `ProfilerBridge.stats(...)` already returns empty when disabled.

## Risks

- `SavedData` format is version-specific Minecraft code, so keep it under `versions/1.20.1-forge`.
- Persisting only aggregates would lose the latest-10 behavior after restart. Persist samples instead.
- If output ids disappear because a mod is removed, keep their saved entries harmlessly. They will not be requested by the UI if those items are gone.
- The save file can grow with every distinct output ever crafted. Each output keeps only 10 samples, so growth is bounded by output variety, not craft count.

## Tests

Shared tests:

- profiler exports latest 10 samples for an output
- profiler imports samples and produces the same `ProfileStats`
- importing more than 10 samples keeps the latest 10

Version tests:

- saved data writes `version` and `outputs`
- saved data loads entries into equivalent profiler samples
- storage id constant is exactly `ae2-crafting-time`

## Chosen Approach

Use `SavedData` attached to the overworld with id `ae2-crafting-time`. Persist retained samples, not pending crafts and not only aggregate stats. This satisfies the requested `./data/ae2-crafting-time.dat` path while letting Minecraft handle load, dirty tracking, compression, data version wrapping, and save timing.

## Sources

- Forge Saved Data docs: https://docs.minecraftforge.net/en/latest/datastorage/saveddata/
- Local Minecraft 1.20.1 source/bytecode inspected:
  - `net.minecraft.world.level.saveddata.SavedData`
  - `net.minecraft.world.level.storage.DimensionDataStorage`
  - `net.minecraft.world.level.ForcedChunksSavedData`
