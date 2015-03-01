package com.p2p.agents;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import jade.core.AID;
import jade.core.Agent;
import jade.core.CaseInsensitiveString;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.UnreadableException;

public class Peer extends Agent {
	
	//will keep track if one of the host caches responded so it can stop listening for the rest
	private boolean hostCacheResponded = false;
	
	//will contain a list generated from the host caches of peers to try pinging
	private ArrayList<String> neighboursToTry = new ArrayList<String>();
	
	//the peer's neighbours will be stored here
	private ArrayList<String> super_peer_neighbours = new ArrayList<String>();
	
	//the file we will write to for the simulation
	PrintWriter writer = null;
	
	protected void setup() {
		
		ArrayList<String> hostCaches = null;
		//the list of host caches will be sent as null
		Object[] args = getArguments();
	    if (args != null && args.length > 0) {
	      hostCaches = (ArrayList) args[0];
	    }
	    
		try {
			writer = new PrintWriter((String)((ArrayList)args[1]).get(0)+".txt", "UTF-8");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
//	    writer.println("The first line");
//	    writer.println("The second line");
//	    writer.close();
	    
		writer.print("Peer has been initialized with list of host caches: ");
		for(String s : hostCaches) writer.print(s+" ");
	    writer.println();
		
	    //host caches must be final to be used in the ticker
	    final ArrayList<String> finalizedHostCaches = hostCaches;
	    //we check every 20 seconds if we have 1 neighbour to try pinging
	    //we do this mainly because at the beginning, only one peer will ping a host cache
	    //and that host cache will return zero neighbours (because it did not have any other previous
	    //peers ping it)
	    //once more peers will ping the host cache, the initial peer will return to it, and finally
	    //get a list of 4 neighbours
	    addBehaviour(new TickerBehaviour(this, 20) {
	        protected void onTick() {
		          if(neighboursToTry.size()<1) {
		        	  	//while we have no neighbour to try, ping all the host caches in finalizedHostCaches
		      			addBehaviour(new PeerOneShotBehaviour("HC_ping",finalizedHostCaches,null,null,null));
		          }
		          else{
		        	  //stop the current behaviour from running again
		        	  stop();
		        	  //try sending a ping to all the neighbours
		        	  for(String s : neighboursToTry) {
		        		  addBehaviour(new PeerOneShotBehaviour("ping",neighboursToTry,null,null,null));
		        	  }
		          }
		       } } );
	    
		//start the message receive listener
		addBehaviour(new PeerSimpleBehaviour());

		//close the file after 5 seconds
		new java.util.Timer().schedule( 
		        new java.util.TimerTask() {
		            @Override
		            public void run() {
		                writer.close();
		            }
		        }, 
		        5000 
		);
		
	}
	
	//this class sets up the message receive listener
	class PeerSimpleBehaviour extends Behaviour {

		@Override
		public void action() {
			ACLMessage msg = myAgent.receive();
			if (msg != null) {
				Message msgContent = null;
				//extract the Message class object from the message content
				try {
					msgContent = (Message)msg.getContentObject();
				} catch (UnreadableException e) {
					
					e.printStackTrace();
				}
				writer.println("RECEIVE - Message "+msgContent.Action+" received (performative is "+((msg.getPerformative()==7)?"INFORM":"REQUEST")+"), from "+ msg.getSender().getLocalName() + ":");
				
				//if the message is an INFORM
				//i.e. a pong from a peer or from a host cache
				if(msg.getPerformative() == ACLMessage.INFORM) {
					switch (msgContent.Action) {
					
					//if we receive a list of peers from a host cache
					case "HC_pong":
						//if it is the first host cache that responded
						if(!hostCacheResponded) {
							writer.println("First host cache that responded.");
							//all the received peers from the host cache will become
							//the list of neighbours to try
							neighboursToTry = new ArrayList<String>();
							for(String s : msgContent.SuperPeers) {
								neighboursToTry.add(s);
							}
							writer.print("My new neighbour list TO TRY pinging is: ");
							for(String s : neighboursToTry) writer.print(s+" ");
							writer.println();
							//set this to true so we ignore all other responses from future host caches
							hostCacheResponded = true;
						}
						else writer.println("Already received response from another host cache - IGNORING");
						break;
					case "pong":
						//if path size is 1 then this peer was the original ping sender
						//so add the responder as a neighbour (if we still have space for it)
							writer.println("A ping I issued has returned back. The responding peer was "+ msgContent.initiatorPeer);
							if(super_peer_neighbours.size()<1) {
								super_peer_neighbours.add(msgContent.initiatorPeer);
								writer.print("I added him as neighbour. New neighbour list: ");
								for(String s : super_peer_neighbours) writer.print(s+" ");
								writer.println();
							}
							else {
								writer.println("But I do not have space to add him as a neighbour. (neighbours ");
								for(String s:super_peer_neighbours) writer.print(s+ " ");
								writer.println(")");
							}
						
						break;
					default:
						break;
					}
				}
				writer.println();
				writer.println("////////////////////////");
				writer.println();
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
	
	public class PeerOneShotBehaviour extends OneShotBehaviour{
		
		//the list of all the intended receivers for the message
		ArrayList<String> receivers;
		//the path the message has travelled on
		ArrayList<String> path;
		//the action intent for the message (ping,pong etc.)
		String action;
		//the remaining TTL
		Integer TTL;
		//the peer that either sent the ping or responded to the ping (depending on the direction the
		//message is travelling)
		String initiatorPeer;
		
		public PeerOneShotBehaviour(String action, ArrayList<String> receivers,ArrayList<String>path,Integer TTL,String initiatorPeer){		
			this.receivers = receivers;
			//reset hostCacheResponded so we can start waiting for a new set of responses
			hostCacheResponded = false;
			this.action = action;
			this.path = path;
			this.TTL = TTL;
			this.initiatorPeer = initiatorPeer;
		}
		
		@Override
		public void action() {
			Integer performative = null;
			Object toSend = null;
			switch (action) {
			case "HC_ping":
				performative = ACLMessage.REQUEST;
				//the action will be HC_ping
				//we will be sending the current list of neighbours we have
				//so the host cache will only try to send us back the exact number
				//of peers we need
				toSend = new Message(action,neighboursToTry,null,null,null);
				break;
			case "ping":
				performative = ACLMessage.REQUEST;
				toSend = new Message(action,null,null,null,null);
				break;
			default:
				break;
			}
			
			ACLMessage msg = new ACLMessage(performative);
			for (String receiver : receivers) {
					msg.addReceiver(new AID(receiver, AID.ISLOCALNAME));
			}
			
			try {
				msg.setContentObject((Serializable) toSend);
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			
			
			myAgent.send(msg);
		}
		
	}
}
