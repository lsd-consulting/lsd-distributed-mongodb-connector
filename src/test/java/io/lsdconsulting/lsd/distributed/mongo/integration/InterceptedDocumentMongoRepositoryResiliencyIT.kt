package io.lsdconsulting.lsd.distributed.mongo.integration

import io.lsdconsulting.lsd.distributed.access.model.InterceptedInteraction
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.TestApplication
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.Companion.MONGODB_PORT
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.Companion.setupDatabase
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.Companion.tearDownClient
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.Companion.tearDownDatabase
import io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoRepository
import org.apache.commons.lang3.RandomStringUtils
import org.awaitility.Awaitility.await
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.jeasy.random.EasyRandom
import org.jeasy.random.EasyRandomParameters
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import java.util.concurrent.TimeUnit.MILLISECONDS

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = [TestApplication::class])
@ActiveProfiles("test")
internal class InterceptedDocumentMongoRepositoryResiliencyIT {
    private val easyRandom = EasyRandom(EasyRandomParameters().seed(Instant.now().toEpochMilli()))

    @BeforeEach
    fun setupTestDatabase() {
        setupDatabase()
    }

    @AfterEach
    fun tearDownTestDatabase() {
        tearDownDatabase()
    }

    @Test
    fun shouldHandleDbBeingDownGracefullyOnStartup() {
        InterceptedDocumentMongoRepository(
            "mongodb://" + RandomStringUtils.randomAlphabetic(10),
            DB_CONNECTION_TIMEOUT,
            DB_COLLECTION_SIZE_LIMIT
        )
    }

    @Test
    fun shouldNotSlowDownStartupIfDbDown() {
        await()
            .atLeast(450, MILLISECONDS)
            .atMost(1000, MILLISECONDS)
            .untilAsserted {
                assertThat(
                    InterceptedDocumentMongoRepository(
                        "mongodb://" + RandomStringUtils.randomAlphabetic(10) + ":" + MONGODB_PORT,
                        DB_CONNECTION_TIMEOUT,
                        DB_COLLECTION_SIZE_LIMIT
                    ), `is`(
                        notNullValue()
                    )
                )
            }
    }

    @Test
    fun shouldHandleDbGoingDownAfterStartup() {
        val underTest = InterceptedDocumentMongoRepository(
            "mongodb://" + TestRepository.MONGODB_HOST + ":" + MONGODB_PORT,
            DB_CONNECTION_TIMEOUT,
            DB_COLLECTION_SIZE_LIMIT
        )
        val interceptedInteraction = easyRandom.nextObject(InterceptedInteraction::class.java)
        underTest.save(interceptedInteraction)
        val initialResult = underTest.findByTraceIds("traceId")
        assertThat(initialResult, `is`(notNullValue()))
        tearDownDatabase()
        val result = underTest.findByTraceIds("traceId")
        assertThat(result, `is`(empty()))
    }

    @Test
    fun shouldRecoverFromDbGoingDown() {
        val underTest = InterceptedDocumentMongoRepository(
            "mongodb://" + TestRepository.MONGODB_HOST + ":" + MONGODB_PORT,
            DB_CONNECTION_TIMEOUT,
            DB_COLLECTION_SIZE_LIMIT
        )
        val interceptedInteraction = easyRandom.nextObject(InterceptedInteraction::class.java)
        underTest.save(interceptedInteraction)
        tearDownDatabase()
        val result = underTest.findByTraceIds("traceId")
        assertThat(result, `is`(empty()))
        setupDatabase()
        val initialResult = underTest.findByTraceIds("traceId")
        assertThat(initialResult, `is`(notNullValue()))
    }

    @Test
    fun shouldNotSlowDownProduction() {
        val underTest = InterceptedDocumentMongoRepository(
            "mongodb://" + TestRepository.MONGODB_HOST + ":" + MONGODB_PORT,
            DB_CONNECTION_TIMEOUT,
            DB_COLLECTION_SIZE_LIMIT
        )
        val interceptedInteraction = easyRandom.nextObject(InterceptedInteraction::class.java)
        underTest.save(interceptedInteraction)
        tearDownDatabase()
        await()
            .atLeast(450, MILLISECONDS)
            .atMost(1000, MILLISECONDS)
            .untilAsserted {
                assertThat(
                    underTest.findByTraceIds("traceId"),
                    `is`(empty())
                )
            }
    }

    companion object {
        private const val DB_CONNECTION_TIMEOUT = 500
        const val DB_COLLECTION_SIZE_LIMIT = 1024 * 100L

        @JvmStatic
        @AfterAll
        fun tearDownTestClient() {
            tearDownClient()
        }
    }
}
