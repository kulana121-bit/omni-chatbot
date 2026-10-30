package com.ravi.omnichat.data

import android.content.Intent
import android.provider.Settings
import org.junit.Assert.assertEquals
import org.junit.Test

/** P1-2: ACTION_DATA_USAGE_SETTINGS needs API 28; older phones fall back to main Settings. */
class ConnectorsTest {
    @Test fun dataUsageActionNeedsApi28() {
        assertEquals(Settings.ACTION_SETTINGS, Connectors.settingsAction("data", 26))
        assertEquals(Settings.ACTION_SETTINGS, Connectors.settingsAction("data", 27))
        assertEquals(Settings.ACTION_DATA_USAGE_SETTINGS, Connectors.settingsAction("data", 28))
        assertEquals(Settings.ACTION_DATA_USAGE_SETTINGS, Connectors.settingsAction("data", 35))
    }

    @Test fun otherPagesUnchanged() {
        assertEquals(Settings.ACTION_WIFI_SETTINGS, Connectors.settingsAction("wifi", 26))
        assertEquals(Intent.ACTION_POWER_USAGE_SUMMARY, Connectors.settingsAction("battery", 26))
        assertEquals(Settings.ACTION_SETTINGS, Connectors.settingsAction("whatever", 26))
    }
}
