package no.nav.sosialhjelp.avtaler

import no.finn.unleash.DefaultUnleash
import no.finn.unleash.Unleash
import no.finn.unleash.strategy.Strategy
import no.finn.unleash.util.UnleashConfig

object UnleashKlient {
    private val unleash: Unleash

    init {
        val miljo = Configuration.cluster
        unleash = when (miljo) {
            Configuration.Cluster.`PROD-GCP`, Configuration.Cluster.`DEV-GCP`, Configuration.Cluster.LOCAL -> DefaultUnleash(
                UnleashConfig.builder()
                    .appName("sosialhjelp-avtaler-api")
                    .instanceId(miljo.name)
                    .unleashAPI("https://unleash.nais.io/api/")
                    .build(),
                ClusterStrategy(miljo)
            )
        }
    }

    fun isEnabled(toggleKey: String) = unleash.isEnabled(toggleKey, false)
}

object UnleashToggleKeys

class ClusterStrategy(val miljo: Configuration.Cluster) : Strategy {
    override fun getName() = "byCluster"

    override fun isEnabled(parameters: MutableMap<String, String>): Boolean {
        val clustersParameter = parameters["cluster"] ?: return false
        val alleClustere = clustersParameter.split(",").map { it.trim() }.map { it.lowercase() }.toList()
        return alleClustere.contains(miljo.name.lowercase())
    }
}
