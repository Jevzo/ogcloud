plugins {
    id("ogcloud.kotlin-library")
}

dependencies {
    api(project(":contracts"))
    // Netty comes shaded inside grpc-java, so it never meets the server's own Netty (D-0008).
    implementation(libs.grpc.netty.shaded)
    testImplementation(libs.grpc.inprocess)
}
