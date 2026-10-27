package io.ogwars.cloud.controller.kube

import io.ogwars.cloud.controller.config.PodProperties
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.client.KubernetesClient
import io.fabric8.kubernetes.client.KubernetesClientException
import io.fabric8.kubernetes.client.dsl.base.PatchContext
import io.fabric8.kubernetes.client.dsl.base.PatchType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration

/** The pod operations the lifecycle needs. */
interface PodGateway {
    /** Creates the pod; an existing pod of that name counts as created (a retried provisioning). */
    fun create(pod: Pod)

    /** Deletes the pod; [grace] null uses the pod's own grace period, zero kills it. A missing pod is fine. */
    fun delete(
        podName: String,
        grace: Duration? = null,
    )

    fun setSafeToEvict(
        podName: String,
        safe: Boolean,
    )

    fun ensureVolumeClaim(claim: PersistentVolumeClaim)

    /** Every server pod, read fresh from the API server rather than an informer cache. */
    fun list(): List<Pod>

    /** The controller's volume claims in the game namespace (STATIC groups). */
    fun listVolumeClaims(): List<PersistentVolumeClaim>

    fun deleteVolumeClaim(name: String)
}

@Component
class FabricPodGateway(
    private val client: KubernetesClient,
    private val settings: PodProperties,
) : PodGateway {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun create(pod: Pod) {
        try {
            pods().resource(pod).create()
        } catch (e: KubernetesClientException) {
            if (e.code != CONFLICT) throw e
            log.info("Pod {} already exists", pod.metadata.name)
        }
    }

    override fun delete(
        podName: String,
        grace: Duration?,
    ) {
        val pod = pods().withName(podName)
        if (grace == null) pod.delete() else pod.withGracePeriod(grace.seconds).delete()
    }

    override fun setSafeToEvict(
        podName: String,
        safe: Boolean,
    ) {
        try {
            pods()
                .withName(podName)
                .patch(
                    PatchContext.of(PatchType.JSON_MERGE),
                    """{"metadata":{"annotations":{"${PodLabels.SAFE_TO_EVICT}":"$safe"}}}""",
                )
        } catch (e: KubernetesClientException) {
            // The pod may be gone already; it then needs no annotation.
            if (e.code != NOT_FOUND) throw e
        }
    }

    override fun ensureVolumeClaim(claim: PersistentVolumeClaim) {
        client
            .persistentVolumeClaims()
            .inNamespace(settings.namespace)
            .resource(claim)
            .serverSideApply()
    }

    override fun list(): List<Pod> = pods().withLabel(PodLabels.MANAGED_BY, PodLabels.CONTROLLER_NAME).list().items

    override fun listVolumeClaims(): List<PersistentVolumeClaim> =
        client
            .persistentVolumeClaims()
            .inNamespace(settings.namespace)
            .withLabel(PodLabels.MANAGED_BY, PodLabels.CONTROLLER_NAME)
            .list()
            .items

    override fun deleteVolumeClaim(name: String) {
        client
            .persistentVolumeClaims()
            .inNamespace(settings.namespace)
            .withName(name)
            .delete()
    }

    private fun pods() = client.pods().inNamespace(settings.namespace)

    private companion object {
        const val CONFLICT = 409
        const val NOT_FOUND = 404
    }
}
