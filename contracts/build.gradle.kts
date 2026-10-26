plugins {
    id("ogcloud.kotlin-library")
    alias(libs.plugins.protobuf)
}

dependencies {
    api(libs.protobuf.java)
    api(libs.protobuf.kotlin)
    // grpc-java needs all its artifacts on one version; this aligns those pulled in by Spring gRPC too.
    api(platform(libs.grpc.bom))
    api(libs.grpc.protobuf)
    api(libs.grpc.stub)
}

sourceSets {
    main {
        proto {
            srcDir("proto")
        }
    }
}

protobuf {
    protoc {
        artifact =
            libs.protobuf.protoc
                .get()
                .toString()
    }
    plugins {
        create("grpc") {
            artifact =
                libs.grpc.protoc.gen.java
                    .get()
                    .toString()
        }
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                create("kotlin")
            }
            plugins {
                create("grpc")
            }
        }
    }
}
