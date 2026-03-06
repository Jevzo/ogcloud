package io.ogwars.cloud.controller.repository

import io.ogwars.cloud.controller.model.ScalingLogDocument
import org.springframework.data.mongodb.repository.MongoRepository

interface ScalingLogRepository : MongoRepository<ScalingLogDocument, String>
