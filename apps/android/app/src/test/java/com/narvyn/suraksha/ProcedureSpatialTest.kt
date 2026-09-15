package com.narvyn.suraksha

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProcedureSpatialTest {
    private val identity=FloatArray(16) { if(it%5==0)1f else 0f }
    private fun proof(action:String):ProcedureSpatial.Proof {
        val hold=ProcedureSpatialHold()
        var result:ProcedureSpatial.Proof?=null
        for(t in 0L..700L step 50) { result=hold.sample(ProcedureSpatial.Hit(action,.2f),1000+t)?:result }
        return result!!
    }
    private fun session(step:String,guided:Boolean=true):ProcedureSession {
        val s=ProcedureSession.create(if(step.startsWith("fire"))"fire"else"gas","worker",guided)
        while(s.step.id!=step) { s.choose(s.step.id,"description",s.data.getLong("updatedAt")+1);s.advance() }
        return s
    }
    private fun invalid(block:()->Unit) { try { block();fail("Expected invalid evidence") }catch(_:IllegalArgumentException){} }

    @Test fun rayPickingUsesNearestGeometryAndRejectsMissesAndInvalidRays() {
        val near=ProcedureSpatial.Zone("near",floatArrayOf(0f,0f,-.5f),.1f)
        val far=ProcedureSpatial.Zone("far",floatArrayOf(0f,0f,.5f),.1f)
        assertEquals("near",ProcedureSpatial.hit(100f,100f,200,200,identity,listOf(far,near))!!.id)
        assertNull(ProcedureSpatial.hit(180f,100f,200,200,identity,listOf(near)))
        assertNull(ProcedureSpatial.hit(Float.NaN,100f,200,200,identity,listOf(near)))
        assertNull(ProcedureSpatial.hit(-1f,100f,200,200,identity,listOf(near)))
        assertNull(ProcedureSpatial.hit(100f,100f,200,200,FloatArray(16),listOf(near)))
        assertNull(ProcedureSpatial.hit(100f,100f,0,200,identity,listOf(near)))
        assertNull(ProcedureSpatial.hit(100f,100f,200,200,identity,listOf(near.copy(center=floatArrayOf(0f,0f,-2f)))) )
    }
    @Test fun translatedRayStillSelectsTheSameLocalTarget() {
        val translated=identity.copyOf().apply { this[12]=.4f;this[13]=-.2f }
        val zone=ProcedureSpatial.Zone("local",floatArrayOf(.4f,-.2f,0f),.1f)
        assertEquals("local",ProcedureSpatial.hit(50f,50f,100,100,translated,listOf(zone))!!.id)
        assertNull(ProcedureSpatial.hit(50f,50f,100,100,identity,listOf(zone)))
    }
    @Test fun holdRequiresContinuousFreshSamplesNotRepeatedFramesOrADeadline() {
        val hold=ProcedureSpatialHold();val hit=ProcedureSpatial.Hit("fire-aim",0f)
        repeat(200) { assertNull(hold.sample(hit,1000)) }
        assertNull(hold.sample(hit,1800)) // stale interval restarts instead of finishing
        for(t in 1850L..2400L step 50)assertNull(hold.sample(hit,t))
        assertNotNull(hold.sample(hit,2450))
    }
    @Test fun missSwitchInvalidErrorAndExplicitResetDiscardIncompleteHold() {
        for(reset in 0..4) {
            val h=ProcedureSpatialHold();val hit=ProcedureSpatial.Hit("fire-aim",.1f)
            for(t in 1000L..1500L step 50)assertNull(h.sample(hit,t))
            when(reset) {
                0->h.sample(null,1550)
                1->h.sample(hit.copy(id="fire-aim-unsafe"),1550)
                2->h.sample(hit.copy(error=Float.NaN),1550)
                3->h.reset()
                else->h.sample(hit,999)
            }
            assertNull(h.sample(hit,1600));assertNull(h.sample(hit,1650))
        }
    }
    @Test fun spatialEvidenceRoundTripsAndDoesNotBecomePracticalCertification() {
        val s=session("fire-aim")
        assertTrue(s.choose(s.step.id,"screen",s.data.getLong("updatedAt")+1,proof(s.step.id).json()))
        assertEquals(s.data.toString(),ProcedureSession.restore(s.data).data.toString())
        val event=s.data.getJSONArray("events").getJSONObject(s.data.getJSONArray("events").length()-1)
        assertTrue(event.has("spatial"));assertEquals("screen",event.getString("presentation"))
        s.advance()
        while(!s.done) { s.choose(s.step.id,"description",s.data.getLong("updatedAt")+1);s.advance() }
        assertFalse(s.data.getJSONObject("result").getBoolean("certifiable"))
        assertEquals("not-assessed",s.data.getJSONObject("result").getString("practical"))
    }
    @Test fun unsafeGasPositionStopsIndependentAttemptAndRecordsTheSpatialSelection() {
        val s=session("gas-attendant",false);val action="gas-attendant-unsafe"
        assertTrue(s.choose(action,"camera",s.data.getLong("updatedAt")+1,proof(action).json()))
        assertTrue(s.done);assertTrue(s.data.getJSONObject("result").getBoolean("stopped"))
        assertFalse(s.data.getJSONObject("flags").has("gas-attendant"))
        assertTrue(ProcedureSession.restore(s.data).done)
    }
    @Test fun fabricatedOrMisattributedSpatialProofCannotBeReplayed() {
        val s=session("fire-aim");val valid=proof("fire-aim").json();val now=s.data.getLong("updatedAt")+1
        invalid { s.choose("fire-aim","description",now,valid) }
        invalid { s.choose("fire-aim-unsafe","camera",now,valid) }
        invalid { val other=session("fire-label");other.choose("fire-label","screen",other.data.getLong("updatedAt")+1,valid) }
        for(mutation in 0..4) {
            val damaged=JSONObject(valid.toString());val sample=damaged.getJSONArray("samples").getJSONObject(1)
            when(mutation) { 0->sample.put("elapsedMs",900);1->sample.put("normalisedError",1.2);2->damaged.put("version",2);3->sample.put("elapsedMs",0);else->damaged.getJSONArray("samples").remove(damaged.getJSONArray("samples").length()-1) }
            invalid { s.choose("fire-aim","screen",now,damaged) }
        }
        assertFalse(s.feedback)
        s.choose("fire-aim","screen",now,valid)
        val saved=JSONObject(s.data.toString());val events=saved.getJSONArray("events")
        events.getJSONObject(events.length()-1).getJSONObject("spatial").put("action","fire-aim-unsafe")
        invalid { ProcedureSession.restore(saved) }
    }
    @Test fun olderButtonJournalsRemainUnchangedAndContainNoSpatialCredit() {
        val s=session("fire-aim");s.choose(s.step.id,"camera",s.data.getLong("updatedAt")+1)
        assertEquals(s.data.toString(),ProcedureSession.restore(s.data).data.toString())
        assertTrue(s.data.getJSONArray("events").objects().none { it.has("spatial") })
    }
}
