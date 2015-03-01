package com.p2p.agents;

import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

//The Host Cache class definition
public class HostCache extends Agent {
	
	//will hold a list of the current peers in the network
	ArrayList<String> peers = new ArrayList<String>();
	//a list of the current super_peers in the network
	ArrayList<String> super_peers = new ArrayList<String>();
	
	
	protected void setup() {
		addBehaviour(new HostCacheSimpleBehaviour());
	}
	//The continous message listener behaviour
	private class HostCacheSimpleBehaviour extends Behaviour {
		@Override
		public void action() {
			ACLMessage msg = myAgent.receive();
			if (msg != null) {
				String sender_name = msg.getSender().getLocalName();
				ArrayList<String> peersToReturn = new ArrayList<String>();
				try {
					peersToReturn = ((Message)msg.getContentObject()).SuperPeers;
				} catch (UnreadableException e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
				Random rand = new Random();
				//if we have 5 or more peers in our list, then randomly pick 4 of them and return
				if(super_peers.size()>4) {
					while(peersToReturn.size()<4) {
						Integer randIndex = rand.nextInt(super_peers.size());
						//return a unique set of peers, that does not contain the peer that issued the request
						if(!super_peers.get(randIndex).equals(sender_name) && !peersToReturn.contains(super_peers.get(randIndex))) {
							peersToReturn.add(super_peers.get(randIndex));
						}
					}
				}
				//if we have 4 or less peers in our list, then return the whole list
				else {
					for(String s : super_peers) {
						if(!s.equals(sender_name) && !peersToReturn.contains(s)) {
							peersToReturn.add(s);
						}
					}
				}
				//if the sender is a super peer, add him to the super peer list
				if(sender_name.contains("SuperPeer")) {
					super_peers.add(sender_name);
				}
				else {
					peers.add(sender_name);
				}
				
				Message msgContent = new Message("HC_pong",peersToReturn,null,null,null);
				ACLMessage reply = msg.createReply();
				reply.setPerformative(ACLMessage.INFORM);
				try {
					reply.setContentObject(msgContent);
				} catch (IOException e) {
					e.printStackTrace();
				}
				myAgent.send(reply);
			}
			else{
				block();
			}
		}

		@Override
		public boolean done() {
			return false;
		}

	}
	
}
