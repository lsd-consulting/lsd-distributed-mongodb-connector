package io.lsdconsulting.lsd.distributed.mongo.integration

import io.lsdconsulting.lsd.distributed.access.model.InteractionType
import io.lsdconsulting.lsd.distributed.access.model.InterceptedInteraction
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.TestApplication
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.config.RepositoryConfig
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.Companion.tearDownClient
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.Companion.tearDownDatabase
import io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoAdminRepository
import io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoRepository
import io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedInteractionCollectionBuilder
import org.apache.commons.lang3.RandomStringUtils.randomAlphanumeric
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.*
import org.jeasy.random.EasyRandom
import org.jeasy.random.EasyRandomParameters
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.ZonedDateTime.now
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*


@Import(RepositoryConfig::class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, classes = [TestApplication::class])
@ActiveProfiles("test")
internal class InterceptedDocumentMongoAdminRepositoryIT {
    private val easyRandom = EasyRandom(EasyRandomParameters().seed(System.currentTimeMillis()))

    @Autowired
    private lateinit var testRepository: TestRepository

    private lateinit var underTest: InterceptedDocumentMongoAdminRepository

    private val secondaryTraceId = randomAlphanumeric(10)
    private val primaryTraceId = randomAlphanumeric(10)
    private val sourceName = randomAlphanumeric(10).uppercase(Locale.getDefault())
    private val targetName = randomAlphanumeric(10).uppercase(Locale.getDefault())

    @BeforeEach
    fun setup() {
        underTest = InterceptedDocumentMongoAdminRepository(
            InterceptedInteractionCollectionBuilder(
                "mongodb://" + TestRepository.MONGODB_HOST + ":" + TestRepository.MONGODB_PORT,
                1500,
                1L,
            ),
            InterceptedDocumentMongoRepository(
                InterceptedInteractionCollectionBuilder(
                    "mongodb://" + TestRepository.MONGODB_HOST + ":" + TestRepository.MONGODB_PORT,
                    1500,
                    1L
                )
            )
        )
        testRepository.deleteAll()
    }

    @Test
    fun `should retrieve flows`() {
        val initialInterceptedInteraction = InterceptedInteraction(
            traceId = primaryTraceId,
            httpMethod = "GET",
            path = "/api-listener?message=from_test",
            serviceName = sourceName,
            target = targetName,
            interactionType = InteractionType.REQUEST,
            elapsedTime = 0,
            createdAt = now(ZoneId.of("UTC")).truncatedTo(MILLIS)
        )
        testRepository.save(initialInterceptedInteraction)

        testRepository.save(
            InterceptedInteraction(
                traceId = primaryTraceId,
                serviceName = sourceName,
                target = targetName,
                interactionType = InteractionType.RESPONSE,
                elapsedTime = 10L,
                httpStatus = "200 OK",
                createdAt = now(ZoneId.of("UTC")).truncatedTo(MILLIS)
            )
        )
        testRepository.save(
            InterceptedInteraction(
                traceId = primaryTraceId,
                httpMethod = "POST",
                path = "/external-api?message=from_feign",
                serviceName = "TestApp",
                target = "UNKNOWN_TARGET",
                interactionType = InteractionType.REQUEST,
                elapsedTime = 0,
                createdAt = now(ZoneId.of("UTC")).truncatedTo(MILLIS)
            )
        )
        val finalInterceptedInteraction = InterceptedInteraction(
            traceId = primaryTraceId,
            serviceName = "TestApp",
            target = "UNKNOWN_TARGET",
            interactionType = InteractionType.RESPONSE,
            elapsedTime = 20L,
            httpStatus = "200 OK",
            createdAt = now(ZoneId.of("UTC")).truncatedTo(MILLIS)
        )
        testRepository.save(finalInterceptedInteraction)

        val result = underTest.findRecentFlows(1)

        assertThat(result, hasSize(1))
        assertThat(result[0].initialInteraction, `is`(initialInterceptedInteraction))
        assertThat(result[0].finalInteraction, `is`(finalInterceptedInteraction))
        assertThat(result[0].totalCapturedInteractions, `is`(4))
    }

    @Test
    fun `should distinguish flows`() {
        val mainFlowInitialInterceptedInteraction = buildInterceptedInteraction(primaryTraceId)
        testRepository.save(mainFlowInitialInterceptedInteraction)
        val secondaryFlowInitialInterceptedInteraction = buildInterceptedInteraction(secondaryTraceId)
        testRepository.save(secondaryFlowInitialInterceptedInteraction)
        testRepository.save(buildInterceptedInteraction(primaryTraceId))
        val secondaryFlowFinalInterceptedInteraction = buildInterceptedInteraction(secondaryTraceId)
        testRepository.save(secondaryFlowFinalInterceptedInteraction)
        testRepository.save(buildInterceptedInteraction(primaryTraceId))
        val primaryFlowFinalInterceptedInteraction = buildInterceptedInteraction(primaryTraceId)
        testRepository.save(primaryFlowFinalInterceptedInteraction)

        val result = underTest.findRecentFlows(2)

        assertThat(result, hasSize(2))
        assertThat(result[0].initialInteraction, `is`(mainFlowInitialInterceptedInteraction))
        assertThat(result[0].finalInteraction, `is`(primaryFlowFinalInterceptedInteraction))
        assertThat(result[0].totalCapturedInteractions, `is`(4))
        assertThat(result[1].initialInteraction, `is`(secondaryFlowInitialInterceptedInteraction))
        assertThat(result[1].finalInteraction, `is`(secondaryFlowFinalInterceptedInteraction))
        assertThat(result[1].totalCapturedInteractions, `is`(2))
    }

    @Test
    fun `should respect the resultSizeLimit`() {
        val mainFlowInitialInterceptedInteraction = buildInterceptedInteraction(primaryTraceId)
        testRepository.save(mainFlowInitialInterceptedInteraction)
        val secondaryFlowInitialInterceptedInteraction = buildInterceptedInteraction(secondaryTraceId)
        testRepository.save(secondaryFlowInitialInterceptedInteraction)
        testRepository.save(buildInterceptedInteraction(primaryTraceId))
        val secondaryFlowFinalInterceptedInteraction = buildInterceptedInteraction(secondaryTraceId)
        testRepository.save(secondaryFlowFinalInterceptedInteraction)
        testRepository.save(buildInterceptedInteraction(primaryTraceId))
        val primaryFlowFinalInterceptedInteraction = buildInterceptedInteraction(primaryTraceId)
        testRepository.save(primaryFlowFinalInterceptedInteraction)

        val result = underTest.findRecentFlows(1)

        assertThat(result, hasSize(1))
        assertThat(result[0].initialInteraction, `is`(mainFlowInitialInterceptedInteraction))
        assertThat(result[0].finalInteraction, `is`(primaryFlowFinalInterceptedInteraction))
        assertThat(result[0].totalCapturedInteractions, `is`(4))
    }

    @Test
    fun `should retrieve recent flows according to createdAt`() {
        val traceId1 = "traceId1"
        val traceId2 = "traceId2"
        val traceId3 = "traceId3"
        val traceId4 = "traceId4"
        val traceId5 = "traceId5"
        val now = now(ZoneId.of("UTC"))
        saveInterceptedInteraction(traceId1, now.plusSeconds(5))
        saveInterceptedInteraction(traceId2, now.plusSeconds(4))
        saveInterceptedInteraction(traceId3, now.plusSeconds(3))
        saveInterceptedInteraction(traceId4, now.plusSeconds(2))
        saveInterceptedInteraction(traceId5, now.plusSeconds(1))

        val result = underTest.findRecentFlows(2)

        assertThat(result, hasSize(2))
        assertThat(result[0].initialInteraction.traceId, `is`(traceId1))
        assertThat(result[1].initialInteraction.traceId, `is`(traceId2))
    }

    private fun buildInterceptedInteraction(traceId: String) = easyRandom.nextObject(InterceptedInteraction::class.java)
        .copy(traceId = traceId, createdAt = now(ZoneId.of("UTC")).truncatedTo(MILLIS))

    private fun saveInterceptedInteraction(traceId: String, createdAt: ZonedDateTime) {
        testRepository.save(easyRandom.nextObject(InterceptedInteraction::class.java)
            .copy(traceId = traceId, createdAt = createdAt.truncatedTo(MILLIS)))
    }

    companion object {
        @JvmStatic
        @AfterAll
        fun tearDown() {
            tearDownDatabase()
            tearDownClient()
        }
    }
}
