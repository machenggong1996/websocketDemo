/*
 * Copyright (c) 2019-2020, Beyondsoft All rights reserved..
 * <p>
 * File Name		:  WiseCenterSocketServer.java
 * Author			:  v-tiqiu
 * Version			:  V1.0
 * CreateDate		:  2019/05/14
 * EditDate			:
 * Description		:  WebSocket 服务器端
*/
package com.beyondsoft.wisecenter.websocket;

import com.beyondsoft.wisecenter.device.DeviceMessageHandler;
import com.beyondsoft.wisecenter.pojo.dto.common.DeviceDTO;
import com.beyondsoft.wisecenter.pojo.dto.common.SupplierDeviceConfigDTO;
import com.beyondsoft.wisecenter.pojo.dto.common.websocket.ReceiveMessageDTO;
import com.beyondsoft.wisecenter.service.DeviceService;
import com.beyondsoft.wisecenter.service.SupplierDeviceConfigService;
import com.beyondsoft.wisecenter.supplier.Supplier;
import com.beyondsoft.wisecenter.supplier.SupplierFactory;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 服务器端，用于客户端服务与Wise Center通信
 *
 * @author v-tiqiu
 * @date 2019/06/09
 */
@SuppressWarnings("ALL")
@ServerEndpoint(value = "/wisecenter/{deviceId}")
@Component
public class WiseCenterSocketServer {
    /**
     * 日志记录对象
     */
    private static final Logger logger = LoggerFactory.getLogger(WiseCenterSocketServer.class);

    /**
     * 系统APP上下文
     */
    private static ApplicationContext applicationContext;

    /**
     * 静态变量，用来记录当前在线连接数。应该把它设计成线程安全的。
     */
    private static int onlineCount = 0;

    /**
     * concurrent包的线程安全Set，用来存放每个客户端对应的MyWebSocket对象。
     */
    @SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
    private static CopyOnWriteArraySet<WiseCenterSocketServer> webSocketSet = new CopyOnWriteArraySet<>();

    /**
     * 设备列表
     */
    private List<DeviceDTO> subDeviceList;

    /**
     * 供应商抽象类
     */
    @Autowired
    private SupplierFactory supplierFactory;

    /**
     * 设备数据信息服务
     */
    @Autowired
    private DeviceService deviceService;

    /**
     * 设备供应商配置服务
     */
    @Autowired
    private SupplierDeviceConfigService supplierConfigService;
    /**
     * 与某个客户端的连接会话，需要通过它来给客户端发送数据
     */
    private Session session;
    /**
     * 当前Socket session对应的设备
     */
    private DeviceDTO device;
    /**
     * 厂商管道消息处理器
     */
    private DeviceMessageHandler messageHandler;

    /**
     * 设置Socket的Application 上下文
     *
     * @param appContext
     */
    public static void setApplicationContext(ApplicationContext appContext) {
        WiseCenterSocketServer.applicationContext = appContext;
    }

    /**
     * 广播群发自定义消息
     */
    public static void sendInfo(String message) throws IOException {
        for (WiseCenterSocketServer item : webSocketSet) {
            try {
                item.sendMessage(message);
            } catch (IOException e) {
                logger.error("Failed send message to client, sessionID{}", item.session.getId(), e);
                continue;
            }
        }
    }

    /**
     * 指向设备发送自定义消息
     *
     * @param message
     * @param deviceId
     * @throws IOException
     */
    public static boolean sendMessageToDevice(String message, String deviceId) {
        boolean result = false;
        for (WiseCenterSocketServer item : webSocketSet) {
            try {
                if (item.device.getDeviceId().equals(deviceId)) {
                    item.sendMessage(message);
                    result = true;
                }
            } catch (IOException e) {
                logger.error("Failed send message to client, sessionID{}", item.session.getId(), e);
                continue;
            }
        }

        return result;
    }

    /**
     * 获取在线客户端数量
     *
     * @return
     */
    public static synchronized int getOnlineCount() {
        return onlineCount;
    }

    /**
     * 添加在线客户端计数
     */
    public static synchronized void addOnlineCount() {
        WiseCenterSocketServer.onlineCount++;
    }

    /**
     * 减少在线连接客户端计数
     */
    public static synchronized void subOnlineCount() {
        WiseCenterSocketServer.onlineCount--;
    }

    /**
     * WiseCenterSocketServer实例比较
     *
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof WiseCenterSocketServer)) {
            return false;
        }

        WiseCenterSocketServer that = (WiseCenterSocketServer) o;

        return device.equals(that.device);
    }

    /**
     * 获取设备类hash code
     *
     * @return hash code
     */
    @Override
    public int hashCode() {
        return device.hashCode();
    }

    /**
     * 连接建立成功调用的方法
     */
    @SuppressWarnings("MapOrSetKeyShouldOverrideHashCodeEquals")
    @OnOpen
    public void onOpen(Session session, @PathParam(value = "deviceId") String deviceId) {
        this.session = session;

        try {
            this.deviceService = applicationContext.getBean(DeviceService.class);
            this.supplierFactory = applicationContext.getBean(SupplierFactory.class);
            this.supplierConfigService = applicationContext.getBean(SupplierDeviceConfigService.class);
        } catch (Exception e) {
            logger.error("Failed to initialize device context ID:{}", deviceId, e);
        }

        try {
            // 从数据库获取设备及其配置信息.
            this.device = deviceService.getDeviceById(deviceId);
            SupplierDeviceConfigDTO configDTO = supplierConfigService.getConfigById(device.getSupplierConfigId(), this.device.getTenantId());
            if (configDTO == null) {
                return;
            }

            device.setConfigDTO(configDTO);
            Supplier supplier = supplierFactory.createSupplier(device.getSupplierId(), configDTO.getConfigData());
            messageHandler = supplier.getHandler();

        } catch (Exception e) {
            logger.error("Failed to get device ID:{}", deviceId, e);
            return;
        }

        // 获取所有子设备
        try {
            subDeviceList = deviceService.getDevicesByParent(device.getDeviceId());
        } catch (Exception e) {
            logger.warn("Exception occur when fetch kid devices by device ID:{}", device.getDeviceId());
            if (subDeviceList == null) {
                subDeviceList = new ArrayList<>();
            }
        }

        for (DeviceDTO deviceItem : subDeviceList) {
            if (deviceItem == null) {
                continue;
            }

            SupplierDeviceConfigDTO childDeviceConfig = supplierConfigService.getConfigById(deviceItem.getSupplierConfigId(), deviceItem.getTenantId());
            if (childDeviceConfig != null) {
                deviceItem.setConfigDTO(childDeviceConfig);
            }
        }

        subDeviceList.add(device);

        // 加入set中
        webSocketSet.add(this);

        // 在线数加1
        addOnlineCount();
        logger.info("There is a new client join, current online client count is {}", getOnlineCount());
    }

    /**
     * 连接关闭调用的方法
     */
    @OnClose
    public void onClose() {
        // 从set中删除
        webSocketSet.remove(this);
        // 在线数减1
        subOnlineCount();
        logger.info("有一连接关闭！当前在线设备数为" + getOnlineCount());
    }

    /**
     * 收到客户端消息后调用的方法
     *
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        logger.info("Get message from client : {}", message);
        Gson jsonSerializer = new Gson();
        ReceiveMessageDTO deviceMessage;
        try {
            deviceMessage = jsonSerializer.fromJson(message, ReceiveMessageDTO.class);
            if (this.messageHandler == null) {
                logger.info("Failed to convert client message to be device message data.");
                return;
            }
        } catch (Exception e) {
            logger.warn("Failed to resolve device message data.{}", message, e);
            return;
        }

        try {
            this.messageHandler.onReceiveMessage(deviceMessage, this.subDeviceList);
        } catch (Exception e) {
            logger.error("Failed to resolve received message, {}", message, e);
        }
    }

    /**
     * 错误处理
     *
     * @param session
     * @param error
     */
    public void onError(Session session, Throwable error) {
        logger.error("Failed send message to client, sessionID{}, error message:{}", session.getId(), error.getMessage());
    }

    /**
     * 发送消息
     *
     * @param message
     * @throws IOException
     */
    public void sendMessage(String message) throws IOException {
        this.session.getBasicRemote().sendText(message);
    }
}