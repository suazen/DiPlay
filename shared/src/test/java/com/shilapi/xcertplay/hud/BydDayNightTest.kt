package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BydDayNightTest {
    @Test
    fun twoIsNight() = assertEquals(true, BydDayNight.parse("daynight=2\n"))

    @Test
    fun oneIsDay() = assertEquals(false, BydDayNight.parse("daynight=1"))

    @Test
    fun zeroIsDay() = assertEquals(false, BydDayNight.parse("daynight=0"))

    @Test
    fun anErrorLineLeavesTheStateUnknown() =
        assertNull(BydDayNight.parse("daynightERR=NoSuchMethodException"))
}
