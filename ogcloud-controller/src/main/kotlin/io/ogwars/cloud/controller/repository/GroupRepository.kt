package io.ogwars.cloud.controller.repository

import io.ogwars.cloud.controller.model.GroupDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface GroupRepository : MongoRepository<GroupDocument, String>
