package karst.vpn.data

import karst.vpn.data.entities.SubscriptionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubscriptionAutoRefreshSchedulerTest {

    @Test
    fun testOffModeIsNeverScheduled() {
        val config = SubscriptionAutoRefreshConfig(
            mode = SubscriptionAutoRefreshMode.Off,
            customHours = 6,
            subscriptions = listOf(subscription(profileUpdateIntervalHours = 3)),
        )
        assertNull(config.repeatHoursOrNull())
    }

    @Test
    fun testNoSubscriptionsMeansNoSchedule() {
        val config = SubscriptionAutoRefreshConfig(
            mode = SubscriptionAutoRefreshMode.EveryHours,
            customHours = 6,
            subscriptions = emptyList(),
        )
        assertNull(config.repeatHoursOrNull())
    }

    @Test
    fun testEveryHoursModeUsesCustomHoursClamped() {
        val config = SubscriptionAutoRefreshConfig(
            mode = SubscriptionAutoRefreshMode.EveryHours,
            customHours = 0,
            subscriptions = listOf(subscription()),
        )
        assertEquals(1, config.repeatHoursOrNull())

        val tooLarge = config.copy(customHours = MAX_AUTO_REFRESH_HOURS + 100)
        assertEquals(MAX_AUTO_REFRESH_HOURS, tooLarge.repeatHoursOrNull())
    }

    @Test
    fun testAutoModePicksSmallestValidInterval() {
        val config = SubscriptionAutoRefreshConfig(
            mode = SubscriptionAutoRefreshMode.Auto,
            customHours = 24,
            subscriptions = listOf(
                subscription(profileUpdateIntervalHours = 12),
                subscription(profileUpdateIntervalHours = 4),
                subscription(profileUpdateIntervalHours = null),
                subscription(profileUpdateIntervalHours = 0),
                subscription(profileUpdateIntervalHours = MAX_AUTO_REFRESH_HOURS + 1),
            ),
        )
        assertEquals(4, config.repeatHoursOrNull())
    }

    @Test
    fun testAutoModeFallsBackToDefaultWhenNoValidIntervals() {
        val config = SubscriptionAutoRefreshConfig(
            mode = SubscriptionAutoRefreshMode.Auto,
            customHours = 24,
            subscriptions = listOf(subscription(profileUpdateIntervalHours = null)),
        )
        assertEquals(SettingsRepository.DEFAULT_AUTO_REFRESH_HOURS, config.repeatHoursOrNull())
    }

    private var nextSubscriptionId = 0

    private fun subscription(profileUpdateIntervalHours: Int? = null) = SubscriptionEntity(
        id = "sub-${nextSubscriptionId++}",
        url = "https://example.com/sub",
        displayName = "Sub",
        profileUpdateIntervalHours = profileUpdateIntervalHours,
        lastRefreshedAtEpochMs = null,
        lastRefreshError = null,
        createdAtEpochMs = 0L,
    )
}
