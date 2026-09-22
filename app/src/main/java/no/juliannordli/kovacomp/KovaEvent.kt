package no.juliannordli.kovacomp

data class KovaEvent(
    val id: String,
    val dateIso: String,
    val dateLabel: String,
    val time: String,
    val type: String,
    val description: String,
    val sourceUrl: String
) {
    val semanticKey: String
        get() = (type.trim().lowercase() + "|" + description.trim().lowercase()).replace(Regex("\\s+"), " ")
}
