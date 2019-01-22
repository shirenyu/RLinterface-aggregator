/*
 * Copyright © 2017 xujun and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.bupt.RLinterface.impl.bandwidth;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnector;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.model.statistics.types.rev130925.duration.Duration;
import org.opendaylight.yang.gen.v1.urn.opendaylight.port.statistics.rev131214.FlowCapableNodeConnectorStatisticsData;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class PortStatesReaderTask implements Runnable{ 
	private Map<String, FlowCapableNodeConnectorStatisticsData> map=new ConcurrentHashMap<>();
	private Map<String, Long> BWMap;
	private Map<String, Long> cableMap;
	private final DataBroker dataBroker;
	private final ExecutorService service=Executors.newFixedThreadPool(10);
	public PortStatesReaderTask(final DataBroker dataBroker,Map<String, Long> BWMap,Map<String, Long> cableMap) {
		// TODO Auto-generated constructor stub
		this.dataBroker=dataBroker;
		this.BWMap=BWMap;
		this.cableMap=cableMap;
	}
	/**
	 * read the port statics
	 * @param nodeConnectorId
	 */
	public void read(NodeConnectorId nodeConnectorId){
		long cable=cableMap.getOrDefault(nodeConnectorId.getValue(),5l)*1000000;
		String nodeidString[]=nodeConnectorId.getValue().split(":");
		NodeId nodeId=new NodeId(nodeidString[0]+":"+nodeidString[1]);
		InstanceIdentifier<FlowCapableNodeConnectorStatisticsData> connector=InstanceIdentifier.builder(Nodes.class)
				.child(Node.class,new NodeKey(nodeId))
				.child(NodeConnector.class,new NodeConnectorKey(nodeConnectorId))
				.augmentation(FlowCapableNodeConnectorStatisticsData.class)
				.build();	
		ReadOnlyTransaction rx=dataBroker.newReadOnlyTransaction();
		CheckedFuture<Optional<FlowCapableNodeConnectorStatisticsData>,ReadFailedException> future=rx.read(LogicalDatastoreType.OPERATIONAL,connector);
		try {
			Optional<FlowCapableNodeConnectorStatisticsData> optional=future.get(1000, TimeUnit.MILLISECONDS);
			if(optional.isPresent()){
				FlowCapableNodeConnectorStatisticsData fcnc=optional.get();
				if(map.containsKey(nodeConnectorId.getValue())){
					FlowCapableNodeConnectorStatisticsData oldfcnc=map.get(nodeConnectorId.getValue());
					BWMap.put(nodeConnectorId.getValue(),cable-computeBw(fcnc, oldfcnc));
					map.put(nodeConnectorId.getValue(), fcnc);
				}
				else{
					map.put(nodeConnectorId.getValue(), fcnc);
				}
			}
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}			
	}
	/**
	 * compute the bw
	 * @param now
	 * @param old
	 * @return
	 */
	private long computeBw(FlowCapableNodeConnectorStatisticsData now,FlowCapableNodeConnectorStatisticsData old){
		if(old==null || now==null || old.getFlowCapableNodeConnectorStatistics()==null || now.getFlowCapableNodeConnectorStatistics()==null){
			return 0;
		}		
		Duration olddur=old.getFlowCapableNodeConnectorStatistics().getDuration();
		Duration nowdur=now.getFlowCapableNodeConnectorStatistics().getDuration();
		if(olddur==null || olddur.getSecond()==null || nowdur==null || nowdur.getSecond()==null){
			return 0;
		}
		long oldtime=olddur.getSecond().getValue();
		long nowtime=nowdur.getSecond().getValue();
		long time=nowtime-oldtime;
		if(time==0){
			return 0;
		}
		if(old.getFlowCapableNodeConnectorStatistics()==null || old.getFlowCapableNodeConnectorStatistics().getBytes()==null
			|| now.getFlowCapableNodeConnectorStatistics()==null || now.getFlowCapableNodeConnectorStatistics().getBytes()==null){
			return 0;
		}
		BigInteger oldbyte=old.getFlowCapableNodeConnectorStatistics().getBytes().getTransmitted();
		BigInteger nowbyte=now.getFlowCapableNodeConnectorStatistics().getBytes().getTransmitted();
		long bw=(nowbyte.longValue()-oldbyte.longValue())/time;
		return bw*8;
	}
	/**
	 * 
	 * @return
	 */
	private List<NodeConnectorId> getAllPorts(){
		List<NodeConnectorId> list=new LinkedList<>();
		InstanceIdentifier<Topology> path=InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class,new TopologyKey(new TopologyId("flow:1")))
                .build();
		ReadOnlyTransaction rx=dataBroker.newReadOnlyTransaction(); 
		CheckedFuture<Optional<Topology>,ReadFailedException> future=rx.read(LogicalDatastoreType.OPERATIONAL, path);
		try {
			Optional<Topology> optional=future.get(1000, TimeUnit.MILLISECONDS);
			if(optional.isPresent()){
				Topology topology=optional.get();
				for(org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node node:topology.getNode()){
					for(TerminationPoint t:node.getTerminationPoint()){
						String tpid=t.getTpId().getValue();
						if(tpid!=null && tpid.contains("openflow") && !tpid.contains("LOCAL")){
							list.add(new NodeConnectorId(t.getTpId().getValue()));
						}
					}
				}
			}
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return list;
	}
	@Override
	public void run() {
		// TODO Auto-generated method stub
		List<NodeConnectorId> nodeConnectorIds=getAllPorts();
		for(NodeConnectorId nodeConnectorId:nodeConnectorIds){
			service.execute(()->{read(nodeConnectorId);});
		}
		
	}

}
