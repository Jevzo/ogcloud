package io.ogwars.cloud.controller.template

import io.ogwars.cloud.contracts.internal.v1.RegisterTemplateVersionRequest
import io.ogwars.cloud.contracts.internal.v1.RegisterTemplateVersionResponse
import io.ogwars.cloud.contracts.internal.v1.TemplateServiceGrpc
import io.grpc.Status
import io.grpc.stub.StreamObserver
import org.springframework.stereotype.Service
import java.util.UUID

/** The gateway registers finished template uploads here (internal gRPC, gateway only, D-0081). */
@Service
class TemplateGrpcService(
    private val versions: TemplateVersions,
) : TemplateServiceGrpc.TemplateServiceImplBase() {
    override fun registerTemplateVersion(
        request: RegisterTemplateVersionRequest,
        response: StreamObserver<RegisterTemplateVersionResponse>,
    ) {
        val status =
            try {
                val serverId = UUID.fromString(request.serverId)
                val registered = versions.register(serverId, request.objectKey, request.sha256, request.sizeBytes)
                response.onNext(
                    RegisterTemplateVersionResponse
                        .newBuilder()
                        .setTemplateVersionId(registered.id.toString())
                        .setPromoted(registered.promoted)
                        .build(),
                )
                response.onCompleted()
                return
            } catch (e: NoSuchElementException) {
                Status.NOT_FOUND.withDescription(e.message)
            } catch (e: IllegalArgumentException) {
                Status.INVALID_ARGUMENT.withDescription(e.message)
            }
        response.onError(status.asRuntimeException())
    }
}
