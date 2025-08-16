package com.codingmonster.matchingengine;

import com.codingmonster.common.sbe.admin.StopSessionDecoder;
import com.codingmonster.common.sbe.trade.*;
import com.codingmonster.common.sbe.trade.OrderType;
import io.aeron.Aeron;
import io.aeron.Publication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.logbuffer.FragmentHandler;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

  private final Logger LOG = LoggerFactory.getLogger(this.getClass());
  private static CountDownLatch latch;
  private AtomicBoolean started = new AtomicBoolean(true);

  // none of these following fields is thread safe
  // but we are going single thread per shard
  private final List<Subscription> subscriptions = new ArrayList<>();
  private final Map<String, Publication> publications = new HashMap<>();
  private final UnsafeBuffer encodeBuffer = new UnsafeBuffer(new byte[4096]);
  private final NewOrderSingleDecoder newOrderSingleDecoder = new NewOrderSingleDecoder();
  private final OrderCancelReplaceRequestDecoder orderCancelReplaceRequestDecoder =
      new OrderCancelReplaceRequestDecoder();
  private final OrderCancelRequestDecoder orderCancelRequestDecoder =
      new OrderCancelRequestDecoder();
  private final ExecutionReportEncoder execEncoder = new ExecutionReportEncoder();

  public static void main(String[] args) {
    MediaDriver.Context context =
        new MediaDriver.Context()
            .aeronDirectoryName("/tmp/aeron") // 👈 Set custom directory
            .dirDeleteOnStart(true); // Optional: clean dir on start
    latch = new CountDownLatch(1); // only one thread so far

   // try (MediaDriver ignore = MediaDriver.launchEmbedded(context)) {
      for (int i = 0; i < 1; i++) {
        final int core = i;
        new Thread(
                () -> {
                  // CPU affinity with core. In hyperthreading, will try to affinity one thread per
                  // core
                  // and when all used, then move to use hyperthreaded twin
                  // does not work on ARM, uncomment on x64 for thread affinity

                  // try (AffinityLock lock = AffinityLock.acquireLock(core)) {
                  new Main(
                      "com/codingmonster/common/engine-shard-" + core + "-channels.properties");
                  //  }
                })
            .start();
      }

      try {
        latch.await();
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
  //  }
  }

  public Main(String path) {
    InputStream input = Main.class.getClassLoader().getResourceAsStream(path);
    Properties props = new Properties();
    try {
      props.load(input);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    // not thread safe, create in each thread
    MediaDriver.Context context =
        new MediaDriver.Context().aeronDirectoryName("/tmp/aeron").dirDeleteOnStart(true);

    Aeron.Context aeronCtx = new Aeron.Context().aeronDirectoryName(context.aeronDirectoryName());

    try (Aeron aeron = Aeron.connect(aeronCtx)) {
      props.forEach(
          (k, v) -> {
            String[] vals = v.toString().split(",");
            String trader = k.toString();
            String subChannel = vals[0];
            int subStreamId = Integer.parseInt(vals[1]);
            String pubChannel = vals[2];
            int pubStreamId = Integer.parseInt(vals[3]);
            Subscription subscription = aeron.addSubscription(subChannel, subStreamId);
            LOG.info("Subscribed to: " + subChannel + ", " + subStreamId);
            this.subscriptions.add(subscription);
            this.publications.put(trader, aeron.addPublication(pubChannel, pubStreamId));
            LOG.info(String.format("Matching Engine Instance Ready for: %s %s", path, trader));
          });

    while (started.get()) {
      FragmentHandler handler =
              (buffer, offset, length, header) -> {
                // Decode header
                MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
                headerDecoder.wrap(buffer, offset); // will read only header from buffer
                offset += MessageHeaderDecoder.ENCODED_LENGTH;

                int templateId = headerDecoder.templateId();
                switch (templateId) {
                  case NewOrderSingleDecoder.TEMPLATE_ID:
                    processNewOrderSingle(buffer, offset, headerDecoder);
                    break;
                  case OrderCancelReplaceRequestDecoder.TEMPLATE_ID:
                    break;
                  case OrderCancelRequestDecoder.TEMPLATE_ID:
                    break;
                  case StopSessionDecoder.TEMPLATE_ID:
                    this.started.set(false);
                    break;
                  default:
                    LOG.error("Unknown message with templateId: " + templateId);
                }
              };

      // Poll from the subscription
      for (Subscription subscription : subscriptions) {
        int ignore = subscription.poll(handler, 1);
      }
    }
      // Thread.yield();
    }
   // latch.countDown();
  }

  private void processNewOrderSingle(
      DirectBuffer buffer, int offset, MessageHeaderDecoder headerDecoder) {
      int actingBlockLength = headerDecoder.blockLength();
      int schemaVersion = headerDecoder.version();

// Wrap the message decoder at the correct offset
      NewOrderSingleDecoder orderDecoder = new NewOrderSingleDecoder();
      orderDecoder.wrap(buffer, offset,
              actingBlockLength, schemaVersion);

    var senderCompID = orderDecoder.senderCompID();
    long clOrdID = orderDecoder.clOrdID();
    Side side =
        orderDecoder.side().equals(com.codingmonster.common.sbe.trade.Side.Buy)
            ? Side.BUY
            : Side.SELL;
    int qty = orderDecoder.orderQty();
    PriceDecoder price = orderDecoder.price();
      System.out.println(qty + ", " + price.toString());
    long timestamp = orderDecoder.timestamp();
    if (orderDecoder.orderType().equals(OrderType.Limit)) {

    } else if (orderDecoder.orderType().equals(OrderType.Market)) {

    } else {

    }

    System.out.printf(
        "Received order: sender=%s, ID=%d, Side=%s, Qty=%d, Price=%s, TS: %d %n",
            senderCompID, clOrdID, side, qty, price.toString(), timestamp);

    // 1. Wrap a buffer at offset 0 for header + message
    offset = 0;

// 2. Encode the SBE Message Header first
    MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    ExecutionReportEncoder executionReportEncoder = new ExecutionReportEncoder();

    // 3. Encode your ExecutionReport payload right after header
    executionReportEncoder
            .wrapAndApplyHeader(this.encodeBuffer, offset, messageHeaderEncoder)
            .clOrdID(121)
            .execType(ExecType.Fill)
            .ordStatus(OrdStatus.Filled)
            .filledQty(12)
            .leavesQty(8);

    executionReportEncoder.price()
            .exponent((byte) 3)
            .mantissa(12345);

// 4. Calculate total length = header + payload
    int length =
            messageHeaderEncoder.encodedLength() +
                    executionReportEncoder.encodedLength();

    Publication publication = publications.get(senderCompID);
// 5. Send via Aeron
    long ignore = publication.offer(this.encodeBuffer, offset, length);
  }
}
