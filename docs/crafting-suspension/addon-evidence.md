# Addon suspension planning evidence

Inspected on 2026-10-03 against planning base `f0cf420b`. This records released
bytecode and repository seams, not a Minecraft run or a support claim.

| Artifact | SHA-512 | Observation |
| --- | --- | --- |
| AdvancedAE `1.3.6-1.20.1` | `11a675245ce0d18428ceba4524a2e89e037d09d8a932ac5d50594b1a0d88920ff8d2dd4622b011c6f51454b211d5e33ca08450882bb6334c47bf693ccbd916ef` | Independent CPU/logic; public dispatch, native job UUID and one-argument NBT; no public player-pause API in the inspected logic. |
| NeoEco `20.3.0` | `07da3cad9b513470e72834e88fbe02a4d44f38e2a851bab25d047202f67245189dba04be69bc205dd08521984ac625b5e6aafbae4012e76c04dcdafc533c9f19` | Separate native user pause and internal suspension; private budgeted dispatch entrance; setter dirties CPU; job NBT writes/reads `userPaused` independently from `suspended`. |
| NeoEco `20.4.2` | `36ec97ef0d5b5226eaa58fa9e253a2fc24b8218207d12cd162ce913fb029be75bee8d2e4e352bbe64861c444e596bf50f5961b7c0bf028d86821544d2c2a1d8c` | Same pause distinction; public dispatch entrance; player-pause setter dirties CPU; job NBT persists `userPaused` and `suspended`. |
| LightningTech `2.1.0-beta.4-forge.1.20.1` | `d08720dce5b47ffc1df793c8eb0e0784dd23e67035e006e1091b3076f81fd49944bf7e0c0c2295028885d08075382fd1a950b23daa91a8406120b7c1b13e3834` | Native suspension API and persistent job flag; public dispatch delegates to private budgeted dispatch; setter dirties CPU. |

NeoEco hashes match the retained
[20.3.0 contract](../../shared/src/test/resources/integration-contracts/neoeco-20.3.0.tsv)
and [20.4.2 contract](../../shared/src/test/resources/integration-contracts/neoeco-20.4.2.tsv).
AdvancedAE matches the pinned build dependency and
[client inventory](../../scripts/run-client-versions.json). LightningTech's hash
matches CurseForge file 8829989 in that inventory; newer release/source identity
remains tracked in [#460](https://github.com/cTux/ae2-crafting-time/issues/460).

`javap -p -c` inspected lifecycle, dispatch, pause setters and persisted-job
classes. ZIP entries were inspected directly for addon CPU/menu/screen classes.
The NeoEco menu owns `neoecoae$cpu`; LightningTech owns
`thunderbolt$timeWheelCpu`; the existing AdvancedAE selected-CPU reader uses
`advancedAE$advCpu`. These slots need contract and transformed-runtime checks
before the resolver is accepted. Native APIs alone do not prove that TTC's
button can select or safely pause these jobs.

Rechecked NeoEco 20.3.0 on 2026-10-05 against the same SHA-512. In
`ECOCraftingCPULogic.setJobUserPaused(boolean)`, a changed live job flag calls
`ECOCraftingCPU.markDirty()` when the CPU exists and `postChange(null)`.
`ExecutingCraftingJob` loads and writes `userPaused` using CompoundTag boolean
accessors. Logic read/write descriptors are
`(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V`;
the job writer takes HolderLookup.Provider and returns CompoundTag. These
bytecode observations support native persistence delegation, not restart proof.

LightningTech beta.2/beta.3 suspension contracts were not inspected. The plan
therefore sets beta.4 as the suspension capability floor while preserving the
existing beta.2 profiling minimum. No support claim is made for earlier pause APIs.

Cached MEGA Cells `forge-2.4.6` has no separate CPU/logic class among its ZIP
entries. Keep its existing native-CPU path; do not add a replacement adapter
merely because a fixture has an addon name.

The working tree currently excludes replacements in three places: the standard
logic mixin's support method, the menu snapshot, and the C2S action. Changing
only one leaves suspension inaccessible or advertises an action that is rejected.
