# PeopleHunt Feature Roadmap

Roadmap to a feature-rich manhunt administration and reporting plugin for Paper on Minecraft 26.1.2.

Rewritten in Kotlin from the ground-up based on the monstrous codebase that was [the vibecoded Java version](https://github.com/ganyuke/peoplehunt-java). This go-around should be much smarter about doing everything.

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

* [ ] Acquired first iron ingot
* [ ] Crafted/Acquired a bucket
* [ ] Picked up water
* [ ] Picked up lava
* [x] Entered Nether
* [x] Entered Fortress
* [x] Entered Bastion
* [ ] Acquired first Ender Pearl
* [ ] Acquired first Blaze Rod
* [ ] Left the Nether
* [ ] Crafted first Eye of Ender
* [ ] Threw first Eye of Ender
* [ ] Entered Stronghold
* [ ] Completed End Portal
* [ ] Entered the End
* [ ] Destroyed first End Crystal
* [ ] Destroyed all End Crystals
* [ ] Brought the dragon to 50%
* [ ] Brought the dragon to 25%
* [ ] Brought the dragon to 10%
* [ ] Brought the dragon to 5%
* [ ] Slew the dragon / Had the dragon slain by someone/something else

### Implementation details

* Since a lot of these events can happen out of order or not at all, need to probably either listen for all of them at once.
* "Left the Nether" should probably be dependent on obtaining the Blaze Rod achievement, since otherwise leaving the Nether at all would award this.

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

### Persistence

* [ ] **SQLite Database:** Draft and implement asynchronous SQLite database to 

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
* [ ] Crafted/Acquired a bucket
