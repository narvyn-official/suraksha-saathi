package com.narvyn.suraksha

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
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
}
