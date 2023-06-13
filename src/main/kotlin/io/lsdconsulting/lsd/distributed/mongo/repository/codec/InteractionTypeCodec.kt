package io.lsdconsulting.lsd.distributed.mongo.repository.codec

import io.lsdconsulting.lsd.distributed.connector.model.InteractionType
import org.bson.BsonReader
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext

class InteractionTypeCodec : Codec<InteractionType> {
    override fun encode(writer: BsonWriter, value: InteractionType, encoderContext: EncoderContext) {
        writer.writeString(value.name)
    }

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): InteractionType {
        return InteractionType.valueOf(reader.readString())
    }

    override fun getEncoderClass(): Class<InteractionType> {
        return InteractionType::class.java
    }
}
