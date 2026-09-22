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
    val displayTime: String
        get() = time.ifBlank { "Tid ikke oppgitt" }

    val semanticKey: String
        get() = (type.trim().lowercase() + "|" + description.trim().lowercase()).replace(Regex("\\s+"), " ")
}
