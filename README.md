# Aeron Transport, SBE Encoding Examples

This repository demonstrates how to use [Aeron](https://aeron.io/) and [SBE](https://github.com/aeron-io/simple-binary-encoding/wiki/FIX-SBE-XML-Primer) to build Java applications that communicate over the [FIX protocol](https://www.fixtrading.org/).

## Features

- ✅ Create `NewOrderSingle` (type `D`) and other standard FIX messages
- ✅ Set up **Initiator** (client) and **Acceptor** (server)
- ✅ Send/receive FIX messages over TCP/IP
- ✅ Use standard FIX fields and custom fields
- ✅ Load session settings from `.cfg` files

#### AffinityLock

AffinityLock by itself just says,

“Please run me on this core and try not to move me.”

But the OS scheduler still plays by its own rules unless you’ve done the deeper OS tuning.

Without OS-level isolation
AffinityLock pins your thread to a specific core (via sched_setaffinity on Linux).

The OS honors that most of the time.

But background tasks, kernel housekeeping, or interrupts can still run on that core.

So you might still get jitter, cache evictions, and random latency spikes.

With OS-level isolation
Core isolation (isolcpus, nohz_full, rcu_nocbs) keeps everything else off that core.

IRQ affinity tuning moves hardware interrupts to other cores.

CPU frequency scaling disabled ensures constant cycle timing.

Hyperthreading disabled ensures no sibling thread steals execution units.

Once you’ve done that, AffinityLock actually gives you an exclusive, stable core.

💡 Think of it like reserving a seat:

Without isolation, you put your jacket on the chair (AffinityLock), but strangers can still sit there if they feel like it.

With isolation, you rope off the entire row and put a “Reserved” sign (OS-level tuning), so no one else comes near.

#### Why disable CPU frequency scaling

You disable CPU frequency scaling because it introduces unpredictable latency spikes, which is poison for a low-latency matching engine like yours.

Why it’s a problem
Modern CPUs change clock speed dynamically (Turbo Boost, SpeedStep, Cool’n’Quiet, etc.) based on load and temperature.
That means:

When your thread wakes up, the CPU might be running at a low frequency to save power.

It takes a few microseconds (sometimes hundreds) to ramp up to max frequency.

That delay shows up as jitter in your matching engine’s response time.

The ramp-up timing is not consistent, so you lose determinism.

#### Why hyperthreading hurts in a matching engine

Hyperthreading lets two logical CPUs share one physical CPU core’s execution resources (ALUs, FPUs, L1 cache, etc.). That’s great for throughput workloads like video encoding or database queries, but in ultra-low-latency trading it causes trouble:

Resource contention

Two threads on the same core fight for the same execution units.

If your matching engine thread and some OS/kernel thread (or another app) share a core, your engine can stall for extra cycles.

Cache thrash

The L1/L2 cache is shared between hyperthreads.

If the other logical CPU is doing something, it can evict your hot order book data from cache.

Unpredictable latency

Sometimes the sibling thread is idle (fast).

Sometimes it’s active (slow).

The jitter is random and shows up in your tail latency measurements.

OS scheduler surprises

Even with CPU affinity, the OS can decide to schedule other work on the sibling hyperthread.

You can lock the logical CPU, but you’re still not physically isolated from the sibling.

What happens when you disable it
Each logical CPU you see in lscpu or Task Manager is now backed by a full physical core with no sibling.

No execution resource sharing.

Cache is fully yours.

Latency histograms tighten up—tail latency improves drastically.


#### why isolate CPU cores
You isolate CPU cores for the same reason you disable hyperthreading — to give your critical threads an uncontested, predictable execution environment — but core isolation goes a step further: it keeps all other processes off those cores entirely.

Why core isolation matters in a matching engine
Even if you disable hyperthreading, the OS scheduler can still run other processes (kernel threads, daemons, logging agents, etc.) on the same physical core as your matching engine thread.

That leads to:

Scheduler interrupts – The OS might preempt your thread to run background work.

Cache pollution – Other processes evict your order book data from L1/L2 cache.

NUMA locality breaks – Your thread could migrate across sockets if the OS thinks it’s “helping”.

Unpredictable tail latency – One moment you’re 2 µs, the next you’re 50 µs because the kernel did some housekeeping.

What “isolating a core” does
When you isolate a core, you tell the OS:

“Never schedule any process or kernel work here unless I explicitly say so.”

This means:

Your matching engine thread is the only thing that runs there.

No other user processes, no kernel housekeeping, no random cron jobs.

The cache, branch predictors, TLB — all stay “warm” with your code and data.

#### Start Media Driver
curl -O https://repo1.maven.org/maven2/io/aeron/aeron-all/1.44.0/aeron-all-1.44.0.jar

java \
--add-opens=java.base/sun.nio.ch=ALL-UNNAMED \
-Daeron.dir=/tmp/aeron \
-Daeron.dir.delete.on.start=true \
-Daeron.print.configuration=true \
-Daeron.event.log=true \
-Daeron.client.liveness.timeout=10000000000 \
-Daeron.driver.timeout=60000 \
-cp aeron-all-1.44.0.jar io.aeron.driver.MediaDriver


