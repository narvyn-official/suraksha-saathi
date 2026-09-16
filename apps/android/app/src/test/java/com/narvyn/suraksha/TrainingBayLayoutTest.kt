package com.narvyn.suraksha

import org.junit.Assert.*
import org.junit.Test

class TrainingBayLayoutTest {
    @Test fun bothLayoutsRespectExistingExclusionAndSpacingRules() {
        assertTrue(TrainingBayLayout.valid("fire"))
        assertTrue(TrainingBayLayout.valid("gas"))
        assertFalse(TrainingBayLayout.valid("unknown"))
    }
}
