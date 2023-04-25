package io.lsdconsulting.lsd.distributed.mongo.repository

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.Accumulators.max
import com.mongodb.client.model.Aggregates.*
import com.mongodb.client.model.Sorts.descending
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

        val sort1 = sort(descending("createdAt"))
        val group = group("\$traceId", max("maxCreatedAt", "\$createdAt"))
        val sort2 = sort(descending("maxCreatedAt"))

        val initialLimit = resultSizeLimit * 1000 // To speed up the query
        val distinctTraceIds = interceptedInteractions
            .aggregate(listOf(sort1, limit(initialLimit), group, sort2, limit(resultSizeLimit)))
            .into(LinkedHashSet<Document>())
            .map { it.getString("_id") }

        val interactionsGroupedByTraceId = interceptedDocumentRepository
            .findByTraceIds(*distinctTraceIds.toTypedArray())
            .sortedByDescending { it.createdAt }
            .groupBy { it.traceId }

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
            interceptedInteractionCollectionBuilder.get()
        } catch (e: Exception) {
            log().error(e.message, e)
            throw e
        }
    }
}
