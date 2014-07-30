package com.ebay.maui.util.proxy;

import java.nio.channels.SocketChannel;
/**
 * 
* Socket.java Create on Feb 6, 2012    
*     
* Copyright (c) Feb 6, 2012  
*     
* @author doxie@ebay.com   
* @version 1.0
*
 */
public class Socket {

	public SocketChannel socket = null;
	public long created = System.currentTimeMillis();
	public long lastWrite = created;
	public long lastRead = created;
}
