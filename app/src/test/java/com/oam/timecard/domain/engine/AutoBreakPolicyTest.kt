package com.oam.timecard.domain.engine

import com.oam.timecard.domain.model.RulePresets
import com.oam.timecard.domain.model.RuleSet
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoBreakPolicyTest {
    private val rule = RuleSet(autoBreak = mins(30), autoBreakAfter = hours(6.0))

    private fun deduct(grossH: Double, recordedMin: Long, r: RuleSet = rule) =
        AutoBreakPolicy.toDeduct(hours(grossH), mins(recordedMin), r)

    @Test fun overThreshold_noBreak_deductsFull() = assertEquals(mins(30), deduct(6.5, 0))
    @Test fun exactlyAtThreshold_deductsNothing() = assertEquals(Duration.ZERO, deduct(6.0, 0))
    @Test fun partialRecordedBreak_topsUp() = assertEquals(mins(20), deduct(7.0, 10))
    @Test fun recordedBreakLongerThanRule_deductsNothing() = assertEquals(Duration.ZERO, deduct(7.0, 40))
    @Test fun thresholdUsesWorkedTimeNotGross() = assertEquals(Duration.ZERO, deduct(6.0 + 20.0 / 60, 30))
    @Test fun presetWithoutAutoBreak_deductsNothing() = assertEquals(Duration.ZERO, deduct(12.0, 0, RulePresets.US_FEDERAL))
}
