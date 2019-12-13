package com.senseportraitclient.websocket;

import com.alibaba.fastjson.JSONObject;
import com.senseportraitclient.pojo.MethodNameEnum;
import com.senseportraitclient.repository.redis.RedisHelper;
import com.senseportraitclient.service.WebSocketService;
import org.java_websocket.drafts.Draft;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;

/**
 * Created by machenggong on 2019/3/7.
 */
public class ReconnectingClient extends ReconnectingWSClient {

    private static final Logger logger = LoggerFactory.getLogger(ReconnectingClient.class);

    @Autowired
    private WebSocketService webSocketService;

    public ReconnectingClient(URI serverURI, Draft protocolDraft, String baseUrl, String iotUrl) {
        super(serverURI, protocolDraft);
    }

    @Override
    public void onOpenEvent(ServerHandshake handshakedata) {
        logger.trace("websocket open");
    }

    @Override
    public void onErrorEvent(Exception ex) {
        ex.printStackTrace();
        logger.error("error make websocket close");
    }

    @Override
    public void onMessageEvent(String message) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(message);
            MethodNameEnum.valueOf(jsonObject.getString("methodName")).op(jsonObject, webSocketService);
        } catch (Exception e) {
            logger.info(e.getMessage());
        }
    }
}
