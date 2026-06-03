# PeopleHunt Feature Roadmap

Roadmap to a feature-rich manhunt administration and reporting plugin for Paper on Minecraft 26.1.2.

Rewritten in Kotlin from the ground-up based on the monstrous codebase that was [the vibecoded Java version](https://github.com/ganyuke/peoplehunt-java). This go-around should be much smarter about doing everything.

* Phase 1 is a playable version of the plugin with core mechanics.
* Phase 2-6 add the reporting infrastructure.
* Phase 7 adds the persistence layer to actually save the collected reports.
* Phase 8 adds the features that my friend wanted.
* Phase 9 re-adds the glorious web UI from the original PeopleHunt.

---

## Phase 1: MVP core manhunt gameplay

**Goal:** Complete basic Dream-style manhunt functionality.

### Compass tracking

* [x] While match is active, have compass track runner position.
* [x] Handle cross-dimension tracking (if the runner enters the Nether/End, point the compass to the last portal they used).

### Match lifecycle

* [x] Implement start condition 1: on runner movement (prime)
* [x] Implement start condition 2: on command (force-start)
* [x] Implement end condition 1: on runner death
* [x] Implement end condition 2: on Ender Dragon death
* [x] Create periodic match interval timer to broadcast elapsed time every `XX` minutes.

### Additional features

* Added `/ph status` to show current match status (IDLE, PRIMED, ACTIVE, FINISHED) and `/ph status last` to show previous match stats.
* Added compass behaviors: either override hunters' compass target OR use lodestone on specialized "Hunter Compasses".
* Added configuration options (`config.yml`) to choose hunter compass behavior, interval for compass update tick, interval for elapsed time broadcast.

### Implementation details

* Participants stats currently not collected; Finished match `/ph status` does not show stats currently.
* Expected runner to be a single player only.
* No mutation of runners, hunters, or configuration mid-match.
* Hunter compasses always given on respawn. Compass not filtered from death drops.

---

## Phase 2: Speedrun milestones

**Goal:** Log runner progress with typical Minecraft speedrun splits.

* [x] Acquired first iron ingot
* [x] Crafted/Acquired a bucket
* [x] Picked up water
* [x] Picked up lava
* [x] Entered Nether
* [x] Entered Fortress
* [x] Entered Bastion
* [x] Acquired first Ender Pearl
* [x] Acquired first Blaze Rod
* [x] Left the Nether
* [x] Crafted first Eye of Ender
* [x] Threw first Eye of Ender
* [x] Entered Stronghold
* [x] Completed End Portal
* [x] Entered the End
* [x] Destroyed first End Crystal
* [x] Destroyed all End Crystals
* [x] Brought the dragon to 50%
* [x] Brought the dragon to 25%
* [x] Brought the dragon to 10%
* [x] Brought the dragon to 5%
* [x] Slew the dragon / Had the dragon slain by someone/something else

### Implementation details

* Structure enter/exit for milestones uses `StructureListener` + `StructureLocator.getStructureAt` on `PlayerMoveEvent` when `hasChangedBlock()` (per player, not only runner).
* `ReportingEngine` consumes `PlayerEnteredStructure` / item / dimension / dragon HP milestones from the event bus.
* Left the Nether is dependent on obtaining the Blaze Rod achievement, since otherwise leaving the Nether at all would award this.
* Eye of Ender thrown detection is a bit of a hack. Will also fire if another player throws an eye while standing near the runner.
* Ender Portal completion is a bit of a hack relying on checking if the placed Eye of Ender will complete the End Portal. Had to steal [a bit of code from Cooperative End Access](https://github.com/ganyuke/CooperativeEndAccess/blob/main/src/main/java/io/github/ganyuke/cooperativeEndAccess/portal/PortalListener.java) to get this to work.
* `EndCrystalDestroyed` milestone is inferred from `PlayerDamagedEntity` with `entityIdentifier == "minecraft:end_crystal"`, attributed from `CombatStatsListener`.

---

## Phase 3: Core player tracking

**Goal:** Implement path recording for participants in the manhunt match.

### Core path recorder

* [x] Capture player coordinates + world/dimension (`PlayerMoved` on every move via `CoreListener`).
* [x] Record distinct teleportation causes (Ender Pearl, chorus, commands, portals, etc.) — `TeleportListener` → `TeleportSnapshot` + `TeleportCause`; filters `UNKNOWN` teleports under 4 blocks.
* [x] Handle discontinuities in path + filter out minor teleport noises beyond the teleport listener threshold (e.g. remaining vanilla push-out cases).
* [x] Record basic movement states on move (`sprinting`, `sneaking`, `flying`, `swimming`, `gliding` on `PlayerMoved`).
* [x] Richer movement/environment state on match ticks via `PlayerSnapshotChanged` (`MovementFlags`, `EnvironmentFlags`, game mode, vehicle, potion list on snapshot).
* [x] Record vitals during active matches via `PlayerSnapshotPoller` → `PlayerSnapshotChanged` (`Vitals`: health, food, air, XP, absorption, etc.) every tick while match is active.

### Combat and damage tracking

* [x] Record player ↔ entity damage amounts (`CombatStatsListener`, `PlayerDamagedEntity` / `PlayerDamagedByEntity`).
* [x] Record player deaths and killer attribution (`PlayerDied` / `EntityDied`, `KillCause`).
* [x] Record environmental damage with typed cause (`PlayerDamagedByEnvironment`).
* [x] Record projectile launch and hit (`ProjectileLaunched`, `ProjectileHit`).

### Implementation details

* Structure detection was moved to check for ALL players on block movement. A little expensive maybe.
* Pathing is no longer polling-based. All paths are snapshotted from player movement. Might turn out to be a really bad idea later on when I need to write this data.
* Need to detect discontinuities with PlayerTeleportEvent.
* Vitals derived from per-tick polling.

---

## Phase 4: End fight progress tracking

**Goal:** Watch the path of the dragon on the map and watch its health gradually decline.

* [x] **Dragon Position:** Poll position via `EndFightTracker` per-tick.
* [x] **Dragon Vitals:** Poll health via `EndFightTracker` per-tick.
* [x] **Dragon HP milestones** (50 / 25 / 10 / 5%).
* [x] **Attribute Killing blow**: Weapon/projectile/final-damage enrichment on all `EntityDied` / `PlayerDamagedEntity` / `PlayerDamagedByEntity` events.
* [x] **End Crystal map** (positions, per-crystal timeline via `EndFightTracker` + enriched `PlayerDamagedEntity`).
* [x] **End Crystal destroyed event & milestones**: Handled through `PlayerDamagedEntity` with `entityIdentifier == "minecraft:end_crystal"` in `processMilestoneTracking`.

---

## Phase 5: External status effects tracking

**Goal:** Record damage and status afflicting players.

* [x] **Environmental flags on snapshots** (`PlayerEnvironment`: `EnvironmentFlags` on `PlayerSnapshotChanged`).
* [x] **Fluid enter/exit events**
* [x] **Environmental tick windows** (first/last tick for drowning, suffocation, lava, etc.). Data collected via `PlayerDamagedByEnvironment` with windows reconstructed in frontend.
* [x] **Potion apply / remove / reapply** (`PotionEffectListener`).
* [x] **Effect clear attribution** (milk, water bucket, etc.) — cause string tracked in `PotionEffectRemoved`.

### Implementation details

* Head vs feet block cells for submerged/wading; eye collision for suffocation (`PlayerEnvironment.kt`).

---

## Phase 6: Nitty-gritty analysis

**Goal:** Record non-PVP-related events to get more granular insight on combat encounters.

### Player economy

* [x] **Inventory keyframes and deltas** (`InventoryKeyframeListener`).
* [ ] **Crafting Lifecycle:** Record items crafted, items repaired (via anvil/crafting), and tool break events.
* [ ] **Food Tracking:** Record food items consumed, surface total hunger and saturation values of food item in UI.

### PvE combat tracking

* [ ] **Mob Tracking:** Track the positions of nearby mobs relative to players and record any mob deaths.

### Landmarks

* [ ] **Landmarks:** Record world spawns, constructed Nether and End Portals (event on construction completion).
* [x] **Structure enter/exit events**
* [ ] **Global structure first-visit map**
* [ ] **Respawn locations:** Record respawn locations per player (event on spawn set, removed from map when player changes spawn).

### Implementation details

* Will probably use a window to group together crafting events to prevent spamming the logs creating a crap ton of sticks.
* In UI, probably need to de-duplicate shared spawn locations. Also need to figure out how to handle broken beds/respawn anchors in UI.
* `EndPortalCompleted` already fired from `EndPortalListener` when portal frame is completed.

---

## Phase 7: Persistence

**Goal**: Actually persist the thousands of data points that this plugin collects.

* [ ] **SQLite Database:** Draft and implement asynchronous SQLite database to persist all collected reporting data to disk.
* [ ] **Operator Transparency:** Notify online operators when the SQLite database reporting fails to write.

### Implementation details

* Must be careful to implement the SQLite writes asynchronously to avoid blocking the main thread.
* The previous plugin periodically appended data from the reporting engine to disk (esp. for paths) so doing that again might be okay.

---

## Phase 8: Deathstreaks and kits

**Goal:** Give the hunters a bit of help if they're terrible or the operator wants to run "assassin"-style manhunts (i.e. hunters have crazy starting gear).

* [ ] **Deathstreaks:** Thresholds dictate what gear that a hunter gets on respawn.
* [ ] **Kits:** Hunters immediately given a set of gear on spawn and regain it on respawn.
* [ ] **Session Storage:** Persist session configuration settings to disk.

---

## Phase 9: Report web interface

**Goal:** Surface the thousands of data points that were collected onto a pretty web UI.

`// TODO: write a LOT of checkboxes`