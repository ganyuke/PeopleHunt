package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.*
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportDocument
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.ReportJson
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.pos
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

class JsonReplaySerializerTest {

    private val json: Json = ReportJson.instance

    private inline fun <reified T> roundTrip(value: T): T {
        val encoded = json.encodeToString(value)
        return json.decodeFromString(encoded)
    }

    // -------------------------------------------------------------------------
    // PARAMETERIZED: EVERY REPORTABLE PAYLOAD SERIALIZER EXISTS
    // -------------------------------------------------------------------------

    companion object {
        @JvmStatic
        fun providePayloadClasses(): List<KClass<out ReportablePayload>> =
            ReportablePayload::class.sealedSubclasses

        @JvmStatic
        fun providePayloadInstances(): List<Pair<String, ReportablePayload>> {
            val p = MatchPlayer(Uuid.parse("11111111-1111-1111-1111-111111111111"), "test")
            val pos = Pos4(0, 64, 0, Uuid.parse("22222222-2222-2222-2222-222222222222"))
            val vel = Velocity(0.0, 0.0, 0.0)
            val now = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())
            return listOf(
                "PlayerMoved" to ReportablePayload.PlayerMovedByBlock(p, pos, false),
                "PlayerRespawned" to ReportablePayload.PlayerRespawned(p, pos),
                "TeleportSnapshot" to ReportablePayload.TeleportSnapshot(p, pos, pos, TeleportCause.ENDER_PEARL),
                "PlayerGameModeChanged" to ReportablePayload.PlayerGameModeChanged(p, "SURVIVAL", "CREATIVE"),
                "PlayerConnected" to ReportablePayload.PlayerConnected(p, pos),
                "PlayerDisconnected" to ReportablePayload.PlayerDisconnected(p, pos),
                "InventoryKeyframe" to ReportablePayload.InventoryKeyframe(p, listOf("minecraft:diamond_sword"), 0),
                "InventoryDelta" to ReportablePayload.InventoryDelta(p, 4, "minecraft:cooked_beef"),
                "MainHandChanged" to ReportablePayload.MainHandChanged(p, 0),
                "PlayerEnteredStructure" to ReportablePayload.PlayerEnteredStructure(p, "minecraft:fortress", pos),
                "PlayerExitedStructure" to ReportablePayload.PlayerExitedStructure(p, "minecraft:fortress"),
                "PlayerEnteredFluid" to ReportablePayload.PlayerEnteredFluid(p, FluidState.SubmergedInWater(now)),
                "PlayerExitedFluid" to ReportablePayload.PlayerExitedFluid(p, FluidState.InLava(now)),
                "PlayerHealthRegained" to ReportablePayload.PlayerHealthRegained(p, 20.0, 20.0, "CUSTOM"),
                "EntityHealthRegained" to ReportablePayload.EntityHealthRegained(Uuid.random(), "minecraft:ender_dragon", 200.0, 300.0, "CRYSTAL"),
                "PlayerHungerChanged" to ReportablePayload.PlayerHungerChanged(p, 14, 3.2f, 0.5f),
                "PlayerBreathChanged" to ReportablePayload.PlayerBreathChanged(p, 280, 300),
                "PlayerXpChanged" to ReportablePayload.PlayerXpChanged(p, 7, 0.45f, 320),
                "EntityDied" to ReportablePayload.EntityDied("minecraft:zombie", pos, KillCause.KilledByPlayer(p)),
                "PlayerDied" to ReportablePayload.PlayerDied(p, pos, KillCause.Environmental, "fell"),
                "PlayerDamagedEntity" to ReportablePayload.PlayerDamagedEntity(p, "minecraft:zombie", 7.5, 12.5, null, "sword", null),
                "PlayerDamagedByEntity" to ReportablePayload.PlayerDamagedByEntity(p, "minecraft:creeper", 10.0, 10.0, null, null),
                "PlayerDamagedByEnvironment" to ReportablePayload.PlayerDamagedByEnvironment(p, "FALL", 6.0, 14.0),
                "ProjectileLaunched" to ReportablePayload.ProjectileLaunched(42, p, "arrow", "minecraft:arrow", pos, vel),
                "ProjectileMoved" to ReportablePayload.ProjectileMoved(42, pos, vel),
                "ProjectileHit" to ReportablePayload.ProjectileHit(42, p, "arrow", "minecraft:arrow", pos, "zombie", null, 6.0),
                "PlayerAcquiredItem" to ReportablePayload.PlayerAcquiredItem(p, SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD, SpeedrunMilestone.AcquisitionMethod.KILLED),
                "PlayerChangedDimension" to ReportablePayload.PlayerChangedDimension(p, "overworld", "the_nether"),
                "PlayerThrewEnderEye" to ReportablePayload.PlayerThrewEnderEye(p),
                "PlayerFilledBucket" to ReportablePayload.PlayerFilledBucket(p, "water"),
                "DragonSnapshot" to ReportablePayload.DragonSnapshot(pos, 150.0, 300.0),
                "EndCrystalDiscovered" to ReportablePayload.EndCrystalDiscovered(pos, 99),
                "EndPortalCompleted" to ReportablePayload.EndPortalCompleted(pos),
                "PotionEffectApplied" to ReportablePayload.PotionEffectApplied(p, "minecraft:speed", 1, 400, "arrow", false),
                "PotionEffectRemoved" to ReportablePayload.PotionEffectRemoved(p, "minecraft:speed", "EXPIRED"),
                "PlayerSnapshotChanged" to ReportablePayload.PlayerSnapshotChanged(p, PlayerSnapshot.Offline),
                "PlayerJoined" to ReportablePayload.PlayerJoined(p),
                "PlayerQuit" to ReportablePayload.PlayerQuit(p, "QUIT"),
                "PlayerCraftedItem" to ReportablePayload.PlayerCraftedItem(p, "minecraft:diamond_sword", 1),
                "PlayerRepairedItem" to ReportablePayload.PlayerRepairedItem(p, "minecraft:netherite_pickaxe"),
                "PlayerItemBroke" to ReportablePayload.PlayerItemBroke(p, "minecraft:iron_sword"),
                "PlayerConsumedItem" to ReportablePayload.PlayerConsumedItem(p, "minecraft:cooked_beef", 8, 12.8f),
                "NearbyMobs" to ReportablePayload.NearbyMobs(p, listOf(MobSnapshot(Uuid.random(), pos, "minecraft:zombie", 10.0, 5.2))),
                "NetherPortalCreated" to ReportablePayload.NetherPortalCreated(pos),
                "WorldSpawnRecorded" to ReportablePayload.WorldSpawnRecorded(pos),
                "PlayerSetSpawn" to ReportablePayload.PlayerSetSpawn(p, pos, "BED"),
                "MilestoneUnlocked" to ReportablePayload.MilestoneUnlocked(p, SpeedrunMilestone.EnteredNether),
                "ProjectileLaunched (no shooter)" to ReportablePayload.ProjectileLaunched(43, null, null, "minecraft:arrow", pos, vel),
                "ProjectileHit (block, no player)" to ReportablePayload.ProjectileHit(43, null, null, "minecraft:arrow", pos, null, null, 0.0),
                "PlayerDamagedEntity (all nulls)" to ReportablePayload.PlayerDamagedEntity(p, "minecraft:zombie", 7.5, null, null, null, null),
                "PlayerDamagedByEntity (all nulls)" to ReportablePayload.PlayerDamagedByEntity(p, "minecraft:creeper", 10.0, null, null, null),
                "EntityDied (no weapon, no projectile)" to ReportablePayload.EntityDied("minecraft:zombie", pos, KillCause.Unknown, null, null),
                "PlayerDied (no death message)" to ReportablePayload.PlayerDied(p, pos, KillCause.Environmental, null)
            )
        }

        @JvmStatic
        fun provideSpeedrunMilestones(): List<Pair<String, SpeedrunMilestone>> = listOf(
            "PickedUpWater" to SpeedrunMilestone.PickedUpWater,
            "PickedUpLava" to SpeedrunMilestone.PickedUpLava,
            "EnteredNether" to SpeedrunMilestone.EnteredNether,
            "EnteredFortress" to SpeedrunMilestone.EnteredFortress,
            "EnteredBastion" to SpeedrunMilestone.EnteredBastion,
            "LeftNether" to SpeedrunMilestone.LeftNether,
            "ThrewEyeOfEnder" to SpeedrunMilestone.ThrewEyeOfEnder,
            "EnteredStronghold" to SpeedrunMilestone.EnteredStronghold,
            "CompletedEndPortal" to SpeedrunMilestone.CompletedEndPortal,
            "EnteredEnd" to SpeedrunMilestone.EnteredEnd,
            "DestroyedFirstEndCrystal" to SpeedrunMilestone.DestroyedFirstEndCrystal,
            "DestroyedAllEndCrystals" to SpeedrunMilestone.DestroyedAllEndCrystals,
            "DragonAt50Percent" to SpeedrunMilestone.DragonAt50Percent,
            "DragonAt25Percent" to SpeedrunMilestone.DragonAt25Percent,
            "DragonAt10Percent" to SpeedrunMilestone.DragonAt10Percent,
            "DragonAt5Percent" to SpeedrunMilestone.DragonAt5Percent,
            "ItemAcquired" to SpeedrunMilestone.ItemAcquired(SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD, SpeedrunMilestone.AcquisitionMethod.KILLED),
            "DragonSlain.ByRunner" to SpeedrunMilestone.DragonSlain.ByRunner,
            "DragonSlain.ByOther" to SpeedrunMilestone.DragonSlain.ByOther("minecraft:ender_dragon"),
        )
    }

    @ParameterizedTest
    @MethodSource("providePayloadClasses")
    fun `every sealed subclass of ReportablePayload has a compiler-generated serializer`(payloadClass: KClass<out ReportablePayload>) {
        val s = json.serializersModule.serializer(payloadClass.java)
        assertNotNull(s, "Serializer for ${payloadClass.simpleName} should not be null")
    }

    @ParameterizedTest
    @MethodSource("providePayloadInstances")
    fun `every ReportablePayload round-trips through JSON`(pair: Pair<String, ReportablePayload>) {
        val (name, payload) = pair
        val encoded = json.encodeToString(payload)
        val decoded = json.decodeFromString<ReportablePayload>(encoded)
        assertEquals(payload, decoded, "Round-trip failed for $name")
    }

    @ParameterizedTest
    @MethodSource("provideSpeedrunMilestones")
    fun `every SpeedrunMilestone variant round-trips through JSON`(pair: Pair<String, SpeedrunMilestone>) {
        val (name, milestone) = pair
        val encoded = json.encodeToString(milestone)
        val decoded = json.decodeFromString<SpeedrunMilestone>(encoded)
        assertEquals(milestone, decoded, "Round-trip failed for SpeedrunMilestone.$name")
    }

    // -------------------------------------------------------------------------
    // SPECIALIZED SERIALIZERS: UUID
    // -------------------------------------------------------------------------

    private fun testInstant() = Instant.fromEpochMilliseconds(Clock.System.now().toEpochMilliseconds())

    @Test
    fun uuidSerializer_serializesAsString() {
        val uuid = Uuid.random()
        val doc = ReportDocument(
            matchId = uuid, startedAt = testInstant(),
            runner = MatchPlayer(uuid, "runner"), hunters = emptyList(),
            durationTicks = 0, projectiles = emptyList(), snapshots = emptyList(), events = emptyList(),
        )
        val encoded = json.encodeToString(doc)
        assertTrue(encoded.contains("\"${uuid.toString().replace("-", "")}\""), "UUID should appear as compact string in JSON")
    }

    @Test
    fun uuidSerializer_roundTrip() {
        val uuid = Uuid.random()
        val doc = ReportDocument(
            matchId = uuid, startedAt = testInstant(),
            runner = MatchPlayer(uuid, "runner"), hunters = emptyList(),
            durationTicks = 0, projectiles = emptyList(), snapshots = emptyList(), events = emptyList(),
        )
        val decoded = roundTrip(doc)
        assertEquals(uuid, decoded.runner.uuid)
    }

    // -------------------------------------------------------------------------
    // SPECIALIZED SERIALIZERS: INSTANT
    // -------------------------------------------------------------------------

    @Test
    fun instantSerializer_serializesAsEpochMillis() {
        val instant = testInstant()
        val doc = ReportDocument(
            matchId = Uuid.random(), startedAt = instant,
            runner = player(), hunters = emptyList(),
            durationTicks = 0, projectiles = emptyList(), snapshots = emptyList(), events = emptyList(),
        )
        val encoded = json.encodeToString(doc)
        assertTrue(encoded.contains(instant.toEpochMilliseconds().toString()), "Instant should appear as epoch millis in JSON")
    }

    @Test
    fun instantSerializer_roundTrip() {
        val instant = testInstant()
        val doc = ReportDocument(
            matchId = Uuid.random(), startedAt = instant,
            runner = player(), hunters = emptyList(),
            durationTicks = 0, projectiles = emptyList(), snapshots = emptyList(), events = emptyList(),
        )
        val decoded = roundTrip(doc)
        assertEquals(instant, decoded.startedAt)
    }

    // -------------------------------------------------------------------------
    // REPLAY FRAME & DOCUMENT
    // -------------------------------------------------------------------------

    @Test
    fun replayFrame_roundTrip() {
        val p = player()
        val now = testInstant()
        val frame = EventFrame(
            tick = 100, occurredAt = now,
            payload = ReportablePayload.PlayerMovedByBlock(
                player = p, pos = pos(), isSneaking = false,
            ),
        )
        val decoded = roundTrip(frame)
        assertEquals(frame.tick, decoded.tick)
        assertEquals(frame.occurredAt, decoded.occurredAt)
        assertEquals(frame.payload, decoded.payload)
    }

    @Test
    fun replayDocument_roundTrip() {
        val p = player()
        val hunter = player("hunter")
        val now = testInstant()
        val frame = EventFrame(
            tick = 50, occurredAt = now,
            payload = ReportablePayload.PlayerDied(
                player = p, pos = pos(), cause = KillCause.KilledByPlayer(hunter), deathMessage = "was slain",
            ),
        )
        val matchId = Uuid.parse("550e8400-e29b-41d4-a716-446655440000")
        val doc = ReportDocument(
            matchId = matchId,
            startedAt = now, runner = p, hunters = listOf(hunter),
            durationTicks = 50, projectiles = emptyList(), snapshots = emptyList(), events = listOf(frame),
        )
        val decoded = roundTrip(doc)
        assertEquals(doc.matchId, decoded.matchId)
        assertEquals(doc.startedAt, decoded.startedAt)
        assertEquals(doc.runner, decoded.runner)
        assertEquals(doc.hunters, decoded.hunters)
        assertEquals(doc.durationTicks, decoded.durationTicks)
        assertEquals(1, decoded.events.size)
        assertEquals(doc.events[0].payload, decoded.events[0].payload)
    }

    @Test
    fun replayDocument_emptyFrames_roundTrip() {
        val doc = ReportDocument(
            matchId = Uuid.random(), startedAt = testInstant(),
            runner = player(), hunters = emptyList(),
            durationTicks = 0, projectiles = emptyList(), snapshots = emptyList(), events = emptyList(),
        )
        val decoded = roundTrip(doc)
        assertEquals(doc.matchId, decoded.matchId)
        assertTrue(decoded.events.isEmpty())
        assertTrue(decoded.hunters.isEmpty())
    }
}
