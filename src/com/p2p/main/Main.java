package com.p2p.main;


import java.util.ArrayList;

import com.p2p.agents.HostCache;
import com.p2p.agents.Peer;
import com.p2p.agents.SuperPeer;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.wrapper.AgentController;
import jade.wrapper.ContainerController;
import jade.wrapper.StaleProxyException;

public class Main {
	
	private jade.core.Runtime runtime = jade.core.Runtime.instance();
	private ContainerController home=null;
	private Profile p = new ProfileImpl();
	
	private int number_of_host_caches = 2;
	private int number_of_super_peers = 8;
	private int number_of_peers = 20;
	

	public static void main(String[] args) {
		//TODO: make number of host caches/super peers parametized
		Main EnvRunner = new Main();
		EnvRunner.StartEnv("16467");
	}
	
	public void StartEnv(String port){
		
		p.setParameter(Profile.LOCAL_PORT, port);
		home= runtime.createMainContainer(p);

		try {
			AgentController rma =home.createNewAgent("rma",	"jade.tools.rma.rma", new Object[0]);
			rma.start();
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
		
		createAgents();
	}
	
	private void createAgents() {
		try {
			ArrayList<String> hostCacheNames = new ArrayList<>();
			//create host caches
			for(int i=0; i<number_of_host_caches; i++) {
				String hostCacheName = "HostCache"+i;
				AgentController host_cache = home.createNewAgent("HostCache"+i, HostCache.class.getName(), new Object[0]);
				host_cache.start();
				hostCacheNames.add(hostCacheName);
			}
			//create super peers
			for(int i=0; i<number_of_super_peers; i++) {
				ArrayList<String> peerName = new ArrayList<String>();
				peerName.add("SuperPeer"+i);
			    ArrayList[] args = { hostCacheNames, peerName };
				AgentController super_peer = home.createNewAgent("SuperPeer"+i, SuperPeer.class.getName(), args);
				super_peer.start();
			}
			//create peers
//			for(int i=0; i<number_of_peers; i++) {
//				ArrayList<String> peerName = new ArrayList<String>();
//				peerName.add("Peer"+i);
//			    ArrayList[] args = { hostCacheNames, peerName };
//				AgentController peer = home.createNewAgent("Peer"+i, SuperPeer.class.getName(), args);
//				peer.start();
//			}
		} catch (StaleProxyException e) {
			e.printStackTrace();
		}
	}
	
	
}
