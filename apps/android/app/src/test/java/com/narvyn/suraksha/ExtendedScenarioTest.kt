package com.narvyn.suraksha
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ExtendedScenarioTest {
    @Test fun allFiveModulesCompleteRestoreAndRemainNonCertifying(){
        assertEquals(setOf("fire","gas","machinery","ppe","emergency"),ProcedureCatalog.modules.keys)
        for(module in ProcedureCatalog.modules.keys){var session=ProcedureSession.create(module,"learner",false);assertTrue(session.definition.steps.size>=8)
            while(!session.done){assertTrue(session.choose(session.step.id,"screen",session.data.getLong("updatedAt")+1));assertTrue(session.advance());session=ProcedureSession.restore(JSONObject(session.data.toString()))}
            assertTrue(session.data.getJSONObject("result").getBoolean("complete"));assertFalse(session.data.getJSONObject("result").getBoolean("certifiable"))
        }
    }
    @Test fun unsafeIndependentActionStopsAndGuidedRetryPreservesMistake(){for(module in listOf("machinery","ppe","emergency")){
        val check=ProcedureSession.create(module,"learner",false);assertTrue(check.choose(check.step.id+"-unsafe","description",check.data.getLong("updatedAt")+1));assertTrue(check.done);assertTrue(check.data.getJSONObject("result").getBoolean("stopped"))
        val guided=ProcedureSession.create(module,"learner",true);guided.choose(guided.step.id+"-unsafe","description",guided.data.getLong("updatedAt")+1);guided.advance();assertFalse(guided.done);assertEquals(0,guided.index);assertEquals(1,guided.data.getJSONArray("criticalFailures").length())
    }}
    @Test fun legacySnapshotsKeepTheirOriginalSequence(){val old=JSONObject().put("schemaVersion",1).put("catalogVersion",1).put("id",java.util.UUID.randomUUID().toString()).put("workerId","learner").put("module","fire").put("guided",true).put("createdAt",10L).put("updatedAt",10L).put("index",0).put("finished",false).put("feedback",false).put("lastCorrect",false).put("flags",JSONObject()).put("events",org.json.JSONArray()).put("criticalFailures",org.json.JSONArray());val restored=ProcedureSession.restore(old);assertEquals(12,restored.definition.steps.size);assertEquals(16,ProcedureCatalog.modules.getValue("fire").steps.size)}
}
