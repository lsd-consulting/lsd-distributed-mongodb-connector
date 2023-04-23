package io.lsdconsulting.lsd.distributed.mongo.config

import io.lsdconsulting.lsd.distributed.access.repository.InterceptedDocumentRepository
import io.lsdconsulting.lsd.distributed.mongo.repository.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["lsd.dist.db.connectionString"])
open class LibraryConfig {
    @Bean
    @ConditionalOnExpression("#{'\${lsd.dist.db.connectionString:}'.startsWith('mongodb://')}")
    open fun interceptedDocumentRepository(
        interceptedInteractionCollectionBuilder: InterceptedInteractionCollectionBuilder
    ) = InterceptedDocumentMongoRepository(
        interceptedInteractionCollectionBuilder
    )

    @Bean
    @ConditionalOnExpression("#{'\${lsd.dist.db.connectionString:}'.startsWith('mongodb://')}")
    open fun interceptedDocumentAdminRepository(
        interceptedDocumentRepository: InterceptedDocumentRepository,
        interceptedInteractionCollectionBuilder: InterceptedInteractionCollectionBuilder
    ) = InterceptedDocumentMongoAdminRepository(
            interceptedInteractionCollectionBuilder, interceptedDocumentRepository
        )

    @Bean
    @ConditionalOnExpression("#{'\${lsd.dist.db.connectionString:}'.startsWith('mongodb://')}")
    open fun interceptedInteractionCollectionBuilder(
        @Value("\${lsd.dist.db.connectionString}") dbConnectionString: String,
        @Value("\${lsd.dist.db.trustStoreLocation:#{null}}") trustStoreLocation: String?,
        @Value("\${lsd.dist.db.trustStorePassword:#{null}}") trustStorePassword: String?,
        @Value("\${lsd.dist.db.connectionTimeout.millis:#{" + DEFAULT_TIMEOUT_MILLIS + "}}") connectionTimeout: Int,
        @Value("\${lsd.dist.db.collectionSizeLimit.megabytes:#{" + DEFAULT_COLLECTION_SIZE_LIMIT_MBS + "}}") collectionSizeLimit: Long,
        interceptedDocumentRepository: InterceptedDocumentRepository
    ): InterceptedInteractionCollectionBuilder = InterceptedInteractionCollectionBuilder(
        dbConnectionString,
        trustStoreLocation,
        trustStorePassword,
        connectionTimeout,
        collectionSizeLimit
    )
}
