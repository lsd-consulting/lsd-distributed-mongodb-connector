package io.lsdconsulting.lsd.distributed.mongo.config;

import io.lsdconsulting.lsd.distributed.access.repository.InterceptedDocumentRepository;
import io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoRepository.DEFAULT_COLLECTION_SIZE_LIMIT_MBS;
import static io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoRepository.DEFAULT_TIMEOUT_MILLIS;

@Configuration
@ConditionalOnProperty(name = "lsd.dist.db.connectionString")
public class LibraryConfig {

    @Bean
    public InterceptedDocumentRepository interceptedDocumentRepository(@Value("${lsd.dist.db.connectionString}") String dbConnectionString,
                                                                       @Value("${lsd.dist.db.trustStoreLocation:#{null}}") String trustStoreLocation,
                                                                       @Value("${lsd.dist.db.trustStorePassword:#{null}}") String trustStorePassword,
                                                                       @Value("${lsd.dist.db.connectionTimeout.millis:#{" + DEFAULT_TIMEOUT_MILLIS + "}}") Integer connectionTimeout,
                                                                       @Value("${lsd.dist.db.collectionSizeLimit.megabytes:#{" + DEFAULT_COLLECTION_SIZE_LIMIT_MBS + "}}") Long collectionSizeLimit) {
        return new InterceptedDocumentMongoRepository(dbConnectionString, trustStoreLocation, trustStorePassword, connectionTimeout, collectionSizeLimit);
    }
}
