package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test
import com.narvyn.suraksha.RoomStationPlacement.Reason
import com.narvyn.suraksha.RoomStationPlacement.Station
import kotlin.math.cos
import kotlin.math.sin

class RoomStationPlacementTest {
    private val equipment = Station(-3f, 0f)
    private val hazard = Station(0f, 0f)
    private fun decision(module: String, existing: List<Station>, candidate: Station) =
        RoomStationPlacement.evaluate(module, existing, candidate)

    @Test fun firstStationAndExistingSeparationRulesRemainAvailable() {
        assertTrue(decision("fire", emptyList(), Station(0f, 0f)).allowed)
        assertEquals(Reason.TOO_CLOSE, decision("gas", listOf(hazard), Station(.5f, 0f)).reason)
        assertTrue(decision("gas", listOf(hazard), Station(.501f, 0f)).allowed)
        assertEquals(Reason.TOO_CLOSE, decision("fire", listOf(equipment, hazard), Station(0f, 1f)).reason)
        assertTrue(decision("fire", listOf(equipment, hazard), Station(0f, 1.01f)).allowed)
        assertEquals(Reason.TOO_CLOSE, decision("fire", listOf(equipment, hazard), Station(-3f, .5f)).reason)
    }

    @Test fun gasRearPointCannotBeCalledOutsideEvenBeyondTheOldRadialLimit() {
        val rear = Station(0f, -1.01f)
        assertTrue(decision("fire", listOf(equipment, hazard), rear).allowed)
        val result = decision("gas", listOf(equipment, hazard), rear)
        assertFalse(result.allowed)
        assertEquals(Reason.GAS_WRONG_SIDE, result.reason)
        assertEquals(Reason.GAS_WRONG_SIDE, decision("gas", listOf(equipment, hazard), Station(2f, 0f)).reason)
    }

    @Test fun gasGreenFootprintMustRemainEntirelyOnTheOutsideSide() {
        assertEquals(Reason.GAS_FOOTPRINT_OVERLAP,
            decision("gas", listOf(equipment, hazard), Station(1.2f, .2f)).reason)
        assertEquals(Reason.GAS_FOOTPRINT_OVERLAP,
            decision("gas", listOf(equipment, hazard), Station(1.2f, .45f)).reason)
        assertTrue(decision("gas", listOf(equipment, hazard), Station(1.2f, .451f)).allowed)
        assertTrue(decision("gas", listOf(equipment, hazard), Station(0f, 1.01f)).allowed)
    }

    @Test fun gasOutsideDecisionUsesHazardYawAndTranslation() {
        for (yaw in listOf(0f, 90f, -90f, 180f, 37f, 397f)) {
            val origin = Station(4f, -2f, yaw)
            fun world(x: Float, z: Float): Station {
                val angle = Math.toRadians(yaw.toDouble())
                return Station((origin.x + cos(angle) * x + sin(angle) * z).toFloat(),
                    (origin.z - sin(angle) * x + cos(angle) * z).toFloat())
            }
            val existing = listOf(world(-3f, 0f), origin)
            assertTrue("front yaw=$yaw", decision("gas", existing, world(0f, 1.1f)).allowed)
            assertEquals("rear yaw=$yaw", Reason.GAS_WRONG_SIDE, decision("gas", existing, world(0f, -1.01f)).reason)
            assertEquals("footprint yaw=$yaw", Reason.GAS_FOOTPRINT_OVERLAP,
                decision("gas", existing, world(1.2f, .2f)).reason)
        }
    }

    @Test fun invalidCoordinatesOrModulesCannotAuthorizePlacement() {
        for (invalid in listOf(Station(Float.NaN, 0f), Station(0f, Float.POSITIVE_INFINITY), Station(0f, 0f, Float.NEGATIVE_INFINITY))) {
            assertEquals(Reason.INVALID_INPUT, decision("fire", emptyList(), invalid).reason)
            assertEquals(Reason.INVALID_INPUT, decision("gas", listOf(invalid), Station(3f, 3f)).reason)
        }
        assertEquals(Reason.INVALID_INPUT, decision("unknown", emptyList(), hazard).reason)
    }

    @Test fun fullMissionCannotAcceptAnotherStationAndFiniteLargeCoordinatesDoNotOverflow() {
        assertEquals(Reason.ALL_STATIONS_PLACED,
            decision("gas", listOf(equipment, hazard, Station(0f, 2f)), Station(0f, 3f)).reason)
        assertTrue(decision("fire", listOf(Station(-Float.MAX_VALUE, 0f)), Station(Float.MAX_VALUE, 0f)).allowed)
    }
}
