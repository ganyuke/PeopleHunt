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
* Exepected runner to be a single player only.
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

* Structure events are implemented directly in the ReportingEngine; they piggy-back off the PlayerMoved event and lookup the structure they are standing in. Requires an adapter for the structure lookup. Not sure if this'll cause problems if there are easier ways to do it in something like Fabric.
* Left the Nether is dependent on obtaining the Blaze Rod achievement, since otherwise leaving the Nether at all would award this.
* Eye of Ender thrown detection is a bit of a hack. Will also fire if another player throws an eye while standing near the runner.
* Ender Portal completion is a bit of a hack relying on checking if the placed Eye of Ender will complete the End Portal. Had to steal [a bit of code from Cooperative End Access](https://github.com/ganyuke/CooperativeEndAccess/blob/main/src/main/java/io/github/ganyuke/cooperativeEndAccess/portal/PortalListener.java) to get this to work.

---

## Phase 3: Core player tracking

**Goal:** Implement path recording for participants in the manhunt match.

### Core path recorder

* [ ] **Player Position:** Capture coordinates, world/dimension, and handle discontinuities + filter out minor teleport noises (e.g. vanilla push-out mechanics).
* [ ] **Teleportation:** Record distinct teleportation causes (Ender Pearl, commands, etc.).
* [ ] **Player Status:** Record basic movement states (swimming, flying, falling, walking, running) and active game mode.
* [ ] **Player Vitals:** Poll health, absorption, hunger, saturation, breath levels, XP levels.

### Combat and damage tracking

* [ ] **Player Damage:** Record exact damage and attribute cause (explosions, fall damage, lava, etc.) back to a specific player or entity.
* [ ] **Projectiles:** Record projectile paths, entity types, and resolve projectile owner.

---

## Phase 4: End fight progress tracking

**Goal:** Watch the path of the dragon on the map and watch its health gradually decline.

* [ ] **Dragon Position:** Poll position.
* [ ] **Dragon Vitals:** Poll health.
* [ ] **Dragon Damage:** Record damage events and attribute to player/entity/event.
* [ ] **Killing Blow:** Record who or what dealt the killing blow to the dragon, when, where, and how.
* [ ] **End Crystal Tracking:** Record crystal positions, their destruction events, and attribute who blew them up.

---

## Phase 5: External status effects tracking

**Goal:** Record damage and status afflicting players.

* [ ] **Environmental Effects:** Record swimming in lava, suffocation, and drowning.
* [ ] **Potion Effects:** Record potion types, remaining durations, re-applications.
* [ ] **Effect Clear Attribution:** Record how effect was lost (e.g. burning put out by water bucket / extinguished normally, poison cleared by drinking milk).

### Implementation details

* Will probably track "drowning" and "suffocation" as first and last tick within window.

---

## Phase 6: Nitty-gritty analysis

**Goal:** Record non-PVP-related events to get more granular insight on combat encounters.

### Player economy

* [ ] **Inventory Tracking:** Record full inventory, including armor, offhand items, item durabilities, item rarities, enchantments.
* [ ] **Crafting Lifecycle:** Record items crafted, items repaired (via anvil/crafting), and tool break events.
* [ ] **Food Tracking:** Record food items consumed, surface total hunger and saturation values of food item in UI.

### PvE combat tracking

* [ ] **Mob Tracking:** Track the positions of nearby mobs relative to players and record any mob deaths.

### Landmarks

* [ ] **Landmarks:** Record world spawns, constructed Nether and End Portals (event on construction completion).
* [ ] **Structures:** Record locations of naturally-generated structures that players enter (event on first step in globally, not per player).
* [ ] **Respawn locations:** Record respawn locations per player (event on spawn set, removed from map when player changes spawn).

### Implementaiton details

* Will probably use a window to group together crafting events to prevent spamming the logs creating a crap ton of sticks.
* In UI, probably need to de-duplicate shared spawn locations. Also need to figure out how to handle broken beds/respawn anchors in UI.

---

## Phase 7: Persistence

**Goal**: Actually persist the thousands of data points that this plugin collects.

* [ ] **SQLite Database:** Draft and implement asynchronous SQLite database to persist all collected reporting data to disk.
* [ ] **Operator Transparency:** Notify online operators when the SQLite database reporting fails to write.

## Implementation details

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