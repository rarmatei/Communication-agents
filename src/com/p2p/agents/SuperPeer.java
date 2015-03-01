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

public class SuperPeer extends Agent {
	
	//will keep track if one of the host caches responded so it can stop listening for the rest
	private boolean hostCacheResponded = false;
	
	//will contain a list generated from the host caches of peers to try pinging
	private ArrayList<String> neighboursToTry = new ArrayList<String>();
	
	//the peer's neighbours will be stored here
	private ArrayList<String> super_peer_neighbours = new ArrayList<String>();
	private ArrayList<String> peer_neighbours = new ArrayList<String>();
	
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
	    //we check every 20 seconds if we have at least 4 neighbours to try pinging
	    //we do this mainly because at the beginning, only one peer will ping a host cache
	    //and that host cache will return zero neighbours (because it did not have any other previous
	    //peers ping it)
	    //once more peers will ping the host cache, the initial peer will return to it, and finally
	    //get a list of 4 neighbours
	    addBehaviour(new TickerBehaviour(this, 20) {
	        protected void onTick() {
		          if(neighboursToTry.size()<4) {
		        	  	//while we have less than 4 neighbours to try, ping all the host caches in finalizedHostCaches
		      			addBehaviour(new SuperPeerOneShotBehaviour("HC_ping",finalizedHostCaches,null,null,null));
		          }
		          else{
		        	  //stop the current behaviour from running again
		        	  stop();
		        	  //try sending a ping to all the neighbours
		        	  writer.println("Sending initial pings:");
		        	  for(String s : neighboursToTry) {
		        		  //when starting to send a ping through the network
		        		  //the path will travel with the message and will retain the list of
		        		  //peers the message has travelled through
		        		  ArrayList<String> path = new ArrayList<String>();
		        		  //start the TTL at maximum, 3
		        		  Integer TTL = 3;
		        		  //add the current peer to the path
		        		  path.add(myAgent.getLocalName());
		        		  writer.println("Sending ping to: "+s);
		        		  addBehaviour(new SuperPeerOneShotBehaviour("ping",neighboursToTry,path,TTL,myAgent.getLocalName()));
		        	  }
		        	  writer.println("//////////");
		          }
		       } } );
	    
		//start the message receive listener
		addBehaviour(new SuperPeerSimpleBehaviour());

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
	class SuperPeerSimpleBehaviour extends Behaviour {

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
						if(msgContent.MsgTravelPath.size()==1) {
							writer.println("A ping I issued has returned back. The responding peer was "+ msgContent.initiatorPeer);
							if(super_peer_neighbours.size()<4) {
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
						}
						//else forward onwards down the path
						else {
							ArrayList<String> pathToSend = msgContent.MsgTravelPath;
							//remove the current peer from the path, before
							//continuing to send it back
							pathToSend.remove(pathToSend.size()-1);
							writer.println("This PONG is meant to reach another peer. Will forward it.");
							writer.print("The peers left in its travel path are/is: ");
							for(String s : msgContent.MsgTravelPath) writer.print(s+" ");
							writer.println("Will send it to: "+ pathToSend.get(pathToSend.size()-1));
							//the last parameter we send to our behaviour, will be the original responder to the ping
							//we do not change it
							addBehaviour(new SuperPeerOneShotBehaviour("pong",null,pathToSend,null,msgContent.initiatorPeer));
						}
						break;
					default:
						break;
					}
				}
				//if we receive a request
				else if(msg.getPerformative() == ACLMessage.REQUEST) {
					switch (msgContent.Action) {
					case "ping":
							writer.println("And the peer that INITIATED the ping was: "+ msgContent.initiatorPeer);
							//if we receive a ping, and we have space to add the peer as a neighbour
							if(super_peer_neighbours.size()<4 && !msgContent.initiatorPeer.equals(myAgent.getLocalName()) && !super_peer_neighbours.contains(msgContent.initiatorPeer)) {
								writer.println("I can add him as a neighbour.");
								writer.print("New neighbour list: ");
								//add it to our neighbours
								super_peer_neighbours.add(msgContent.initiatorPeer);
								for(String s : super_peer_neighbours) writer.print(s + " ");
								writer.println();
								ArrayList<String> pathToSend = msgContent.MsgTravelPath;
								writer.print("SEND - pong back. The peers in the return path will be: ");
								for(String s : pathToSend) writer.write(s+ " ");
								writer.println();
								//send pong back
								//because we are responding to the ping
								//when sending back the pong, the last paramater, the "responding peer"
								//will be set the the current agent's name
								addBehaviour(new SuperPeerOneShotBehaviour("pong",null,pathToSend,null,myAgent.getLocalName()));
								//we also forward the ping if the TTL permits it
								if(msgContent.TTL>0) {
									writer.println("TTL>0 - will also forward the ping.");
									//add the current agent to the path
									pathToSend.add(myAgent.getLocalName());
									//we will forward the ping to all the neighbours except the one we received
									//it from
									ArrayList<String> peersToForwardTo = new ArrayList<>();
									for(String s : super_peer_neighbours) {
										if(!s.equals(msg.getSender().getLocalName()) && !s.equals(msgContent.initiatorPeer)) {
											peersToForwardTo.add(s);
										}
									}
									writer.print("SEND - ping to: ");
									if(peersToForwardTo.size()==0) {
										writer.print("no suitable peers found");
									}
									for(String s : peersToForwardTo) writer.print(s+" ");
									addBehaviour(new SuperPeerOneShotBehaviour("ping",peersToForwardTo,pathToSend,msgContent.TTL-1,msgContent.initiatorPeer));
								}
								else writer.println("TTL=0 - will not forward the ping.");
							}
							//if we do not have space for the peer as a neighbour we just forward it
							else {
								writer.println("But I either do not have space to add him as a neighbour, or he already exists. (neighbours ");
								for(String s:super_peer_neighbours) writer.print(s+ " ");
								writer.println(")");
								ArrayList<String> pathToSend = msgContent.MsgTravelPath;
								if(msgContent.TTL>0) {
									writer.println("TTL>0 - I will forward the ping onwards.");
									pathToSend.add(myAgent.getLocalName());
									ArrayList<String> peersToForwardTo = new ArrayList<>();
									for(String s : super_peer_neighbours) {
										if(!s.equals(msg.getSender().getLocalName()) && !s.equals(msgContent.initiatorPeer)) {
											peersToForwardTo.add(s);
										}
									}
									writer.print("SEND - ping to: ");
									if(peersToForwardTo.size()==0) {
										writer.print("no suitable peers found");
									}
									
									for(String s : peersToForwardTo) writer.print(s+" ");
									addBehaviour(new SuperPeerOneShotBehaviour("ping",peersToForwardTo,pathToSend,msgContent.TTL-1,msgContent.initiatorPeer));
								}
								else writer.println("TTL=0 - I will not forward the ping.");
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
	
	public class SuperPeerOneShotBehaviour extends OneShotBehaviour{
		
		//the list of all the intended receivers for the message
		ArrayList<String> receivers;
		//the path the message has travlled on
		ArrayList<String> path;
		//the action intent for the message (ping,pong etc.)
		String action;
		//the remaining TTL
		Integer TTL;
		//the peer that either sent the ping or responded to the ping (depending on the direction the
		//message is travelling)
		String initiatorPeer;
		
		public SuperPeerOneShotBehaviour(String action, ArrayList<String> receivers,ArrayList<String>path,Integer TTL,String initiatorPeer){		
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
				toSend = new Message(action,null,path,TTL,initiatorPeer);
				break;
			case "pong":
				performative = ACLMessage.INFORM;
				toSend = new Message(action, null, path, null,initiatorPeer);
				
					if(path.get(path.size()-1).equals(myAgent.getLocalName())) path.remove(path.size()-1);
				//since it is a pong, and we are sending it back the way it came
				//we only want to send the message to the last peer
				//in our path
					this.receivers = new ArrayList<String>();
					this.receivers.add(path.get(path.size()-1));
				
				
				
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
