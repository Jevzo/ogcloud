package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.config.PodProperties
import io.ogwars.cloud.controller.leader.LeaderService
import io.ogwars.cloud.controller.lifecycle.LifecycleService
import io.ogwars.cloud.domain.audit.Actor
import io.fabric8.kubernetes.api.model.Node
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.informers.ResourceEventHandler
import io.fabric8.kubernetes.client.informers.SharedIndexInformer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Drives the lifecycle from Kubernetes on the leader: pod status changes become transitions, finished bare pods are
 * deleted, and servers on a node that gets cordoned start draining.
 */
@Component
class PodWatcher(
    private val client: KubernetesClient,
    private val settings: PodProperties,
    private val lifecycle: LifecycleService,
    private val pods: PodGateway,
) : LeaderService {
    private val log = LoggerFactory.getLogger(javaClass)
    private var podInformer: SharedIndexInformer<Pod>? = null
    private var nodeInformer: SharedIndexInformer<Node>? = null

    @Synchronized
    override fun startLeading() {
        val podsOfServers =
            client
                .pods()
                .inNamespace(
                    settings.namespace,
                ).withLabel(PodLabels.MANAGED_BY, PodLabels.CONTROLLER_NAME)
        podInformer = podsOfServers.inform(PodHandler(), RESYNC.toMillis())
        nodeInformer = client.nodes().inform(NodeHandler(), RESYNC.toMillis())
    }

    @Synchronized
    override fun stopLeading() {
        podInformer?.close()
        nodeInformer?.close()
        podInformer = null
        nodeInformer = null
    }

    private fun apply(
        observation: PodObservation?,
        pod: Pod,
    ) {
        if (observation == null) return
        guarded(pod.metadata.name) {
            when (observation) {
                is PodObservation.Started -> {
                    lifecycle.podStarted(observation.serverId, observation.host)
                }

                is PodObservation.Ended -> {
                    lifecycle.podEnded(observation.serverId, observation.end, observation.reason)
                    // Bare pods stay around after they finish; nothing reads them once the server is gone.
                    if (pod.metadata.deletionTimestamp == null) pods.delete(pod.metadata.name)
                }
            }
        }
    }

    private fun drainServersOn(node: String) {
        val onNode =
            podInformer
                ?.store
                ?.list()
                .orEmpty()
                .filter { it.spec?.nodeName == node }
        log.info("Node {} was cordoned; draining {} servers", node, onNode.size)
        onNode.forEach { pod ->
            val serverId = PodObservation.serverId(pod) ?: return@forEach
            guarded(pod.metadata.name) { lifecycle.drain(serverId, SYSTEM, "node $node cordoned") }
        }
    }

    // An exception would stop the informer's handler thread; one bad pod must not stop the rest.
    private fun guarded(
        subject: String,
        action: () -> Unit,
    ) {
        try {
            action()
        } catch (e: Exception) {
            log.error("Handling {} failed; the next resync retries", subject, e)
        }
    }

    private inner class PodHandler : ResourceEventHandler<Pod> {
        override fun onAdd(pod: Pod) = apply(PodObservation.of(pod), pod)

        override fun onUpdate(
            old: Pod,
            pod: Pod,
        ) = apply(PodObservation.of(pod), pod)

        override fun onDelete(
            pod: Pod,
            deletedFinalStateUnknown: Boolean,
        ) = apply(PodObservation.deleted(pod), pod)
    }

    private inner class NodeHandler : ResourceEventHandler<Node> {
        override fun onAdd(node: Node) = Unit

        override fun onUpdate(
            old: Node,
            node: Node,
        ) {
            if (old.spec?.unschedulable != true && node.spec?.unschedulable == true) drainServersOn(node.metadata.name)
        }

        override fun onDelete(
            node: Node,
            deletedFinalStateUnknown: Boolean,
        ) = Unit
    }

    private companion object {
        val SYSTEM = Actor.system("controller")

        /** A full resync replays every pod, repairing anything a missed event left behind. */
        val RESYNC: Duration = Duration.ofMinutes(5)
    }
}
