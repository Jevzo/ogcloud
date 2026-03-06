package io.ogwars.cloud.controller.repository

import io.ogwars.cloud.controller.model.PlayerDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface PlayerRepository : MongoRepository<PlayerDocument, String>
