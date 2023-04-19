package io.lsdconsulting.lsd.distributed.mongo.integration

import io.lsdconsulting.lsd.distributed.access.model.InteractionType
import io.lsdconsulting.lsd.distributed.access.model.InterceptedInteraction
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.TestApplication
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.config.RepositoryConfig
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.Companion.tearDownClient
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.Companion.tearDownDatabase
import io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoRepository
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.bson.Document
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.jeasy.random.EasyRandom
import org.jeasy.random.EasyRandomParameters
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import java.time.Instant.ofEpochSecond
import java.time.ZoneId
import java.time.ZonedDateTime.now
import java.time.ZonedDateTime.ofInstant
import java.time.temporal.ChronoUnit
import java.util.stream.Collectors
import java.util.stream.StreamSupport


@Import(RepositoryConfig::class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = [TestApplication::class])
internal class InterceptedDocumentMongoRepositoryIT {
    private val easyRandom = EasyRandom(EasyRandomParameters().seed(System.currentTimeMillis()))

    @Autowired
    private lateinit var testRepository: TestRepository

    private lateinit var underTest: InterceptedDocumentMongoRepository

    @BeforeEach
    fun setup() {
        underTest = InterceptedDocumentMongoRepository(
            "mongodb://" + TestRepository.MONGODB_HOST + ":" + TestRepository.MONGODB_PORT,
            1500,
            1L
        )
    }

    @Test
    fun `should create collection with indexes`() {
        val indexes = testRepository.collection.listIndexes()
        val indexNames = StreamSupport.stream(indexes.spliterator(), false)
            .map { doc: Document -> doc["name"] as String? }
            .collect(Collectors.toList())
        assertThat(indexNames, hasItem(startsWith("traceId")))
        assertThat(indexNames, hasItem(startsWith("createdAt")))
    }

    @Test
    fun `should create capped collection`() {
        val isCapped = testRepository.isCollectionCapped
        Assertions.assertTrue(isCapped)
    }

    @Test
    fun `should save and retrieve from collection`() {
        val interceptedInteraction = InterceptedInteraction(
            elapsedTime = 20L,
            httpStatus = "OK",
            path = "/path",
            httpMethod = "GET",
            body = "body",
            interactionType = InteractionType.REQUEST,
            traceId = "traceId",
            createdAt = ofInstant(ofEpochSecond(0), ZoneId.of("UTC")))

        underTest.save(interceptedInteraction)

        val result = underTest.findByTraceIds("traceId")
        assertThat(result, hasSize(1))
        assertThat(result[0].elapsedTime, `is`(20L))
        assertThat(result[0].httpStatus, `is`("OK"))
        assertThat(result[0].path, `is`("/path"))
        assertThat(result[0].httpMethod, `is`("GET"))
        assertThat(result[0].body, `is`("body"))
        assertThat(result[0].interactionType, `is`(InteractionType.REQUEST))
        assertThat(result[0].createdAt, `is`(ofInstant(ofEpochSecond(0), ZoneId.of("UTC"))))
    }

    @Test
    fun `should save and retrieve random entry from collection`() {
        val traceId = randomAlphanumeric(10)
        val interceptedInteraction = buildInterceptedInteraction(traceId)

        underTest.save(interceptedInteraction)

        val result = underTest.findByTraceIds(interceptedInteraction.traceId)
        assertThat(result, hasSize(1))
        assertThat(result[0].elapsedTime, `is`(interceptedInteraction.elapsedTime))
        assertThat(result[0].httpStatus, `is`(interceptedInteraction.httpStatus))
        assertThat(result[0].path, `is`(interceptedInteraction.path))
        assertThat(result[0].httpMethod, `is`(interceptedInteraction.httpMethod))
        assertThat(result[0].body, `is`(interceptedInteraction.body))
        assertThat(result[0].interactionType, `is`(interceptedInteraction.interactionType))
        assertThat(result[0].createdAt, `is`(interceptedInteraction.createdAt))
    }

    @Test
    fun `should save and retrieve in correct order`() {
        val traceId = randomAlphanumeric(10)
        val interceptedInteractions = (1..10)
            .map { _ -> buildInterceptedInteraction(traceId) }
            .sortedByDescending { it.createdAt }

        interceptedInteractions.forEach { underTest.save(it) }

        val result = underTest.findByTraceIds(traceId)
        assertThat(result, hasSize(10))
        (1..10).forEach { assertThat(result[it -1].createdAt, `is`(interceptedInteractions[10 - it].createdAt)) }
    }

    private fun buildInterceptedInteraction(traceId: String) = easyRandom.nextObject(InterceptedInteraction::class.java)
        .copy(traceId = traceId, createdAt = now(ZoneId.of("UTC")).truncatedTo(ChronoUnit.MILLIS))

    companion object {
        @JvmStatic
        @AfterAll
        fun tearDown() {
            tearDownDatabase()
            tearDownClient()
        }
    }
}
