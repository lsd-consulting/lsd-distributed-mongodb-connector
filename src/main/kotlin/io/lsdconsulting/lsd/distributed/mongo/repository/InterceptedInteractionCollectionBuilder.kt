package io.lsdconsulting.lsd.distributed.mongo.repository

import com.mongodb.ConnectionString
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.CreateCollectionOptions
import com.mongodb.client.model.Indexes
import com.mongodb.connection.ClusterSettings
import com.mongodb.connection.SocketSettings
import com.mongodb.connection.SslSettings
import io.lsdconsulting.lsd.distributed.connector.model.InterceptedInteraction
import io.lsdconsulting.lsd.distributed.mongo.repository.codec.InteractionTypeCodec
import io.lsdconsulting.lsd.distributed.mongo.repository.codec.ZonedDateTimeCodec
import org.apache.http.ssl.SSLContextBuilder
import org.bson.codecs.configuration.CodecRegistries
import org.bson.codecs.configuration.CodecRegistry
import org.bson.codecs.pojo.PojoCodecProvider
import org.litote.kmongo.KMongo
import org.litote.kmongo.getCollection
import org.springframework.core.io.ClassPathResource
import java.security.KeyStore
import java.util.concurrent.TimeUnit.MILLISECONDS

const val DATABASE_NAME = "lsd"
const val COLLECTION_NAME = "interceptedInteraction"
const val DEFAULT_TIMEOUT_MILLIS = 500L
const val DEFAULT_COLLECTION_SIZE_LIMIT_MBS = 1000 * 10L // 10Gb

class InterceptedInteractionCollectionBuilder(
    private val dbConnectionString: String, private val trustStoreLocation: String?,
    private val trustStorePassword: String?, private val connectionTimeout: Long,
    private val collectionSizeLimit: Long
) {
    val pojoCodecRegistry: CodecRegistry = CodecRegistries.fromRegistries(
        CodecRegistries.fromCodecs(ZonedDateTimeCodec(), InteractionTypeCodec()),
        MongoClientSettings.getDefaultCodecRegistry(),
        CodecRegistries.fromProviders(PojoCodecProvider.builder().automatic(true).build()),
    )

    fun prepareMongoClient(): MongoClient {
        val builder = MongoClientSettings.builder()
            .applyToSocketSettings { b: SocketSettings.Builder ->
                b.connectTimeout(connectionTimeout, MILLISECONDS)
                b.readTimeout(connectionTimeout, MILLISECONDS)
            }
            .applyToClusterSettings { b: ClusterSettings.Builder ->
                b.serverSelectionTimeout(connectionTimeout, MILLISECONDS)
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

    inline fun <reified T> get(): MongoCollection<T> {
        val mongoClient = prepareMongoClient()
        createCappedCollectionIdMissing(mongoClient)
        val interceptedInteractions = mongoClient
                .getDatabase(DATABASE_NAME)
                .getCollection(COLLECTION_NAME, T::class.java)
                .withCodecRegistry(pojoCodecRegistry)
        interceptedInteractions.createIndex(Indexes.ascending("traceId"))
        interceptedInteractions.createIndex(Indexes.ascending("createdAt"))
        interceptedInteractions.createIndex(Indexes.descending("createdAt"))
        return interceptedInteractions
    }

    fun getInterceptedInteractionCollection(): MongoCollection<InterceptedInteraction> {
        val mongoClient = prepareMongoClient()
        createCappedCollectionIdMissing(mongoClient)
        val interceptedInteractions = mongoClient
                .getDatabase(DATABASE_NAME)
                .getCollection<InterceptedInteraction>(COLLECTION_NAME)
        interceptedInteractions.createIndex(Indexes.ascending("traceId"))
        interceptedInteractions.createIndex(Indexes.ascending("createdAt"))
        return interceptedInteractions
    }

    fun createCappedCollectionIdMissing(mongoClient: MongoClient) {
        if (!collectionExists(mongoClient)) {
            val options = CreateCollectionOptions()
            options.capped(true).sizeInBytes(1024 * 1000 * collectionSizeLimit)
            mongoClient.getDatabase(DATABASE_NAME).createCollection(COLLECTION_NAME, options)
        }
    }

    private fun collectionExists(mongoClient: MongoClient): Boolean =
        mongoClient.getDatabase(DATABASE_NAME).listCollectionNames()
            .into(ArrayList()).contains(COLLECTION_NAME)


    private fun loadCustomTrustStore(
        builder: SslSettings.Builder,
        trustStoreLocation: String,
        trustStorePassword: String
    ) {
        ClassPathResource(trustStoreLocation).inputStream.use { inputStream ->
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType())
            trustStore.load(inputStream, trustStorePassword.toCharArray())
            builder.context(SSLContextBuilder().loadTrustMaterial(trustStore, null).build())
        }
    }

    constructor(
        dbConnectionString: String, connectionTimeout: Long,
        collectionSizeLimit: Long
    ) : this(dbConnectionString, null, null, connectionTimeout, collectionSizeLimit)
}
