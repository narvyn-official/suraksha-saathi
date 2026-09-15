package com.narvyn.suraksha

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RoomMissionStoreTest {
    private fun isolated(block: (Context, String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "room-mission-test-${UUID.randomUUID()}.db"
        try { block(context, name) } finally { context.deleteDatabase(name) }
    }

    private fun record(worker: String, module: String = "fire", mode: String = "screen", updated: Long = 100L) =
        JSONObject().put("id", UUID.randomUUID().toString()).put("workerId", worker)
            .put("module", module).put("mode", mode).put("updatedAt", updated)
            .put("mission", RoomMission(module).toJson())

    private fun rejected(block: () -> Unit) {
        try { block(); fail("Invalid mission write was accepted") } catch (_: IllegalArgumentException) { }
    }

    private fun cue(phase: String, at: Any) = JSONObject().put("phase", phase).put("at", at)
    private fun coaching(mode: String = "recall", vararg cues: JSONObject) =
        JSONObject().put("version", 1).put("mode", mode).put("cues", JSONArray().apply { cues.forEach { put(it) } })

    @Test fun workerJournalsStaySeparateAndPersistAfterHelpersClose() = isolated { context, name ->
        val first = record("worker-one", "fire", "camera")
        val second = record("worker-two", "gas", "screen")
        RoomMissionStore(context, "worker-one", name).use { a ->
            RoomMissionStore(context, "worker-two", name).use { b ->
                a.save(first); assertTrue(b.records().isEmpty())
                b.save(second)
                assertEquals(first.toString(), a.records().single().toString())
                assertEquals(second.toString(), b.records().single().toString())
            }
        }
        RoomMissionStore(context, "worker-one", name).use { assertEquals(first.toString(), it.records().single().toString()) }
        RoomMissionStore(context, "worker-two", name).use { assertEquals(second.toString(), it.records().single().toString()) }
    }

    @Test fun ownerMismatchAndReboundIdCannotReplaceExistingEvidence() = isolated { context, name ->
        val first = record("worker-one")
        RoomMissionStore(context, "worker-one", name).use { a ->
            RoomMissionStore(context, "worker-two", name).use { b ->
                a.save(first)
                rejected { b.save(first) }
                rejected { b.save(JSONObject(first.toString()).put("workerId", "worker-two")) }
                assertTrue(b.records().isEmpty())
                assertEquals(first.toString(), a.records().single().toString())
                // A rejected transactional collision must not poison the next legitimate write.
                b.save(record("worker-two")); assertEquals(1, b.records().size)
            }
        }
    }

    @Test fun modeAndModuleAreLockedForAnExistingMissionId() = isolated { context, name ->
        val original = record("worker-one", "fire", "screen")
        RoomMissionStore(context, "worker-one", name).use { store ->
            store.save(original)
            rejected { store.save(JSONObject(original.toString()).put("mode", "camera")) }
            val otherModule = record("worker-one", "gas", "screen").put("id", original.getString("id"))
            rejected { store.save(otherModule) }
            assertEquals(original.toString(), store.records().single().toString())
        }
    }

    @Test fun invalidScopeAndCertificationClaimsNeverReplaceTheSavedSnapshot() = isolated { context, name ->
        val original = record("worker-one")
        RoomMissionStore(context, "worker-one", name).use { store ->
            store.save(original)
            for (value in listOf<Any>(true, "false", JSONObject.NULL)) {
                val invalid = JSONObject(original.toString())
                invalid.getJSONObject("mission").getJSONObject("result").put("certifiable", value)
                rejected { store.save(invalid) }
            }
            val missing = JSONObject(original.toString()); missing.getJSONObject("mission").remove("result")
            rejected { store.save(missing) }
            val mismatched = JSONObject(original.toString()); mismatched.getJSONObject("mission").put("module", "gas")
            rejected { store.save(mismatched) }
            rejected { store.save(JSONObject(original.toString()).put("module", "unknown")) }
            rejected { store.save(JSONObject(original.toString()).put("mode", "description")) }
            rejected { store.save(JSONObject(original.toString()).put("updatedAt", -1)) }
            rejected { store.save(JSONObject(original.toString()).put("updatedAt", "100")) }
            assertEquals(original.toString(), store.records().single().toString())
        }
    }

    @Test fun sameOwnerAndModeCanAtomicallyReplaceAndReadDetachedSnapshots() = isolated { context, name ->
        val original = record("worker-one")
        val engine = RoomMission("fire"); assertTrue(engine.act("alarm", 0))
        val updated = JSONObject(original.toString()).put("updatedAt", 200L).put("mission", engine.toJson())
        RoomMissionStore(context, "worker-one", name).use { store ->
            store.save(original); store.save(updated)
            assertEquals(1, store.records().size)
            assertEquals(updated.toString(), store.records().single().toString())
            store.records().single().getJSONObject("mission").put("phase", "COMPLETE")
            assertEquals("PIN", store.records().single().getJSONObject("mission").getString("phase"))
            updated.getJSONObject("mission").put("phase", "COMPLETE")
            assertEquals("PIN", store.records().single().getJSONObject("mission").getString("phase"))
        }
        RoomMissionStore(context, "worker-one", name).use { reopened ->
            val saved = reopened.records().single()
            assertEquals(200L, saved.getLong("updatedAt"))
            assertEquals("PIN", saved.getJSONObject("mission").getString("phase"))
        }
    }

    @Test fun malformedCoachingMetadataCannotCreateRecords() = isolated { context, name ->
        val invalid = mutableListOf<Any>(JSONObject.NULL, "recall", JSONArray(), JSONObject(),
            coaching().put("version", "1"), coaching().put("version", 2), coaching().put("version", true),
            coaching().put("version", 1.25), coaching().put("mode", "exam"), coaching().put("mode", 1),
            coaching().put("cues", JSONObject()), coaching().put("cues", JSONObject.NULL),
            coaching().put("cues", JSONArray().put("ALARM")),
            coaching().put("extra", true), coaching("guided", cue("ALARM", 0)),
            coaching("recall", cue("COMPLETE", 0)), coaching("recall", cue("GAS_CHECK", 0)),
            coaching("recall", cue("ALARM", -1)), coaching("recall", cue("ALARM", "1")),
            coaching("recall", cue("ALARM", 1.25)), coaching("recall", cue("ALARM", true)),
            coaching("recall", cue("ALARM", JSONObject.NULL)),
            coaching("recall", cue("ALARM", 10), cue("PIN", 9)),
            coaching("recall", cue("ALARM", 0).put("extra", "changed")))
        for (key in listOf("version", "mode", "cues")) invalid.add(coaching().apply { remove(key) })
        for (key in listOf("phase", "at")) invalid.add(coaching("recall", cue("ALARM", 0).apply { remove(key) }))
        invalid.add(coaching().put("cues", JSONArray().apply { repeat(129) { put(cue("ALARM", it)) } }))
        RoomMissionStore(context, "worker-one", name).use { store ->
            for (metadata in invalid) {
                rejected { store.save(record("worker-one").put("coaching", metadata)) }
                assertTrue("Invalid metadata must not write a record: $metadata", store.records().isEmpty())
            }
            rejected { store.save(record("worker-one", "gas").put("coaching", coaching("recall", cue("ALARM", 0)))) }
            store.save(record("worker-one", "gas").put("coaching", coaching("recall", cue("GAS_CHECK", 0), cue("BARRIER", 0), cue("ATTENDANT", 1), cue("REFUSE", Long.MAX_VALUE))))
            assertEquals(1, store.records().size)
        }
    }

    @Test fun coachingModeAndSavedCuePrefixCannotBeRebound() = isolated { context, name ->
        val original = record("worker-one").put("coaching", coaching("recall", cue("ALARM", 10), cue("PIN", 20)))
        RoomMissionStore(context, "worker-one", name).use { store ->
            store.save(original)
            val replacements = listOf(
                coaching("guided"), coaching("recall"), coaching("recall", cue("ALARM", 10)),
                coaching("recall", cue("ALARM", 11), cue("PIN", 20)),
                coaching("recall", cue("AIM", 10), cue("PIN", 20)),
                coaching("recall", cue("PIN", 10), cue("ALARM", 20)))
            for (replacement in replacements) {
                rejected { store.save(JSONObject(original.toString()).put("coaching", replacement)) }
                assertEquals(original.toString(), store.records().single().toString())
            }
            rejected { store.save(JSONObject(original.toString()).apply { remove("coaching") }) }
            assertEquals(original.toString(), store.records().single().toString())
        }
    }

    @Test fun recalledCuesCanAppendAndRemainImmutableAfterReopening() = isolated { context, name ->
        val first = record("worker-one").put("coaching", coaching("recall", cue("ALARM", 10)))
        val appended = JSONObject(first.toString()).put("updatedAt", 200L)
            .put("coaching", coaching("recall", cue("ALARM", 10), cue("PIN", 10), cue("AIM", 30)))
        RoomMissionStore(context, "worker-one", name).use { store ->
            store.save(first);store.save(appended)
            assertEquals(appended.toString(), store.records().single().toString())
            appended.getJSONObject("coaching").getJSONArray("cues").getJSONObject(0).put("at", 99)
            assertEquals(10L, store.records().single().getJSONObject("coaching").getJSONArray("cues").getJSONObject(0).getLong("at"))
        }
        RoomMissionStore(context, "worker-one", name).use { reopened ->
            val saved = reopened.records().single()
            assertEquals(3, saved.getJSONObject("coaching").getJSONArray("cues").length())
            rejected { reopened.save(first) }
            val next = JSONObject(saved.toString())
            next.getJSONObject("coaching").getJSONArray("cues").put(cue("SWEEP", 40))
            reopened.save(next)
            assertEquals(4, reopened.records().single().getJSONObject("coaching").getJSONArray("cues").length())
        }
    }

    @Test fun legacyRecordsRemainGuidedAndCanReceiveExplicitGuidedMetadata() = isolated { context, name ->
        val legacy = record("worker-one")
        RoomMissionStore(context, "worker-one", name).use { it.save(legacy) }
        RoomMissionStore(context, "worker-one", name).use { store ->
            assertFalse(store.records().single().has("coaching"))
            val oldStyleUpdate = JSONObject(legacy.toString()).put("updatedAt", 150L)
            store.save(oldStyleUpdate)
            rejected { store.save(JSONObject(legacy.toString()).put("coaching", coaching("recall"))) }
            val explicitGuided = JSONObject(legacy.toString()).put("updatedAt", 200L).put("coaching", coaching("guided"))
            store.save(explicitGuided)
            assertEquals("guided", store.records().single().getJSONObject("coaching").getString("mode"))
            // A legacy-shaped update is the same guided/empty meaning, not removal of recorded cues.
            store.save(JSONObject(legacy.toString()).put("updatedAt", 300L))
            assertFalse(store.records().single().has("coaching"))
            val maximum = record("worker-one").put("coaching", coaching().put("cues", JSONArray().apply { repeat(128) { put(cue("ALARM", it)) } }))
            store.save(maximum)
            assertEquals(2, store.records().size)
        }
    }
}
