package io.lsdconsulting.lsd.distributed.mongo.repository

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.MongoClientSettings.getDefaultCodecRegistry
import com.mongodb.MongoException
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.Indexes
import com.mongodb.connection.ClusterSettings
import com.mongodb.connection.SocketSettings
import com.mongodb.connection.SslSettings
import io.lsdconsulting.lsd.distributed.access.model.InterceptedInteraction
import io.lsdconsulting.lsd.distributed.access.repository.InterceptedDocumentRepository
import io.lsdconsulting.lsd.distributed.mongo.config.log
import io.lsdconsulting.lsd.distributed.mongo.repository.codec.InteractionTypeCodec
import io.lsdconsulting.lsd.distributed.mongo.repository.codec.ZonedDateTimeCodec
import org.apache.http.ssl.SSLContextBuilder
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider
import org.litote.kmongo.KMongo
import org.litote.kmongo.getCollection
import org.litote.kmongo.`in`
import org.springframework.core.io.ClassPathResource
import java.security.KeyStore
import java.time.ZoneId
import java.util.concurrent.TimeUnit.MILLISECONDS
import org.litote.kmongo.find as findMany

class InterceptedDocumentMongoRepository(
    dbConnectionString: String, trustStoreLocation: String?,
    trustStorePassword: String?, connectionTimeout: Int,
    collectionSizeLimit: Long,
) : InterceptedDocumentRepository {

    private val interceptedInteractions: MongoCollection<InterceptedInteraction>?
    private lateinit var mongoClient: MongoClient

    constructor(
        dbConnectionString: String, connectionTimeout: Int,
        collectionSizeLimit: Long
    ) : this(dbConnectionString, null, null, connectionTimeout, collectionSizeLimit)

    init {
        val tempCollection: MongoCollection<InterceptedInteraction>? = try {
            mongoClient =
                prepareMongoClient(dbConnectionString, trustStoreLocation, trustStorePassword, connectionTimeout)
            prepareInterceptedInteractionCollection(mongoClient, collectionSizeLimit)
        } catch (e: Exception) {
            log().error(e.message, e)
            null
        }
        interceptedInteractions = tempCollection
    }

    private fun prepareMongoClient(
        dbConnectionString: String,
        trustStoreLocation: String?,
        trustStorePassword: String?,
        connectionTimeout: Int
    ): MongoClient {
        val builder = MongoClientSettings.builder()
            .applyToSocketSettings { b: SocketSettings.Builder ->
                b.connectTimeout(connectionTimeout, MILLISECONDS)
                b.readTimeout(connectionTimeout, MILLISECONDS)
            }
            .applyToClusterSettings { b: ClusterSettings.Builder ->
                b.serverSelectionTimeout(
                    connectionTimeout.toLong(),
                    MILLISECONDS
                )
            }
            .applyConnectionString(ConnectionString(dbConnectionString))
        if (!trustStoreLocation.isNullOrBlank() && !trustStorePassword.isNullOrBlank()) {
            builder.applyToSslSettings { sslSettingsBuilder: SslSettings.Builder ->
                loadCustomTrustStore(sslSettingsBuilder, trustStoreLocation, trustStorePassword)
            }
        }

//    TODO We should also support other AuthenticationMechanisms
//    String user = "xxxx"; // the user name
//    String database = "admin"; // the name of the database in which the user is defined
//    char[] password = "xxxx".toCharArray(); // the password as a character array
//    MongoCredential credential = MongoCredential.createCredential(user, database, password);
        return KMongo.createClient(builder.retryWrites(true).build())
    }

    private fun prepareInterceptedInteractionCollection(mongoClient: MongoClient, collectionSizeLimit: Long): MongoCollection<InterceptedInteraction> {
        if (!collectionExists(mongoClient)) {
            val options = CreateCollectionOptions()
            options.capped(true).sizeInBytes(1024 * 1000 * collectionSizeLimit)
            mongoClient.getDatabase(DATABASE_NAME).createCollection(COLLECTION_NAME, options)
        }
        val interceptedInteractions: MongoCollection<InterceptedInteraction> =
            mongoClient.getDatabase(DATABASE_NAME).getCollection(COLLECTION_NAME, InterceptedInteraction::class.java)
                .withCodecRegistry(pojoCodecRegistry)
        interceptedInteractions.createIndex(Indexes.ascending("traceId"))
        interceptedInteractions.createIndex(Indexes.ascending("createdAt"))
        return interceptedInteractions
    }

    private fun collectionExists(mongoClient: MongoClient): Boolean =
        mongoClient.getDatabase(DATABASE_NAME).listCollectionNames()
            .into(ArrayList()).contains(COLLECTION_NAME)

    override fun save(interceptedInteraction: InterceptedInteraction) {
        if (isActive()) {
            try {
                val startTime = System.currentTimeMillis()
                interceptedInteractions!!.insertOne(interceptedInteraction)
                log().trace("save took {} ms", System.currentTimeMillis() - startTime)
            } catch (e: MongoException) {
                log().error(
                    "Skipping persisting the interceptedInteraction due to exception - interceptedInteraction:{}, message:{}, stackTrace:{}",
                    interceptedInteraction,
                    e.message,
                    e.stackTrace
                )
            }
        }
    }

    override fun findByTraceIds(vararg traceId: String): List<InterceptedInteraction> {
        if (isActive()) {
            val startTime = System.currentTimeMillis()

            val database = mongoClient.getDatabase(DATABASE_NAME) //normal java driver usage
            val col = database.getCollection<InterceptedInteraction>(COLLECTION_NAME) //KMongo extension method

            try {

                val result = col.findMany(InterceptedInteraction::traceId `in` traceId.asList())
                    .toList()
                    .sortedBy { it.createdAt }
                    .map { it.copy(createdAt = it.createdAt.withZoneSameInstant(ZoneId.of("UTC"))) }

                log().trace("findByTraceIds took {} ms", System.currentTimeMillis() - startTime)
                return result
            } catch (e: MongoException) {
                log().error("Failed to retrieve interceptedInteractions - message:${e.message}", e.stackTrace)
            }
        }
        return listOf()
    }

    override fun isActive(): Boolean {
        return if (interceptedInteractions == null) {
            log().warn("The LSD MongoDb repository is disabled!")
            false
        } else true
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 500
        const val DEFAULT_COLLECTION_SIZE_LIMIT_MBS = 1000 * 10L // 10Gb
        private const val DATABASE_NAME = "lsd"
        private const val COLLECTION_NAME = "interceptedInteraction"

        val pojoCodecRegistry: CodecRegistry = CodecRegistries.fromRegistries(
            CodecRegistries.fromCodecs(ZonedDateTimeCodec(), InteractionTypeCodec()),
            getDefaultCodecRegistry(),
            CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()),
        )

        private fun loadCustomTrustStore(builder: SslSettings.Builder, trustStoreLocation: String, trustStorePassword: String) {
            ClassPathResource(trustStoreLocation).inputStream.use { inputStream ->
                val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
                trustStore.load(inputStream, trustStorePassword.toCharArray())
                builder.context(SSLContextBuilder().loadTrustMaterial(trustStore, null).build())
            }
        }
    }
}
