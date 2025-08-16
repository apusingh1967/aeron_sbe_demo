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
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;
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
  private final UnsafeBuffer buffer = new UnsafeBuffer(ByteBuffer.allocateDirect(4096));

  private final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
  private final MessageHeaderDecoder messageHeaderDecoder = new MessageHeaderDecoder();
  private final NewOrderSingleDecoder newOrderSingleDecoder = new NewOrderSingleDecoder();
  private final OrderCancelReplaceRequestDecoder orderCancelReplaceRequestDecoder =
      new OrderCancelReplaceRequestDecoder();
  private final OrderCancelRequestDecoder orderCancelRequestDecoder =
      new OrderCancelRequestDecoder();
  private final ExecutionReportEncoder executionReportEncoder = new ExecutionReportEncoder();

  // This uses BackoffIdleStrategy, which is a progressive idle strategy that escalates from busy
  // spinning → yielding → parking (sleeping).
  // The constructor parameters mean:
  // spins = 100
  //   First, the thread will busy-spin in a loop for up to 100 iterations (fastest, lowest latency,
  // but burns CPU).
  // yields = 10
  //   If still idle, the thread will call Thread.yield() up to 10 times (lets the OS scheduler run
  // other threads).
  // minParkPeriodNs = 1 microsecond
  //   After spins and yields, the thread will LockSupport.parkNanos() for a small time (here: 1
  // µs).
  // maxParkPeriodNs = 1 millisecond
  //   If it remains idle for longer, the park time backs off exponentially up to 1 ms max.
  private final IdleStrategy idleStrategy =
      new BackoffIdleStrategy(
          100, 10, TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MILLISECONDS.toNanos(1));

  public static void main(String[] args) {
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
                new Main("com/codingmonster/common/engine-shard-" + core + "-channels.properties");
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

      runEventLoop();
    }
  }

  private void runEventLoop() {
    while (started.get()) {
      FragmentHandler handler =
          (buffer, offset, length, header) -> {
            messageHeaderDecoder.wrap(buffer, offset);
            offset += MessageHeaderDecoder.ENCODED_LENGTH;

            int templateId = messageHeaderDecoder.templateId();
            switch (templateId) {
              case NewOrderSingleDecoder.TEMPLATE_ID:
                processNewOrderSingle(buffer, offset, messageHeaderDecoder);
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
        int result;
        do {
          result = subscription.poll(handler, 1);
          if (result < 0) {
            if (result == Publication.BACK_PRESSURED) {
              idleStrategy.idle();
            } else if (result == Publication.NOT_CONNECTED) {
              idleStrategy.idle();
            } else if (result == Publication.ADMIN_ACTION) {
              idleStrategy.idle();
            } else {
              LOG.warn("Unknown error " + result);
            }
          }
        } while (result <= 0);
      }
    }
  }

  private void processNewOrderSingle(
      DirectBuffer buffer, int offset, MessageHeaderDecoder headerDecoder) {
    int actingBlockLength = headerDecoder.blockLength();
    int schemaVersion = headerDecoder.version();

    // Wrap the message decoder at the correct offset
    newOrderSingleDecoder.wrap(buffer, offset, actingBlockLength, schemaVersion);

    var senderCompID = newOrderSingleDecoder.senderCompID();
    long clOrdID = newOrderSingleDecoder.clOrdID();
    Side side =
        newOrderSingleDecoder.side().equals(com.codingmonster.common.sbe.trade.Side.Buy)
            ? Side.BUY
            : Side.SELL;
    int qty = newOrderSingleDecoder.orderQty();
    PriceDecoder price = newOrderSingleDecoder.price();
    long timestamp = newOrderSingleDecoder.timestamp();
    LOG.info(
            "Received order: sender={}, ID={}, Side={}, Qty={}, Price={}, TS: {}",
            senderCompID,
            clOrdID,
            side,
            qty,
            price,
            timestamp);

    if (newOrderSingleDecoder.orderType().equals(OrderType.Limit)) {

    } else if (newOrderSingleDecoder.orderType().equals(OrderType.Market)) {

    } else {
      LOG.warn("Order type not supported: " + newOrderSingleDecoder.orderType());
    }

    sendExecutionReport(senderCompID);
  }

  private void sendExecutionReport(String senderCompID) {
    int offset;
    // 1. Wrap a buffer at offset 0 for header + message
    offset = 0;
    // 3. Encode your ExecutionReport payload right after header
    messageHeaderEncoder
            .wrap(buffer, offset)
            .blockLength(executionReportEncoder.sbeBlockLength())
            .templateId(executionReportEncoder.sbeTemplateId())
            .schemaId(executionReportEncoder.sbeSchemaId())
            .version(executionReportEncoder.sbeSchemaVersion());
    offset += messageHeaderDecoder.encodedLength();

    executionReportEncoder
        .clOrdID(121)
        .execType(ExecType.Fill)
        .ordStatus(OrdStatus.Filled)
        .filledQty(12)
        .leavesQty(8);
    executionReportEncoder.senderCompID().appendTo(new StringBuilder("exchange"));
    executionReportEncoder.price().exponent((byte) 3).mantissa(12345);

    // 4. Calculate total length = header + payload
    int length = messageHeaderEncoder.encodedLength() + executionReportEncoder.encodedLength();

    Publication publication = publications.get(senderCompID);
    // 5. Send via Aeron
    long result;
    do {
      result = publication.offer(this.buffer, offset, length);
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
    } while(result <= 0);
  }
}
