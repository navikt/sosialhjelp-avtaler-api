package no.nav.sosialhjelp.avtaler.altinn

import com.fasterxml.jackson.annotation.JsonProperty

data class Avgiver(
    @JsonProperty("Name")
    val navn: String,
    @JsonProperty("OrganizationNumber")
    val orgnr: String,
    @JsonProperty("OrganizationForm")
    val organisasjonsform: String?,
) {
    fun erKommune(): Boolean {
        return organisasjonsform == "KOMM"
    }

    enum class Tjeneste(val kode: String, val versjon: Int) {
        /**
         * "Signering av avtale fra Digisos"
         */
        AVTALESIGNERING(kode = "5867", versjon = 1),
        ;

        override fun toString(): String = "[$kode,$versjon]"
    }
}
