/* Generated SBE (Simple Binary Encoding) message codec. */
package com.codingmonster.common.sbe.trade;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

@SuppressWarnings("all")
public final class MarketDataRequestDecoder {
  public static final int BLOCK_LENGTH = 16;
  public static final int TEMPLATE_ID = 4;
  public static final int SCHEMA_ID = 0;
  public static final int SCHEMA_VERSION = 1;
  public static final java.nio.ByteOrder BYTE_ORDER = java.nio.ByteOrder.LITTLE_ENDIAN;

  private final MarketDataRequestDecoder parentMessage = this;
  private DirectBuffer buffer;
  private int initialOffset;
  private int offset;
  private int limit;
  int actingBlockLength;
  int actingVersion;

  public int sbeBlockLength() {
    return BLOCK_LENGTH;
  }

  public int sbeTemplateId() {
    return TEMPLATE_ID;
  }

  public int sbeSchemaId() {
    return SCHEMA_ID;
  }

  public int sbeSchemaVersion() {
    return SCHEMA_VERSION;
  }

  public String sbeSemanticType() {
    return "";
  }

  public DirectBuffer buffer() {
    return buffer;
  }

  public int initialOffset() {
    return initialOffset;
  }

  public int offset() {
    return offset;
  }

  public MarketDataRequestDecoder wrap(
      final DirectBuffer buffer,
      final int offset,
      final int actingBlockLength,
      final int actingVersion) {
    if (buffer != this.buffer) {
      this.buffer = buffer;
    }
    this.initialOffset = offset;
    this.offset = offset;
    this.actingBlockLength = actingBlockLength;
    this.actingVersion = actingVersion;
    limit(offset + actingBlockLength);

    return this;
  }

  public MarketDataRequestDecoder wrapAndApplyHeader(
      final DirectBuffer buffer, final int offset, final MessageHeaderDecoder headerDecoder) {
    headerDecoder.wrap(buffer, offset);

    final int templateId = headerDecoder.templateId();
    if (TEMPLATE_ID != templateId) {
      throw new IllegalStateException("Invalid TEMPLATE_ID: " + templateId);
    }

    return wrap(
        buffer,
        offset + MessageHeaderDecoder.ENCODED_LENGTH,
        headerDecoder.blockLength(),
        headerDecoder.version());
  }

  public MarketDataRequestDecoder sbeRewind() {
    return wrap(buffer, initialOffset, actingBlockLength, actingVersion);
  }

  public int sbeDecodedLength() {
    final int currentLimit = limit();
    sbeSkip();
    final int decodedLength = encodedLength();
    limit(currentLimit);

    return decodedLength;
  }

  public int encodedLength() {
    return limit - offset;
  }

  public int limit() {
    return limit;
  }

  public void limit(final int limit) {
    this.limit = limit;
  }

  public static int mdReqIDId() {
    return 262;
  }

  public static int mdReqIDSinceVersion() {
    return 0;
  }

  public static int mdReqIDEncodingOffset() {
    return 0;
  }

  public static int mdReqIDEncodingLength() {
    return 8;
  }

  public static String mdReqIDMetaAttribute(final MetaAttribute metaAttribute) {
    if (MetaAttribute.PRESENCE == metaAttribute) {
      return "required";
    }

    return "";
  }

  public static long mdReqIDNullValue() {
    return 0xffffffffffffffffL;
  }

  public static long mdReqIDMinValue() {
    return 0x0L;
  }

  public static long mdReqIDMaxValue() {
    return 0xfffffffffffffffeL;
  }

  public long mdReqID() {
    return buffer.getLong(offset + 0, java.nio.ByteOrder.LITTLE_ENDIAN);
  }

  public static int symbolId() {
    return 55;
  }

  public static int symbolSinceVersion() {
    return 0;
  }

  public static String symbolCharacterEncoding() {
    return java.nio.charset.StandardCharsets.UTF_8.name();
  }

  public static String symbolMetaAttribute(final MetaAttribute metaAttribute) {
    if (MetaAttribute.PRESENCE == metaAttribute) {
      return "required";
    }

    return "";
  }

  public static int symbolHeaderLength() {
    return 2;
  }

  public int symbolLength() {
    final int limit = parentMessage.limit();
    return (buffer.getShort(limit, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
  }

  public int skipSymbol() {
    final int headerLength = 2;
    final int limit = parentMessage.limit();
    final int dataLength = (buffer.getShort(limit, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    final int dataOffset = limit + headerLength;
    parentMessage.limit(dataOffset + dataLength);

    return dataLength;
  }

  public int getSymbol(final MutableDirectBuffer dst, final int dstOffset, final int length) {
    final int headerLength = 2;
    final int limit = parentMessage.limit();
    final int dataLength = (buffer.getShort(limit, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    final int bytesCopied = Math.min(length, dataLength);
    parentMessage.limit(limit + headerLength + dataLength);
    buffer.getBytes(limit + headerLength, dst, dstOffset, bytesCopied);

    return bytesCopied;
  }

  public int getSymbol(final byte[] dst, final int dstOffset, final int length) {
    final int headerLength = 2;
    final int limit = parentMessage.limit();
    final int dataLength = (buffer.getShort(limit, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    final int bytesCopied = Math.min(length, dataLength);
    parentMessage.limit(limit + headerLength + dataLength);
    buffer.getBytes(limit + headerLength, dst, dstOffset, bytesCopied);

    return bytesCopied;
  }

  public void wrapSymbol(final DirectBuffer wrapBuffer) {
    final int headerLength = 2;
    final int limit = parentMessage.limit();
    final int dataLength = (buffer.getShort(limit, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    parentMessage.limit(limit + headerLength + dataLength);
    wrapBuffer.wrap(buffer, limit + headerLength, dataLength);
  }

  public String symbol() {
    final int headerLength = 2;
    final int limit = parentMessage.limit();
    final int dataLength = (buffer.getShort(limit, java.nio.ByteOrder.LITTLE_ENDIAN) & 0xFFFF);
    parentMessage.limit(limit + headerLength + dataLength);

    if (0 == dataLength) {
      return "";
    }

    final byte[] tmp = new byte[dataLength];
    buffer.getBytes(limit + headerLength, tmp, 0, dataLength);

    return new String(tmp, java.nio.charset.StandardCharsets.UTF_8);
  }

  public String toString() {
    if (null == buffer) {
      return "";
    }

    final MarketDataRequestDecoder decoder = new MarketDataRequestDecoder();
    decoder.wrap(buffer, initialOffset, actingBlockLength, actingVersion);

    return decoder.appendTo(new StringBuilder()).toString();
  }

  public StringBuilder appendTo(final StringBuilder builder) {
    if (null == buffer) {
      return builder;
    }

    final int originalLimit = limit();
    limit(initialOffset + actingBlockLength);
    builder.append("[MarketDataRequest](sbeTemplateId=");
    builder.append(TEMPLATE_ID);
    builder.append("|sbeSchemaId=");
    builder.append(SCHEMA_ID);
    builder.append("|sbeSchemaVersion=");
    if (parentMessage.actingVersion != SCHEMA_VERSION) {
      builder.append(parentMessage.actingVersion);
      builder.append('/');
    }
    builder.append(SCHEMA_VERSION);
    builder.append("|sbeBlockLength=");
    if (actingBlockLength != BLOCK_LENGTH) {
      builder.append(actingBlockLength);
      builder.append('/');
    }
    builder.append(BLOCK_LENGTH);
    builder.append("):");
    builder.append("mdReqID=");
    builder.append(this.mdReqID());
    builder.append('|');
    builder.append("symbol=");
    builder.append('\'').append(symbol()).append('\'');

    limit(originalLimit);

    return builder;
  }

  public MarketDataRequestDecoder sbeSkip() {
    sbeRewind();
    skipSymbol();

    return this;
  }
}
