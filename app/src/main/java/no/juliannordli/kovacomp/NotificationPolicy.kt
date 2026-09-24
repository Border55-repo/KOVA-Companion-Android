package no.juliannordli.kovacomp

object NotificationPolicy {
    fun allowsOrganization(kind: String, organization: String, favorites: Set<String>): Boolean =
        kind == "announcement" || organization in favorites

    fun topics(favorites: Set<String>): Set<String> =
        favorites.map { code ->
            "kova_" + code.lowercase().replace(Regex("[^a-z0-9_.~%-]"), "_")
        }.toSet() + "kova_all_users"
}
