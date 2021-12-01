package io.lsdconsulting.lsd.distributed.mongo.integration;

import com.mongodb.client.ListIndexesIterable;
import io.lsdconsulting.lsd.distributed.access.model.InterceptedInteraction;
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.TestApplication;
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.config.RepositoryConfig;
import io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository;
import io.lsdconsulting.lsd.distributed.mongo.repository.InterceptedDocumentMongoRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import static io.lsdconsulting.lsd.distributed.access.model.Type.REQUEST;
import static io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository.TestRepository.*;
import static java.time.Instant.ofEpochSecond;
import static java.time.ZonedDateTime.ofInstant;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Import(RepositoryConfig.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = {TestApplication.class})
class InterceptedDocumentMongoRepositoryIT {

    @Autowired
    private TestRepository testRepository;

    private InterceptedDocumentMongoRepository underTest;

    @BeforeEach
    void setup() {
        underTest = new InterceptedDocumentMongoRepository("mongodb://" + MONGODB_HOST + ":" + MONGODB_PORT,  1500, 1L);
    }

    @AfterAll
    static void tearDown() {
        tearDownDatabase();
        tearDownClient();
    }

    @Test
    public void shouldCreateCollectionWithIndexes() {
        final ListIndexesIterable<Document> indexes = testRepository.getCollection().listIndexes();
        final List<String> indexNames = stream(indexes.spliterator(), false)
                .map(doc -> (String) doc.get("name"))
                .collect(Collectors.toList());

        assertThat(indexNames, hasItem(startsWith("traceId")));
        assertThat(indexNames, hasItem(startsWith("createdAt")));
    }

    @Test
    public void shouldCreateCappedCollection() {
        final boolean isCapped = testRepository.isCollectionCapped();

        assertTrue(isCapped);
    }

    @Test
    public void shouldSaveAndRetrieveFromCollection() {
        InterceptedInteraction interceptedInteraction = InterceptedInteraction.builder()
                .elapsedTime(20L)
                .httpStatus("OK")
                .path("/path")
                .httpMethod("GET")
                .body("body")
                .type(REQUEST)
                .traceId("traceId")
                .createdAt(ofInstant(ofEpochSecond(0), ZoneId.of("UTC")))
                .build();

        underTest.save(interceptedInteraction);

        List<InterceptedInteraction> result = underTest.findByTraceIds("traceId");

        assertThat(result, hasSize(1));
        assertThat(result.get(0).getElapsedTime(), is(20L));
        assertThat(result.get(0).getHttpStatus(), is("OK"));
        assertThat(result.get(0).getPath(), is("/path"));
        assertThat(result.get(0).getHttpMethod(), is("GET"));
        assertThat(result.get(0).getBody(), is("body"));
        assertThat(result.get(0).getType(), is(REQUEST));
        assertThat(result.get(0).getCreatedAt(), is(ofInstant(ofEpochSecond(0), ZoneId.of("UTC"))));
    }
}
