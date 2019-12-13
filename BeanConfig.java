package com.senseportraitclient.websocket;

import org.java_websocket.drafts.Draft_6455;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

/**
 * Created by machenggong on 2019/3/7.
 */
@Configuration
public class BeanConfig {

    @Value("${sense.ip.port}")
    private String baseUrl;

    @Value("${iot.ip.port}")
    private String iotUrl;

    @Value("${ws.ip.port}")
    private String wsUrl;

    private static final Logger logger = LoggerFactory.getLogger(BeanConfig.class);

    @Bean
    ReconnectingWSClient reconnectingWSClient() throws URISyntaxException {
        return new ReconnectingClient(new URI(wsUrl), new Draft_6455(), baseUrl, iotUrl);
    }

    @Bean
    public void init() throws URISyntaxException {
        ReconnectingClient client = (ReconnectingClient) this.reconnectingWSClient();
        logger.info(client.getDraft().toString());
        client.connect();
        while (!client.isReadyState()) {
            try {
                TimeUnit.SECONDS.sleep(3L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            logger.info("websocket not open");
        }
        logger.info("websocket open");
        //client.send("hello faceImageSocketServer");
    }
}
