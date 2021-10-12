package io.lsdconsulting.lsd.distributed.repository.codec;

import io.lsdconsulting.lsd.distributed.model.Type;
import lombok.SneakyThrows;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;

public class TypeCodec implements Codec<Type> {

    @SneakyThrows
    @Override
    public void encode(final BsonWriter writer, final Type value, final EncoderContext encoderContext) {
        writer.writeString(value.name());
    }

    @Override
    public Type decode(final BsonReader reader, final DecoderContext decoderContext) {
        return Type.valueOf(reader.readString());
    }

    @Override
    public Class<Type> getEncoderClass() {
        return Type.class;
    }
}
