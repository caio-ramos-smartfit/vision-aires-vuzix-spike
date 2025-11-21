package com.caio.vuzixhello.network

data class BioritmoResponse(
    val person: Person
)

data class Person(
    val id: Int,
    val name: String,
    val integration_id: Int,
    val program_name: String?,
    val location: Location?
)

data class Location(
    val id: Int,
    val name: String
)
