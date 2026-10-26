package io.ogwars.cloud.controller.kube

/** Labels and annotations on game server pods; the reconciler rebuilds state from them, NetworkPolicies select by role. */
object PodLabels {
    const val PREFIX = "cloud.ogwars.io"
    const val SERVER_ID = "$PREFIX/server-id"
    const val GROUP = "$PREFIX/group"
    const val GROUP_TYPE = "$PREFIX/group-type"
    const val PROFILE = "$PREFIX/profile"

    /** `backend` or `proxy`: backends accept traffic only from proxies (D-0065). */
    const val ROLE = "$PREFIX/role"
    const val NAME = "app.kubernetes.io/name"
    const val MANAGED_BY = "app.kubernetes.io/managed-by"
    const val SERVER_NAME = "ogcloud-server"
    const val CONTROLLER_NAME = "ogcloud-controller"

    const val SAFE_TO_EVICT = "cluster-autoscaler.kubernetes.io/safe-to-evict"
}
