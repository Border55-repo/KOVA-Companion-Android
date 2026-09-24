package no.juliannordli.kovacomp

import org.junit.Assert.*
import org.junit.Test

class NotificationPolicyTest {
    @Test fun activityAndReminderRequireFavoriteCorps() {
        val favorites = setOf("UllensakerRKH")
        for (kind in listOf("added", "changed", "removed", "reminder", "summary")) {
            assertTrue(NotificationPolicy.allowsOrganization(kind, "UllensakerRKH", favorites))
            assertFalse(NotificationPolicy.allowsOrganization(kind, "EHRKH", favorites))
        }
    }
    @Test fun noFavoritesMeansNoCorpsNotifications() {
        assertFalse(NotificationPolicy.allowsOrganization("added", "UllensakerRKH", emptySet()))
        assertEquals(setOf("kova_all_users"), NotificationPolicy.topics(emptySet()))
    }
    @Test fun globalAnnouncementsDoNotDependOnFavoriteCorps() {
        assertTrue(NotificationPolicy.allowsOrganization("announcement", "all_users", emptySet()))
    }
    @Test fun subscriptionTopicsContainOnlyFavoritesAndGlobalAnnouncements() {
        assertEquals(setOf("kova_skedsmo_rkh", "kova_all_users"),
            NotificationPolicy.topics(setOf("Skedsmo RKH")))
    }
}
