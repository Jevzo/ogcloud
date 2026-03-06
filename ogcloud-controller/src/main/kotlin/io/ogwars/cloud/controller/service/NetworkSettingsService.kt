package io.ogwars.cloud.controller.service

import io.ogwars.cloud.api.model.NetworkSettingsDocument
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.findById
import org.springframework.stereotype.Service

@Service
class NetworkSettingsService(
    private val mongoTemplate: MongoTemplate
) {

    fun findGlobal(): NetworkSettingsDocument {
        return mongoTemplate.findById<NetworkSettingsDocument>(GLOBAL_SETTINGS_ID, COLLECTION)
            ?: NetworkSettingsDocument()
    }

    companion object {
        private const val COLLECTION = "network_settings"
        private const val GLOBAL_SETTINGS_ID = "global"
    }
}
