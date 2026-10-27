package io.ogwars.cloud.domain.audit

enum class ActorType {
    USER,
    CLIENT,
    PLAYER,

    /** The platform acting on its own, e.g. the autoscaler. */
    SYSTEM,
}

/** Who caused a change; recorded in the audit log and on jobs. */
data class Actor(
    val type: ActorType,
    val id: String,
) {
    init {
        require(id.isNotBlank()) { "Actor id must not be blank" }
    }

    override fun toString(): String = "${type.name.lowercase()}:$id"

    companion object {
        /** Actors that are people, whose ids an erasure replaces (D-0049). */
        val PERSON_TYPES = setOf(ActorType.USER, ActorType.PLAYER)

        fun system(component: String) = Actor(ActorType.SYSTEM, component)

        /** Reverses [toString], e.g. "user:0192…". */
        fun parse(text: String): Actor {
            val (type, id) = text.split(':', limit = 2).also { require(it.size == 2) { "Invalid actor '$text'" } }
            return Actor(ActorType.valueOf(type.uppercase()), id)
        }
    }
}
