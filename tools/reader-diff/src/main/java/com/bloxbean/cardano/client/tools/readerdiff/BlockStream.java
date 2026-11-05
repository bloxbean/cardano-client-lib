package com.bloxbean.cardano.client.tools.readerdiff;

import com.bloxbean.cardano.client.crypto.Blake2bUtil;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.cardano.yaci.core.common.Constants;
import com.bloxbean.cardano.yaci.core.model.BlockHeader;
import com.bloxbean.cardano.yaci.core.model.byron.ByronBlockHead;
import com.bloxbean.cardano.yaci.core.model.byron.ByronEbHead;
import com.bloxbean.cardano.yaci.core.network.TCPNodeClient;
import com.bloxbean.cardano.yaci.core.protocol.Message;
import com.bloxbean.cardano.yaci.core.protocol.blockfetch.BlockfetchAgent;
import com.bloxbean.cardano.yaci.core.protocol.blockfetch.BlockfetchAgentListener;
import com.bloxbean.cardano.yaci.core.protocol.blockfetch.messages.MsgBlock;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Point;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.messages.Tip;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.n2n.ChainSyncAgentListener;
import com.bloxbean.cardano.yaci.core.protocol.chainsync.n2n.ChainsyncAgent;
import com.bloxbean.cardano.yaci.core.protocol.handshake.HandshakeAgent;
import com.bloxbean.cardano.yaci.core.protocol.handshake.HandshakeAgentListener;
import com.bloxbean.cardano.yaci.core.protocol.handshake.util.N2NVersionTableConstant;
import com.bloxbean.cardano.yaci.core.protocol.keepalive.KeepAliveAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Raw Shelley-or-later blocks from a node over node-to-node block-fetch, as in {@code tools/chain-parse-check}: blocks
 * are not parsed, apart from chain-sync's one header to find the first Shelley block. The connection is not closed at
 * the end, as its event loop may be parked on the full queue: the program exits instead.
 */
final class BlockStream {
    private static final Logger log = LoggerFactory.getLogger(BlockStream.class);
    private static final byte[] DONE = new byte[0];

    private BlockStream() {
    }

    private record Network(long magic, Point intersect) {
    }

    private static Network network(String name) {
        return switch (name) {
            // intersect at the last Byron block; the next header is the first Shelley block (slot 4492800)
            case "mainnet" -> new Network(Constants.MAINNET_PROTOCOL_MAGIC,
                    new Point(4492799, "f8084c61b6a238acec985b59310b6ecec49c0ab8352249afd7268da5cff2a457"));
            case "preprod" -> new Network(Constants.PREPROD_PROTOCOL_MAGIC, Point.ORIGIN);
            case "preview" -> new Network(Constants.PREVIEW_PROTOCOL_MAGIC, Point.ORIGIN);
            default -> throw new IllegalArgumentException("--network must be mainnet, preprod or preview");
        };
    }

    /** Passes every Shelley-or-later block up to the stop slot to the consumer, on one thread, and returns at the end. */
    static void stream(String host, int port, String networkName, long stopSlot, Blocks.BlockConsumer consumer)
            throws Exception {
        Network network = network(networkName);
        Point[] range = discover(host, port, network.magic(), network.intersect());
        Point from = range[0];
        Point tip = range[1];
        log.info("reading {} from {}:{}, range {} .. tip {}, stop slot {}", networkName, host, port, from, tip, stopSlot);

        BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(512);
        AtomicLong lastProgress = new AtomicLong(System.currentTimeMillis());
        CompletableFuture<Void> finished = new CompletableFuture<>();
        Thread dispatcher = new Thread(() -> {
            try {
                dispatch(queue, stopSlot, lastProgress, consumer);
                finished.complete(null);
            } catch (Throwable t) {
                finished.completeExceptionally(t);
            }
        }, "dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();

        HandshakeAgent handshake = new HandshakeAgent(N2NVersionTableConstant.v4AndAbove(network.magic()));
        KeepAliveAgent keepAlive = new KeepAliveAgent();
        RawBlockfetch fetch = new RawBlockfetch(queue, tip);
        fetch.resetPoints(from, tip);
        fetch.addListener(new BlockfetchAgentListener() {
            @Override
            public void batchDone() {
                put(queue, DONE);
            }

            @Override
            public void noBlockFound(Point f, Point t) {
                log.warn("no blocks for range {} .. {}", f, t);
                put(queue, DONE);
            }
        });
        handshake.addListener(new HandshakeAgentListener() {
            @Override
            public void handshakeOk() {
                fetch.sendNextMessage();
            }
        });
        TCPNodeClient client = new TCPNodeClient(host, port, handshake, keepAlive, fetch);
        client.start();

        int cookie = 1;
        long lastKeepAlive = System.currentTimeMillis();
        while (!finished.isDone()) {
            Thread.sleep(1000);
            long now = System.currentTimeMillis();
            if (now - lastKeepAlive > 20_000) {
                cookie = cookie % 60000 + 1;
                try {
                    keepAlive.sendKeepAlive(cookie);
                } catch (Exception e) {
                    log.debug("keep-alive not sent: {}", e.toString()); // the stall check below reconnects if needed
                }
                lastKeepAlive = now;
            }
            if (now - lastProgress.get() > 180_000 && queue.isEmpty()) { // stalled: reconnect from the last block
                log.warn("no progress for 180 s; reconnecting from {}", fetch.from());
                lastProgress.set(now);
                client.restartSession();
            }
        }
        finished.get();
    }

    private static void dispatch(BlockingQueue<byte[]> queue, long stopSlot, AtomicLong lastProgress,
                                 Blocks.BlockConsumer consumer) throws Exception {
        long lastSlot = -1;
        while (true) {
            byte[] block = queue.take();
            lastProgress.set(System.currentTimeMillis());
            if (block == DONE)
                return;
            if (Blocks.eraIndex(block) <= 1) // Byron
                continue;
            long slot = Blocks.slot(block);
            if (slot <= lastSlot) // replay after a reconnect
                continue;
            if (slot > stopSlot)
                return;
            lastSlot = slot;
            consumer.accept(block);
        }
    }

    /** A block-fetch agent that hands each block's bytes over without decoding it, and tracks the resume point. */
    private static final class RawBlockfetch extends BlockfetchAgent {
        private final BlockingQueue<byte[]> queue;
        private final Point to;
        private volatile Point last;

        RawBlockfetch(BlockingQueue<byte[]> queue, Point to) {
            this.queue = queue;
            this.to = to;
        }

        Point from() {
            return last;
        }

        @Override
        public void resetPoints(Point from, Point to) {
            super.resetPoints(from, to);
            last = from;
        }

        @Override
        public void processResponse(Message message) {
            if (message instanceof MsgBlock block) {
                byte[] bytes = block.getBytes();
                if (Blocks.eraIndex(bytes) > 1) // a re-request after a reconnect starts at the last block received
                    resetPoints(new Point(Blocks.slot(bytes),
                            HexUtil.encodeHexString(Blake2bUtil.blake2bHash256(Blocks.headerBytes(bytes)))), to);
                put(queue, bytes);
            } else {
                super.processResponse(message);
            }
        }
    }

    /** Chain-sync on a short-lived connection: the first block after the Byron era, and the tip. */
    private static Point[] discover(String host, int port, long magic, Point intersect) throws Exception {
        CompletableFuture<Point[]> result = new CompletableFuture<>();
        HandshakeAgent handshake = new HandshakeAgent(N2NVersionTableConstant.v4AndAbove(magic));
        ChainsyncAgent chainSync = new ChainsyncAgent(new Point[]{intersect});
        handshake.addListener(new HandshakeAgentListener() {
            @Override
            public void handshakeOk() {
                chainSync.sendNextMessage();
            }
        });
        chainSync.addListener(new ChainSyncAgentListener() {
            @Override
            public void intersactFound(Tip t, Point point) {
                chainSync.sendNextMessage();
            }

            @Override
            public void intersactNotFound(Tip t) {
                result.completeExceptionally(new IllegalStateException("intersect not found: " + intersect + " tip " + t));
            }

            @Override
            public void rollbackward(Tip t, Point toPoint) {
                chainSync.sendNextMessage();
            }

            @Override
            public void rollforwardByronEra(Tip t, ByronBlockHead head) {
                chainSync.sendNextMessage();
            }

            @Override
            public void rollforwardByronEra(Tip t, ByronEbHead head) {
                chainSync.sendNextMessage();
            }

            @Override
            public void rollforward(Tip t, BlockHeader header) {
                result.complete(new Point[]{new Point(header.getHeaderBody().getSlot(),
                        header.getHeaderBody().getBlockHash()), t.getPoint()});
            }
        });
        TCPNodeClient client = new TCPNodeClient(host, port, handshake, chainSync);
        client.start();
        try {
            return result.get(15, TimeUnit.MINUTES);
        } finally {
            client.shutdown();
        }
    }

    private static void put(BlockingQueue<byte[]> queue, byte[] block) {
        try {
            queue.put(block);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
