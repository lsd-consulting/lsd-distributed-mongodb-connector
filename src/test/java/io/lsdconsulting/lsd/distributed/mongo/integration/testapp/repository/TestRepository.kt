package io.lsdconsulting.lsd.distributed.mongo.integration.testapp.repository

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import de.flapdoodle.embed.mongo.MongodExecutable
import de.flapdoodle.embed.mongo.MongodStarter
import de.flapdoodle.embed.mongo.config.MongodConfig
import de.flapdoodle.embed.mongo.config.Net
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.process.runtime.Network
import io.lsdconsulting.lsd.distributed.mongo.config.log
import io.lsdconsulting.lsd.distributed.mongo.repository.codec.ZonedDateTimeCodec
import org.bson.Document
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.pojo.PojoCodecProvider
import org.springframework.data.mongodb.core.MongoTemplate
import java.io.IOException

class TestRepository {
    private val pojoCodecRegistry = CodecRegistries.fromRegistries(
        MongoClientSettings.getDefaultCodecRegistry(),
        CodecRegistries.fromCodecs(ZonedDateTimeCodec()),
        CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build())
    )

    val collection: MongoCollection<Document>
        get() = mongoClient.getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME)
            .withCodecRegistry(pojoCodecRegistry)

    val isCollectionCapped: Boolean
        get() {
            val mongoTemplate = MongoTemplate(mongoClient, DATABASE_NAME)
            val obj = Document()
            obj.append("collStats", COLLECTION_NAME)
            val result = mongoTemplate.executeCommand(obj)
            return result.getBoolean("capped")
        }

    companion object {
        const val MONGODB_HOST = "localhost"
        const val MONGODB_PORT = 27017
        private const val DATABASE_NAME = "lsd"
        private const val COLLECTION_NAME = "interceptedInteraction"
        private lateinit var mongoClient: MongoClient
        private lateinit var mongodExecutable: MongodExecutable
        fun setupDatabase() {
            try {
                val mongodConfig: MongodConfig = MongodConfig.builder()
                    .version(Version.Main.V5_0)
                    .net(Net(MONGODB_HOST, MONGODB_PORT, Network.localhostIsIPv6()))
                    .build()
                mongodExecutable = MongodStarter.getDefaultInstance().prepare(mongodConfig)
                mongodExecutable.start()
                mongoClient = MongoClients.create(
                    MongoClientSettings.builder()
                        .applyConnectionString(ConnectionString("mongodb://$MONGODB_HOST:$MONGODB_PORT"))
                        .retryWrites(true)
                        .build()
                )
            } catch (e: IOException) {
                log().error(e.message, e)
            }
        }

        fun tearDownDatabase() {
            mongodExecutable.stop()
        }

        fun tearDownClient() {
            mongoClient.close()
        }
    }
}
