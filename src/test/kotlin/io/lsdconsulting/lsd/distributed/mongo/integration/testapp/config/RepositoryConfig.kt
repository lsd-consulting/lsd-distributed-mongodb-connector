package io.lsdconsulting.lsd.distributed.mongo.integration.testapp.config

import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
open class RepositoryConfig {
    @Bean
    open fun testRepository() = TestRepository()

    companion object {
        // This is because the configs in spring.factories run always before any test configs.
        init {
            TestRepository.setupDatabase()
        }
    }
}