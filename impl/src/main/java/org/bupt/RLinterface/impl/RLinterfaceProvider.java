/*
 * Copyright © 2017 Inc.sry and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.RLinterface.impl;

import org.bupt.RLinterface.impl.bandwidth.PortStatesReaderTask;
import org.bupt.RLinterface.impl.delay.DelayListener;
import org.bupt.RLinterface.impl.delay.DelaySender;
import org.bupt.RLinterface.impl.util.InitialFlowWriter;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;

import org.opendaylight.yang.gen.v1.urn.opendaylight.echo.service.rev150305.SalEchoService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;

import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.rlinterface.config.rev140528.RLinterfaceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.rlinterface.config.rev140528.rlinterface.config.NodeConnectorCable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.packet.service.rev130709.PacketProcessingService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.rlinterface.rev150105.RLinterfaceService;
import org.opendaylight.yangtools.concepts.Registration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RLinterfaceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(RLinterfaceProvider.class);
    private final DataBroker dataBroker;
//    private final DelaydetectConfig delaydetectConfig;
    private final NotificationProviderService notificationProviderService;
    private final PacketProcessingService packetProcessingService;
    private final RpcProviderRegistry rpcProviderRegistry;
    private final SalFlowService salFlowService;
    private final SalEchoService salEchoService;
    private BindingAwareBroker.RpcRegistration<RLinterfaceService> rpcRegistration;

    private static Thread thread;
    private static Map<String, Long> delayMap = new ConcurrentHashMap<>();
    private static Map<String, Long> echoDelayMap = new ConcurrentHashMap<>();
    private Registration delayRegistration = null, topoNodeListenerReg = null;

    //varibles for BW
    private static Map<String, Long> BWMap=new ConcurrentHashMap<>();
    private static Map<String, Long> cableMap=new ConcurrentHashMap<>();
    private RLinterfaceConfig rLinterfaceConfig;                                ;
    private final ScheduledExecutorService sec= Executors.newScheduledThreadPool(2);



    public RLinterfaceProvider(final DataBroker dataBroker, NotificationProviderService notificationProviderService, PacketProcessingService packetProcessingService, RpcProviderRegistry rpcProviderRegistry, SalFlowService salFlowService, SalEchoService salEchoService,RLinterfaceConfig rLinterfaceConfig) {
        this.dataBroker = dataBroker;
        this.notificationProviderService = notificationProviderService;
        this.packetProcessingService = packetProcessingService;
        this.rpcProviderRegistry = rpcProviderRegistry;
        this.salFlowService = salFlowService;
        this.salEchoService = salEchoService;
        this.rLinterfaceConfig = rLinterfaceConfig;
    }



    /**
     * Method called when the blueprint container is created.
     */
    public void init() {
        //初始流表
        InitialFlowWriter flowWriter = new InitialFlowWriter(salFlowService);
        topoNodeListenerReg = flowWriter.registerAsDataChangeListener(dataBroker);

        //延时测量
        DelaySender delaySender = new DelaySender(dataBroker, packetProcessingService, salEchoService, echoDelayMap);
        thread = new Thread(delaySender, "DelayDetect");
        thread.start();
        DelayListener delayListener = new DelayListener(delayMap, echoDelayMap);
        delayRegistration = notificationProviderService.registerNotificationListener(delayListener);

        //带宽测量
        loadCableData();
        PortStatesReaderTask psrt=new PortStatesReaderTask(dataBroker,BWMap,cableMap);

        //rpc
        RLinterfaceImpl rLinterface = new RLinterfaceImpl(delayMap,BWMap);
        rpcRegistration = rpcProviderRegistry.addRpcImplementation(RLinterfaceService.class, rLinterface);

        sec.scheduleAtFixedRate(psrt,1, 5, TimeUnit.SECONDS);

        LOG.info("RLinterfaceProvider Session Initiated");
    }

    /**
     * Method called when the blueprint container is destroyed.
     */
    public void close() {
//        thread.stop();
//        thread.destroy();
        try {
            if(delayRegistration != null) {
                delayRegistration.close();
            }
            if(topoNodeListenerReg != null) {
                topoNodeListenerReg.close();
            }
            rpcRegistration.close();
            sec.shutdown();
        } catch (Exception e) {
            e.printStackTrace();
        }
        LOG.info("RLinterfaceProvider Closed");
    }

    public void loadCableData() {
        if(rLinterfaceConfig!=null && rLinterfaceConfig.getNodeConnectorCable()!=null){
            for(NodeConnectorCable connectorCable:rLinterfaceConfig.getNodeConnectorCable()){
                cableMap.put(connectorCable.getNodeConnector(),(long)connectorCable.getCable());
            }
        }
    }

}