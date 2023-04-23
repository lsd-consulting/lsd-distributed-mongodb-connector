package io.lsdconsulting.lsd.distributed.mongo.repository

import com.mongodb.client.MongoCollection
import io.lsdconsulting.lsd.distributed.access.model.InterceptedFlow
import io.lsdconsulting.lsd.distributed.access.repository.InterceptedDocumentAdminRepository
import io.lsdconsulting.lsd.distributed.access.repository.InterceptedDocumentRepository
import io.lsdconsulting.lsd.distributed.mongo.config.log
import org.bson.Document

class InterceptedDocumentMongoAdminRepository(
    interceptedInteractionCollectionBuilder: InterceptedInteractionCollectionBuilder,
    private val interceptedDocumentRepository: InterceptedDocumentRepository
) : InterceptedDocumentAdminRepository {

    private val interceptedInteractions: MongoCollection<Document>

    override fun findRecentFlows(resultSizeLimit: Int): List<InterceptedFlow> {

        val interceptedInteractionIterator = interceptedInteractions.find()
            .sort(Document("createdAt", -1))
            .projection(Document("traceId", 1))
            .distinct()
            .iterator()

        val distinctTraceIds = mutableSetOf<String>()
        while (interceptedInteractionIterator.hasNext() && distinctTraceIds.size < resultSizeLimit) {
            val traceId: String = interceptedInteractionIterator.next().getString("traceId")
            distinctTraceIds.add(traceId)
        }
        val interactionsGroupedByTraceId = interceptedDocumentRepository
            .findByTraceIds(*distinctTraceIds.toTypedArray()).groupBy { it.traceId }

        return interactionsGroupedByTraceId.values.map {
            InterceptedFlow(
                initialInteraction = it.first(),
                finalInteraction = it.last(),
                totalCapturedInteractions = it.size
            )
        }
    }

    init {
        interceptedInteractions = try {
            val mongoClient =
                interceptedInteractionCollectionBuilder.prepareMongoClient()
            interceptedInteractionCollectionBuilder.prepareInterceptedInteractionCollection(mongoClient)
        } catch (e: Exception) {
            log().error(e.message, e)
            throw e
        }
    }
}
