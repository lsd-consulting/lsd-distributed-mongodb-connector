package io.lsdconsulting.lsd.distributed.mongo.repository.codec

import org.bson.BsonReader
import org.bson.BsonType
import org.bson.BsonWriter
import org.bson.codecs.Codec
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecConfigurationException
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime


/**
 * ZonedDateTime Codec.
 *
 *
 * Encodes and decodes `ZonedDateTime` objects to and from `DateTime`. Data is stored to millisecond accuracy.
 *
 * Converts the `ZonedDateTime` values to and from [ZoneOffset.UTC].
 */
class ZonedDateTimeCodec : Codec<ZonedDateTime> {

    override fun decode(reader: BsonReader, decoderContext: DecoderContext): ZonedDateTime {
        return Instant.ofEpochMilli(validateAndReadDateTime(reader)).atZone(ZoneOffset.UTC)
    }

    /**
     * {@inheritDoc}
     *
     * Converts the `ZonedDateTime` to [ZoneOffset.UTC] via [ZonedDateTime.toInstant].
     * @throws CodecConfigurationException if the ZonedDateTime cannot be converted to a valid Bson DateTime.
     */
    override fun encode(writer: BsonWriter, value: ZonedDateTime, encoderContext: EncoderContext) {
        try {
            writer.writeDateTime(value.toInstant().toEpochMilli())
        } catch (e: ArithmeticException) {
            throw CodecConfigurationException(
                    "Unsupported ZonedDateTime value $value could not be converted to milliseconds: ${e.message}", e
            )
        }
    }

    override fun getEncoderClass(): Class<ZonedDateTime> = ZonedDateTime::class.java

    private fun validateAndReadDateTime(reader: BsonReader): Long {
        val currentType = reader.currentBsonType
        if (currentType != BsonType.DATE_TIME) {
            throw CodecConfigurationException(
                    "Could not decode into ${encoderClass.simpleName}, expected '${BsonType.DATE_TIME}' BsonType but got '$currentType'."
            )
        }
        return reader.readDateTime()
    }
}
