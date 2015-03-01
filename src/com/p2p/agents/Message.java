package com.p2p.agents;

import java.io.Serializable;
import java.util.ArrayList;

//Class to represent the object that will be sent with every message
//in our protocol
public class Message implements Serializable {
	
	//this will be used when pinging a host cache
	//we will inform the host cache about the current neighbours we have
	//so it knows how many it still needs to send us
	public ArrayList<String> SuperPeers;
	//the travel path will be used to track the way a ping got sent through the network
	public ArrayList<String> MsgTravelPath;
	//when a peer responds to a ping, it will become the initiatorPeer
	//when a pong is returned to the issuer, it will know to connect
	//to the initiatorPeer
	public String initiatorPeer;
	//the message type: host cache ping, host cache pong, peer ping or peer pong
	public String Action;
	//the remaining TTL
	public Integer TTL;
	
	public Message(String Action, ArrayList<String> SuperPeers, ArrayList<String> msgTravelPath,Integer TTL,String initiatorPeer) {
		this.SuperPeers = SuperPeers;
		this.Action = Action;
		
		this.initiatorPeer = initiatorPeer;
		
		this.MsgTravelPath = msgTravelPath;
		
		this.TTL = TTL;
	}
	
}
