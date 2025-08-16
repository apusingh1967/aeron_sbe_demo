package com.codingmonster.client;

import com.codingmonster.common.sbe.trade.*;
import io.aeron.Aeron;
import io.aeron.Publication;
import java.nio.ByteBuffer;

import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClientTests {

  private Logger LOG = LoggerFactory.getLogger(this.getClass());
  @Test
  public void given_sell_and_matched_buy_then_filled_er() {
    Aeron.Context ctx =
        new Aeron.Context()
            .aeronDirectoryName("/tmp/aeron"); // match the name with server process if using IPC
    try (Aeron aeron = Aeron.connect(ctx)) {
      Publication publication = aeron.addPublication("aeron:ipc", 11);
      // "aeron:udp?endpoint=localhost:40123"

      // Prepare a direct buffer to write the message into
      UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));

      // Create and wrap the header and message encoder
      MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
      NewOrderSingleEncoder orderEncoder = new NewOrderSingleEncoder();

      // Start writing from offset 0
      int offset = 0;

      orderEncoder
          .wrapAndApplyHeader(buffer, offset, headerEncoder)
              .senderCompID("trader1")
          .clOrdID(12345)
          .side(Side.Buy) // e.g., Buy
          .orderQty(100)
          .orderType(OrderType.Limit);
      orderEncoder.price().exponent((byte) 5).mantissa(22345);
      int length = MessageHeaderEncoder.ENCODED_LENGTH + orderEncoder.encodedLength();

      // Send the message over Aeron
      LOG.info("Publishing to: " + publication.toString());
      long result = publication.offer(buffer, offset, length);

      if (result < 0) {
        if (result == Publication.BACK_PRESSURED) {
          System.out.println("Back pressured");
        } else if (result == Publication.NOT_CONNECTED) {
          System.out.println("Not connected");
        } else if (result == Publication.ADMIN_ACTION) {
          System.out.println("Admin action");
        } else {
          System.out.println("Unknown error " + result);
        }
      } else {
        System.out.println("Message sent at position " + result);
      }

      Subscription subscription = aeron.addSubscription("aeron:ipc", 10);
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
      processMessage(subscription);
    }
  }

  private void processMessage(Subscription subscription) {
    FragmentHandler handler =
            (buffer, offset, length, header) -> {
              // Decode header
              MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
              headerDecoder.wrap(buffer, offset); // will read only header from buffer
              offset += MessageHeaderDecoder.ENCODED_LENGTH;

              int templateId = headerDecoder.templateId();
              switch (templateId) {
                case ExecutionReportDecoder.TEMPLATE_ID:
                  ExecutionReportDecoder executionReportDecoder = new ExecutionReportDecoder();
                  executionReportDecoder.wrap(
                          buffer,
                          offset,
                          headerDecoder.blockLength(),
                          headerDecoder.version());
                  LOG.info("Exec Report: " + executionReportDecoder.toString());
                  break;
                default:
                  LOG.error("Unknown message with templateId: " + templateId);
              }
            };
    subscription.poll(handler, 1);
  }
}
