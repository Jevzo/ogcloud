package io.ogwars.cloud.api.repository

import io.ogwars.cloud.api.model.GroupDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface GroupRepository : MongoRepository<GroupDocument, String>
