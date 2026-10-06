package app.cove.companion.design

import app.cove.companion.design.illustrations.BannerMode
import app.cove.companion.design.illustrations.Scene
import app.cove.companion.design.illustrations.sceneForHour
import app.cove.companion.design.illustrations.todayBannerMode
import org.junit.Test
import org.junit.Assert.assertEquals

class TimeOfDayTest {
    @Test
    fun hoursMapToScenes() {
        assertEquals(Scene.Night, sceneForHour(0))
        assertEquals(Scene.Night, sceneForHour(4))
        assertEquals(Scene.Morning, sceneForHour(5))
        assertEquals(Scene.Morning, sceneForHour(11))
        assertEquals(Scene.Afternoon, sceneForHour(12))
        assertEquals(Scene.Afternoon, sceneForHour(16))
        assertEquals(Scene.Evening, sceneForHour(17))
        assertEquals(Scene.Evening, sceneForHour(20))
        assertEquals(Scene.Night, sceneForHour(21))
        assertEquals(Scene.Night, sceneForHour(23))
    }

    @Test
    fun bannerCollapsesAsThePageFills() {
        assertEquals(BannerMode.Empty, todayBannerMode(0, nothingPlanned = true, offline = false))
        assertEquals(BannerMode.Full, todayBannerMode(4, nothingPlanned = false, offline = false))
        assertEquals(BannerMode.Strip, todayBannerMode(7, nothingPlanned = false, offline = false))
        assertEquals(BannerMode.Hidden, todayBannerMode(9, nothingPlanned = false, offline = false))
        assertEquals(BannerMode.Hidden, todayBannerMode(0, nothingPlanned = true, offline = true))
    }
}
