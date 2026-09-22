package no.juliannordli.kovacomp

data class KovaOrganization(
    val name: String,
    val code: String
)

object Organizations {
    val known = listOf(
        KovaOrganization("Ullensaker Røde Kors Hjelpekorps", "UllensakerRKH"),
        KovaOrganization("Eidsvoll/Hurdal Røde Kors Hjelpekorps", "EHRKH"),
        KovaOrganization("Nittedal Røde Kors Hjelpekorps", "Nittedal RKH"),
        KovaOrganization("Skedsmo Røde Kors Hjelpekorps", "Skedsmo RKH")
    )

    fun nameFor(code: String): String =
        known.firstOrNull { it.code == code }?.name ?: code
}
