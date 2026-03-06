package io.ogwars.cloud.controller.config

import io.ogwars.cloud.controller.service.LengthSafeBcryptPasswordEncoder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class ApplicationConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = LengthSafeBcryptPasswordEncoder()
}
