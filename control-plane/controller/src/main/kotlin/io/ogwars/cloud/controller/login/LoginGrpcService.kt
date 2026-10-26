package io.ogwars.cloud.controller.login

import io.ogwars.cloud.contracts.internal.v1.LoginServiceGrpc
import org.springframework.stereotype.Service

/** The gateway's login resolution (internal gRPC); calls answer UNIMPLEMENTED until TODO(plan: phase-02-controller) T-0207. */
@Service
class LoginGrpcService : LoginServiceGrpc.LoginServiceImplBase()
