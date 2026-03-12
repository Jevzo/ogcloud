package io.ogwars.cloud.velocity.mongo

import io.ogwars.cloud.common.model.*
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoDatabase
import org.bson.Document

class MongoManager(
    mongoUri: String,
    databaseName: String,
) {
    private val client: MongoClient = MongoClients.create(mongoUri)
    private val database: MongoDatabase = client.getDatabase(databaseName)
    private val permissionGroupsCollection = database.getCollection(PERMISSION_GROUPS_COLLECTION)
    private val networkSettingsCollection = database.getCollection(NETWORK_SETTINGS_COLLECTION)

    fun findAllPermissionGroups(): List<PermissionGroupDocument> =
        permissionGroupsCollection.find().map { document -> document.toPermissionGroupDocument() }.toList()

    fun findNetworkSettings(): NetworkSettingsDocument {
        val document =
            networkSettingsCollection.find(Document(ID_FIELD, GLOBAL_ID)).first() ?: return NetworkSettingsDocument()
        return document.toNetworkSettingsDocument()
    }

    fun close() {
        client.close()
    }

    private fun Document.toPermissionGroupDocument(): PermissionGroupDocument {
        val permissions = getList("permissions", String::class.java) ?: emptyList()
        val displayDocument = get("display", Document::class.java)

        return PermissionGroupDocument(
            id = getString(ID_FIELD),
            name = getString("name") ?: getString(ID_FIELD),
            display =
                DisplayConfig(
                    chatPrefix = displayDocument?.getString("chatPrefix") ?: "",
                    chatSuffix = displayDocument?.getString("chatSuffix") ?: "",
                    nameColor = displayDocument?.getString("nameColor") ?: "&7",
                    tabPrefix = displayDocument?.getString("tabPrefix") ?: "&7",
                ),
            weight = getInteger("weight", 100),
            default = getBoolean("default", false),
            permissions = permissions,
        )
    }

    private fun Document.toNetworkSettingsDocument(): NetworkSettingsDocument {
        val motdDocument = get("motd", Document::class.java)
        val versionDocument = get("versionName", Document::class.java)
        val tablistDocument = get("tablist", Document::class.java)
        val generalDocument = get("general", Document::class.java)

        return NetworkSettingsDocument(
            id = GLOBAL_ID,
            motd =
                MotdSettings(
                    global = motdDocument?.getString("global") ?: DEFAULT_MOTD,
                    maintenance = motdDocument?.getString("maintenance") ?: DEFAULT_MAINTENANCE_MOTD,
                ),
            versionName =
                VersionNameSettings(
                    global = versionDocument?.getString("global") ?: DEFAULT_VERSION_NAME,
                    maintenance = versionDocument?.getString("maintenance") ?: DEFAULT_MAINTENANCE_VERSION_NAME,
                ),
            maxPlayers = getInteger("maxPlayers", 1000),
            defaultGroup = getString("defaultGroup") ?: "lobby",
            maintenance = getBoolean("maintenance", false),
            maintenanceKickMessage =
                getString("maintenanceKickMessage")
                    ?: "&cServer is currently in maintenance mode.",
            tablist = tablistDocument?.toTablistSettings() ?: TablistSettings(),
            general = generalDocument?.toGeneralSettings() ?: GeneralSettings(),
        )
    }

    private fun Document.toTablistSettings(): TablistSettings =
        TablistSettings(
            header = getString("header") ?: DEFAULT_TABLIST_HEADER,
            footer = getString("footer") ?: DEFAULT_TABLIST_FOOTER,
        )

    private fun Document.toGeneralSettings(): GeneralSettings =
        GeneralSettings(
            permissionSystemEnabled = getBoolean("permissionSystemEnabled", true),
            tablistEnabled = getBoolean("tablistEnabled", true),
        )

    companion object {
        private const val ID_FIELD = "_id"
        private const val GLOBAL_ID = "global"
        private const val PERMISSION_GROUPS_COLLECTION = "permission_groups"
        private const val NETWORK_SETTINGS_COLLECTION = "network_settings"
        private const val DEFAULT_MOTD = "&6OgCloud Network\n&7A Minecraft Server"
        private const val DEFAULT_MAINTENANCE_MOTD = "&c&lMAINTENANCE\n&7We'll be back soon!"
        private const val DEFAULT_VERSION_NAME = "OgCloud Network"
        private const val DEFAULT_MAINTENANCE_VERSION_NAME = "MAINTENANCE"
        private const val DEFAULT_TABLIST_HEADER =
            "\n&6&lOgCloud Network\n&7Online: &a%onlinePlayers%&7/&a%maxPlayers%\n"
        private const val DEFAULT_TABLIST_FOOTER = "\n&7Server: &a%server% &8| &7Group: &a%group%\n&7Ping: &a%ping%ms\n"
    }
}
