package com.senseportraitclient.websocket;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.drafts.Draft;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.channels.NotYetConnectedException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 自动重连的webSocket的客户端
 */
public abstract class ReconnectingWSClient {

    private static final Logger logger = LoggerFactory.getLogger(ReconnectingWSClient.class);

    /**
     * 默认重试时间间隔
     */
    private final static int DEFAULT_INTERVAL = 1;

    private WebSocketClient ws;

    private URI serverURI;
    private Draft draft;
    /**
     * 重试时间间隔
     */
    private int interval;

    private AtomicInteger attemptCounter = new AtomicInteger(0);

    public ReconnectingWSClient(URI serverURI, Draft draft, int interval) {
        this.serverURI = serverURI;
        this.draft = draft;
        this.interval = interval > 0 ? interval : 1;
        ws = getWs();
    }

    public ReconnectingWSClient(URI serverURI, Draft draft) {
        this(serverURI, draft, DEFAULT_INTERVAL);
    }

    public ReconnectingWSClient(URI serverURI) {
        this(serverURI, new Draft_6455());
    }

    public void reConnect() {
        attemptCounter.incrementAndGet();
        try {
            TimeUnit.SECONDS.sleep(interval);
            logger.info("thread sleep " + interval + ", attemptCount = " + attemptCounter.get());
        } catch (InterruptedException e) {
            logger.info(e.getMessage(), e);
        }

        ws = getWs();
        ws.connect();
        logger.info(ws.getDraft().toString());
        while (!ws.getReadyState().equals(WebSocket.READYSTATE.OPEN)) {
            logger.trace("websocket not open");
        }
        logger.trace("websocket open");
        //ws.send("hello");
    }

    public void connect() {
        ws.connect();
    }


    public void close() {
        ws.close();
    }


    public void send(String text) throws NotYetConnectedException {
        ws.send(text);
    }


    public void send(byte[] bytes) throws IllegalArgumentException, NotYetConnectedException {
        ws.send(bytes);
    }


    public InetSocketAddress getRemoteSocketAddress() {
        return null;
    }

    public InetSocketAddress getLocalSocketAddress() {
        return ws.getLocalSocketAddress(ws.getConnection());
    }

    public boolean isOpen() {
        return ws.getConnection().isOpen();
    }

    public boolean isClosing() {
        return ws.getConnection().isClosing();
    }

    public boolean isFlushAndClose() {
        return ws.getConnection().isFlushAndClose();
    }

    public boolean isClosed() {
        return ws.getConnection().isClosed();
    }

    public Draft getDraft() {
        return ws.getDraft();
    }

    public boolean isReadyState() {
        return ws.getReadyState().equals(WebSocket.READYSTATE.OPEN);
    }


    public boolean isConnecting() {
        return ws.getConnection().isConnecting();
    }

    public WebSocketClient getWs() {
        return new WebSocketClient(serverURI, draft) {
            @Override
            public void onClose(int code, String reason, boolean remote) {
                onCloseEvent(code, reason, remote);
            }

            @Override
            public void onOpen(ServerHandshake handshakedata) {
                attemptCounter = new AtomicInteger(0);
                onOpenEvent(handshakedata);
            }

            @Override
            public void onMessage(String message) {
                onMessageEvent(message);
                //ws.send(message);
            }

            @Override
            public void onError(Exception ex) {
                onErrorEvent(ex);
            }
        };
    }

    public abstract void onOpenEvent(ServerHandshake handshakedata);

    public abstract void onErrorEvent(Exception ex);

    public abstract void onMessageEvent(String message);

    public void onCloseEvent(int code, String reason, boolean remote) {
        logger.info("[onClose Event]:code = " + code + ", reason = " + reason + ", remote = " + remote);
        reConnect();
    }
}
