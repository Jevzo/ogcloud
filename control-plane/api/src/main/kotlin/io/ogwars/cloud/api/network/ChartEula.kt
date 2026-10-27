package io.ogwars.cloud.api.network

import io.ogwars.cloud.domain.audit.Actor
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/** `ogcloud.minecraft.*`, from the chart's `minecraft.*` values. */
@ConfigurationProperties("ogcloud.minecraft")
data class MinecraftProperties(
    /** Set by Terraform or the installer when the network owner accepted the Minecraft EULA (D-0046). */
    val eulaAccepted: Boolean = false,
)

/** Records an EULA acceptance given in the chart; it never withdraws one (D-0100). */
@Component
class ChartEula(
    private val properties: MinecraftProperties,
    private val network: NetworkManagement,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if (properties.eulaAccepted) network.recordEula(Actor.system("chart"))
    }
}
