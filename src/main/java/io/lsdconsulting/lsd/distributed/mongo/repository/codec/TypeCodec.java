package io.lsdconsulting.lsd.distributed.mongo.repository.codec;

import io.lsdconsulting.lsd.distributed.access.model.InteractionType;
import lombok.SneakyThrows;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

public class TypeCodec implements Codec<InteractionType> {

    @SneakyThrows
    @Override
    public void encode(final BsonWriter writer, final InteractionType value, final EncoderContext encoderContext) {
        writer.writeString(value.name());
    }

    @Override
    public InteractionType decode(final BsonReader reader, final DecoderContext decoderContext) {
        return InteractionType.valueOf(reader.readString());
    }

    @Override
    public Class<InteractionType> getEncoderClass() {
        return InteractionType.class;
    }
}
