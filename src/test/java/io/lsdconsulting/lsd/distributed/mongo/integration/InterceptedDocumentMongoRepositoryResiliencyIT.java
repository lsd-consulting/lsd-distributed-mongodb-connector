package io.lsdconsulting.lsd.distributed.mongo.integration;

import io.lsdconsulting.lsd.distributed.access.model.InterceptedInteraction;
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.TestApplication;
import io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoRepository;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.*;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(webEnvironment = RANDOM_PORT, classes = {TestApplication.class})
class InterceptedDocumentMongoRepositoryResiliencyIT {

    private static final int DB_CONNECTION_TIMEOUT = 500;

    private final EasyRandom easyRandom = new EasyRandom(new EasyRandomParameters().seed(Instant.now().toEpochMilli()));

    @BeforeEach
    void setupTestDatabase() {
        setupDatabase();
    }
    @AfterEach
     void tearDownTestDatabase() {
        tearDownDatabase();
    }

    @AfterAll
     static void tearDownTestClient() {
        tearDownClient();
    }

    @Test
    public void shouldHandleDbBeingDownGracefullyOnStartup() {
        new InterceptedDocumentMongoRepository("mongodb://" + randomAlphabetic(10), DB_CONNECTION_TIMEOUT);
    }

    @Test
    public void shouldNotSlowDownStartupIfDbDown() {
        await()
                .atLeast(450, MILLISECONDS)
                .atMost(1000, MILLISECONDS)
                .untilAsserted(() -> assertThat(new InterceptedDocumentMongoRepository("mongodb://" + randomAlphabetic(10) + ":" + MONGODB_PORT, DB_CONNECTION_TIMEOUT), is(notNullValue())));
    }

    @Test
    public void shouldHandleDbGoingDownAfterStartup() {
        InterceptedDocumentMongoRepository underTest = new InterceptedDocumentMongoRepository("mongodb://" + MONGODB_HOST + ":" + MONGODB_PORT, DB_CONNECTION_TIMEOUT);
        InterceptedInteraction interceptedInteraction =  easyRandom.nextObject(InterceptedInteraction.class);
        underTest.save(interceptedInteraction);
        List<InterceptedInteraction> initialResult = underTest.findByTraceIds("traceId");
        assertThat(initialResult, is(notNullValue()));
        tearDownDatabase();

        List<InterceptedInteraction> result = underTest.findByTraceIds("traceId");

        assertThat(result, is(empty()));
    }

    @Test
    public void shouldRecoverFromDbGoingDown() {
        InterceptedDocumentMongoRepository underTest = new InterceptedDocumentMongoRepository("mongodb://" + MONGODB_HOST + ":" + MONGODB_PORT, DB_CONNECTION_TIMEOUT);
        InterceptedInteraction interceptedInteraction =  easyRandom.nextObject(InterceptedInteraction.class);
        underTest.save(interceptedInteraction);
        tearDownDatabase();
        List<InterceptedInteraction> result = underTest.findByTraceIds("traceId");
        assertThat(result, is(empty()));

        setupDatabase();

        List<InterceptedInteraction> initialResult = underTest.findByTraceIds("traceId");
        assertThat(initialResult, is(notNullValue()));
    }

    @Test
    public void shouldNotSlowDownProduction() {
        InterceptedDocumentMongoRepository underTest = new InterceptedDocumentMongoRepository("mongodb://" + MONGODB_HOST + ":" + MONGODB_PORT, DB_CONNECTION_TIMEOUT);
        InterceptedInteraction interceptedInteraction =  easyRandom.nextObject(InterceptedInteraction.class);
        underTest.save(interceptedInteraction);
        tearDownDatabase();

        await()
                .atLeast(450, MILLISECONDS)
                .atMost(1000, MILLISECONDS)
                .untilAsserted(() -> assertThat(underTest.findByTraceIds("traceId"), is(empty())));
    }
}
