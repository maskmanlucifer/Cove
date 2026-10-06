package app.cove.companion.core

import org.junit.Test
import org.junit.Assert.assertEquals

class PermissionsTest {
    @Test fun grantedWins() = assertEquals(PermissionStep.Granted, permissionStep(true, true, false))
    @Test fun firstTimeAsks() = assertEquals(PermissionStep.Ask, permissionStep(false, false, true))
    @Test fun deniedWithRationaleAsksAgain() = assertEquals(PermissionStep.Ask, permissionStep(false, true, true))
    @Test fun deniedForGoodOpensSettings() = assertEquals(PermissionStep.OpenSettings, permissionStep(false, true, false))
    @Test fun neverAskedButNoRationaleStillAsks() = assertEquals(PermissionStep.Ask, permissionStep(false, false, false))
}
