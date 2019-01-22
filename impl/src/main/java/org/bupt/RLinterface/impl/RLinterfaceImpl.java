/*
 * Copyright © 2017 Inc.sry and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.RLinterface.impl;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.rlinterface.rev150105.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.rlinterface.rev150105.getglobaldata.output.DataList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.rlinterface.rev150105.getglobaldata.output.DataListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.rlinterface.rev150105.getglobaldata.output.DataListKey;

import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

public class RLinterfaceImpl implements RLinterfaceService {
    private Map<String,Long> delayMap;
    private Map<String,Long> BWMap;
    public RLinterfaceImpl(Map<String, Long> delayMap,Map<String,Long> bwMap) {
        this.delayMap = delayMap;
        this.BWMap = bwMap;
    }


    @Override
    public Future<RpcResult<GetDataOutput>> getData(GetDataInput input) {
        String nodeConnector = input.getNodeConnector();
        GetDataOutputBuilder getDataOutputBuilder = new GetDataOutputBuilder();
        getDataOutputBuilder.setDelay(delayMap.get(nodeConnector));
        getDataOutputBuilder.setBW(new Long(String.valueOf(BWMap.get(nodeConnector))));
        return RpcResultBuilder.success(getDataOutputBuilder.build()).buildFuture();
    }

    @Override
    public Future<RpcResult<GetGlobalDataOutput>> getGlobalData() {
        GetGlobalDataOutputBuilder getGlobalDataOutputBuilder = new GetGlobalDataOutputBuilder();
        List<DataList> dataLists = new ArrayList<>();
        for(String ncid : delayMap.keySet())
        {
            DataListBuilder dataListBuilder = new DataListBuilder();
            dataListBuilder.setKey(new DataListKey(ncid));
            dataListBuilder.setDelay(delayMap.get(ncid));
            dataListBuilder.setBW(new Long(String.valueOf(BWMap.get(ncid))));
            dataLists.add(dataListBuilder.build());
        }
        getGlobalDataOutputBuilder.setDataList(dataLists);
        return RpcResultBuilder.success(getGlobalDataOutputBuilder).buildFuture();
    }
}
