package io.lsdconsulting.lsd.distributed.integration;

import com.mongodb.client.ListIndexesIterable;
import io.lsdconsulting.lsd.distributed.integration.testapp.TestApplication;
import io.lsdconsulting.lsd.distributed.integration.testapp.config.RepositoryConfig;
import io.lsdconsulting.lsd.distributed.integration.testapp.repository.TestRepository;
import io.lsdconsulting.lsd.distributed.model.InterceptedInteraction;
import io.lsdconsulting.lsd.distributed.repository.InterceptedDocumentMongoRepository;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import static io.lsdconsulting.lsd.distributed.integration.testapp.repository.TestRepository.MONGODB_HOST;
import static io.lsdconsulting.lsd.distributed.integration.testapp.repository.TestRepository.MONGODB_PORT;
import static io.lsdconsulting.lsd.distributed.model.Type.REQUEST;
import static java.util.stream.StreamSupport.stream;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@Import(RepositoryConfig.class)
@SpringBootTest(webEnvironment = RANDOM_PORT, classes = {TestApplication.class})
class InterceptedDocumentMongoRepositoryIT {

    @Autowired
    private TestRepository testRepository;

    private InterceptedDocumentMongoRepository underTest;

    @BeforeEach
    void setup() {
        underTest = new InterceptedDocumentMongoRepository("mongodb://" + MONGODB_HOST + ":" + MONGODB_PORT, null, null);
    }

    @AfterAll
    static void tearDown() {
        TestRepository.tearDownDatabase();
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
    public void shouldSaveAndRetrieveFromCollection() {
        InterceptedInteraction interceptedInteraction = InterceptedInteraction.builder()
                .elapsedTime(20L)
                .httpStatus("OK")
                .path("/path")
                .httpMethod("GET")
                .body("body")
                .type(REQUEST)
                .traceId("traceId")
                .createdAt(ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneId.of("UTC")))
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
        assertThat(result.get(0).getCreatedAt(), is(ZonedDateTime.ofInstant(Instant.ofEpochSecond(0), ZoneId.of("UTC"))));
    }
}
