package io.ogwars.cloud.controller.exception

class GroupNotFoundException(name: String) : RuntimeException("Group not found: $name")

class ServerNotFoundException(id: String) : RuntimeException("Server not found: $id")