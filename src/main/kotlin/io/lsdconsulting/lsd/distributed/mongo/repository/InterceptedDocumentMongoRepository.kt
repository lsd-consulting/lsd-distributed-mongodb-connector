package io.lsdconsulting.lsd.distributed.mongo.repository

import com.mongodb.MongoException
import com.mongodb.client.MongoCollection
import io.lsdconsulting.lsd.distributed.connector.model.InterceptedInteraction
import io.lsdconsulting.lsd.distributed.connector.repository.InterceptedDocumentRepository
import lsd.logging.log
import org.litote.kmongo.`in`
import java.time.ZoneId
import org.litote.kmongo.find as findMany

class InterceptedDocumentMongoRepository(
    interceptedInteractionCollectionBuilder: InterceptedInteractionCollectionBuilder,
    failOnConnectionError: Boolean = false
) : InterceptedDocumentRepository {

    private val interceptedInteractions: MongoCollection<InterceptedInteraction>?

    init {
        val tempCollection: MongoCollection<InterceptedInteraction>? = try {
            interceptedInteractionCollectionBuilder.getInterceptedInteractionCollection()
        } catch (e: Exception) {
            log().error(e.message, e)
            if (failOnConnectionError) {
                throw e
            }
            null
        }
        interceptedInteractions = tempCollection
    }

    override fun save(interceptedInteraction: InterceptedInteraction) {
        if (isActive()) {
            try {
                log().debug("Saving interceptedInteraction={}", interceptedInteraction)
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
            try {
                val result = interceptedInteractions!!
                    .findMany(InterceptedInteraction::traceId `in` traceId.asList())
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

    fun findByTraceIdsUnsorted(vararg traceId: String): List<InterceptedInteraction> {
        if (isActive()) {
            val startTime = System.currentTimeMillis()
            try {
                val result = interceptedInteractions!!
                    .findMany(InterceptedInteraction::traceId `in` traceId.asList())
                    .toList()
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
}
