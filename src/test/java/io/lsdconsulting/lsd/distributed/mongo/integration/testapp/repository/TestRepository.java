package io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.flapdoodle.embed.mongo.MongodExecutable;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfig;
import de.flapdoodle.embed.mongo.config.Net;
import io.lsdconsulting.lsd.distributed.mongo.repository.codec.ZonedDateTimeCodec;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.io.IOException;

import static com.mongodb.MongoClientSettings.builder;
import static com.mongodb.MongoClientSettings.getDefaultCodecRegistry;
import static de.flapdoodle.embed.mongo.distribution.Version.Main.PRODUCTION;
import static de.flapdoodle.embed.process.runtime.Network.localhostIsIPv6;
import static org.bson.codecs.configuration.CodecRegistries.*;

@Slf4j
public class TestRepository {
    public static final String MONGODB_HOST = "localhost";
    public static final int MONGODB_PORT = 27017;

    private static final String DATABASE_NAME = "lsd";
    private static final String COLLECTION_NAME = "interceptedInteraction";

    private static MongoClient mongoClient;
    private static MongodExecutable mongodExecutable;

    private final CodecRegistry pojoCodecRegistry = fromRegistries(
            getDefaultCodecRegistry(),
            fromCodecs(new ZonedDateTimeCodec()),
            fromProviders(PojoCodecProvider.builder().automatic(true).build())
    );

    public static void setupDatabase() {
        try {
            final MongodConfig mongodConfig = MongodConfig.builder()
                    .version(PRODUCTION)
                    .net(new Net(MONGODB_HOST, MONGODB_PORT, localhostIsIPv6()))
                    .build();

            mongodExecutable = MongodStarter.getDefaultInstance().prepare(mongodConfig);
            mongodExecutable.start();


            mongoClient = MongoClients.create(builder()
                    .applyConnectionString(new ConnectionString("mongodb://" + MONGODB_HOST + ":" + MONGODB_PORT))
                    .retryWrites(true)
                    .build());
        } catch (final IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public static void tearDownDatabase() {
        mongodExecutable.stop();
    }

    public static void tearDownClient() {
        mongoClient.close();
    }

    public MongoCollection<Document> getCollection() {
        final MongoDatabase database = mongoClient.getDatabase(DATABASE_NAME);
        return database.getCollection(COLLECTION_NAME).withCodecRegistry(pojoCodecRegistry);
    }

    public boolean isCollectionCapped() {
        MongoTemplate mongoTemplate = new MongoTemplate(mongoClient, DATABASE_NAME);
        Document obj = new Document();
        obj.append("collStats", COLLECTION_NAME);
        Document result = mongoTemplate.executeCommand(obj);
        return result.getBoolean("capped");
    }
}
