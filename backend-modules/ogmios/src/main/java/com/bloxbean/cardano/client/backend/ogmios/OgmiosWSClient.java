package com.bloxbean.cardano.client.backend.ogmios;

import com.bloxbean.cardano.client.backend.ogmios.model.base.Message;
import com.bloxbean.cardano.client.backend.ogmios.model.base.Request;
import com.bloxbean.cardano.client.backend.ogmios.model.base.Response;
import com.bloxbean.cardano.client.backend.ogmios.model.base.iface.LocalStateQuery;
import com.bloxbean.cardano.client.backend.ogmios.model.base.iface.LocalTxSubmission;
import com.bloxbean.cardano.client.backend.ogmios.model.query.request.CurrentProtocolParametersRequest;
import com.bloxbean.cardano.client.backend.ogmios.model.query.response.CurrentProtocolParameters;
import com.bloxbean.cardano.client.backend.ogmios.model.tx.request.EvaluateTxRequest;
import com.bloxbean.cardano.client.backend.ogmios.model.tx.request.SubmitTxRequest;
import com.bloxbean.cardano.client.backend.ogmios.model.tx.response.EvaluateTxResponse;
import com.bloxbean.cardano.client.backend.ogmios.model.tx.response.SubmitTxResponse;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.security.InvalidParameterException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
class OgmiosWSClient extends WebSocketClient implements LocalTxSubmission, LocalStateQuery {

    private static final long TIMEOUT = 5; // Sec
    private final AtomicLong msgId = new AtomicLong();
    private final ConcurrentHashMap<Long, BlockingQueue<Message>> blockingQueueConcurrentHashMap = new ConcurrentHashMap<>();

    public OgmiosWSClient(URI serverURI) {
        super(serverURI);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("Connection Established!");
        if (log.isDebugEnabled()) log.debug("onOpen -> ServerHandshake: {}", serverHandshake);
    }

    @Override
    public void onMessage(String message) {
        if (log.isDebugEnabled()) log.debug("Received: {}", message);
        Message response = Message.deserialize(message);
        if (response == null) {
            log.error("Response is Null");
            return;
        }
        if (blockingQueueConcurrentHashMap.get(response.getMsgId()).offer(response) && log.isDebugEnabled()) {
            log.debug("Message Offered: {}", message);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("Connection closed by {}, Code: {}{}", (remote ? "remote peer" : "client"), code,
                (reason == null || reason.isEmpty()) ? reason : ", Reason: " + reason);
    }

    @Override
    public void onError(Exception ex) {
        log.error(ex.getMessage());
        // if the error is fatal then onClose will be called additionally
    }

    private Response send(Request request) {
        Response queryResponse = null;
        long msgIdentifier = msgId.incrementAndGet();
        request.setMsgId(msgIdentifier);
        send(request.toString());
        BlockingQueue<Message> messageBlockingQueue = new ArrayBlockingQueue<>(1);
        blockingQueueConcurrentHashMap.put(msgIdentifier, messageBlockingQueue);
        try {
            queryResponse = (Response) messageBlockingQueue.poll(TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
            Thread.currentThread().interrupt();
        }
        return queryResponse;
    }

    /* LocalTxSubmission */

    @Override
    public SubmitTxResponse submitTx(byte[] cborData) throws InvalidParameterException {
        if (cborData.length == 0) {
            throw new InvalidParameterException();
        }
        return (SubmitTxResponse) send(new SubmitTxRequest(cborData));
    }

    @Override
    public EvaluateTxResponse evaluateTx(byte[] cborData) throws InvalidParameterException {
        if (cborData.length == 0) {
            throw new InvalidParameterException();
        }
        Response response = send(new EvaluateTxRequest(cborData));
        if (response.getFault() == null)
            return (EvaluateTxResponse) response;
        else
            throw new RuntimeException(response.toString());
    }

    @Override
    public CurrentProtocolParameters currentProtocolParameters() {
        return (CurrentProtocolParameters) send(new CurrentProtocolParametersRequest());
    }
}
