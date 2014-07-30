package com.ebay.maui.util.proxy;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * 
* HttpTrackProxy.java Create on Feb 6, 2012    
*     
* Copyright (c) Feb 6, 2012  
*     
* @author doxie@ebay.com   
* @version 1.0
*
 */
public class HttpTrackProxy {


	private int port = 8090;
	
//	private int maxProcessors = 5;

	boolean running = false;

	private Stack<RequestProcessor> processors = new Stack<RequestProcessor>();

	private Selector selector = null;
	
	private String remoteProxyHost = null;
	private int remoteProxyPort = 8080;
	
	ServerSocketChannel server = null;
	
	public HttpTrackProxy() {
	}
	
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}

	
	public synchronized void start() throws Exception {
		if (running) {
			return;
		}
		running = true;
		Thread t = new Thread(
			new Runnable() {
				public void run() {
					try {
						server = ServerSocketChannel.open();
						server.configureBlocking(false);
						server.socket().bind(new InetSocketAddress(port));

						selector = Selector.open();
						server.register(selector, SelectionKey.OP_ACCEPT);

						while (true) {
							if(selector == null)
							{
								return;
							}
							selector.select();
							if (server==null) {
								return;
							}
							Iterator<SelectionKey> it = selector.selectedKeys().iterator();

							while (it.hasNext()) {
								SelectionKey key = it.next();
								it.remove();
								
								if (key.isValid()) {
									if (key.isAcceptable()) {
										try {
											RequestProcessor p = null;											
											synchronized(processors) {
												while ( processors.size()>0 && !(p=processors.pop()).isAlive() ) ;
											}
											if (p==null || !p.isAlive()) {
												p = new RequestProcessor() {
													@Override
													public void error(String message, Throwable t) {
														HttpTrackProxy.this.error(message, t);
													}
													public void debug(String message) {
														HttpTrackProxy.this.debug(message);
													}

													@Override
													public void recycle() {
														HttpTrackProxy.this.recycle(this);
													}

													@Override
													public List<RequestFilter> getRequestFilters() {
														return HttpTrackProxy.this.getRequestFilters();
													}

                          public List<ResponseFilter> getResponseFilters() {
                            return HttpTrackProxy.this.getResponseFilters();
                          }

                          @Override
													public String getRemoteProxyHost() {
														return HttpTrackProxy.this.getRemoteProxyHost();
													}

													@Override
													public int getRemoteProxyPort() {
														return HttpTrackProxy.this.getRemoteProxyPort();
													}
													@Override
													public InetAddress resolve(String host) {
														return HttpTrackProxy.this.resolve(host);
													}
													
												};
											}
											
											p.process(key);
										} catch (Throwable t) {
											if (server==null) {
												// On s'est arrete ...
												return;
											}
											error(null, t);
										}

									} 
								}								
							}
						}
					} catch (ClosedSelectorException cse) {
						// Normal
					} catch (Throwable t) {
						error(null, t);
					} finally {
						running=false;
					}
				}
			}
		);
		t.setDaemon(false);
		t.setName("ProxyLight server");
		t.start();
	}
	
	/**
	 * This method is to cache hostname resolution.
	 * Java should have it's own cache so I'm not sure it's really useful ...
	 */
	private Map<String, InetAddress> ipCache = new HashMap<String, InetAddress>();
	protected InetAddress resolve(String host) {
		InetAddress retour = ipCache.get(host);
		if (retour==null) {
	        try {
	            retour = InetAddress.getByName(host);
	            ipCache.put(host, retour);
	            
	        } catch (UnknownHostException uhe) {
	        	return null;
			} catch (Throwable t) {
				error("", t);
			}
	     }
		return retour;
	}

	public void stop() {
		if (!running) {
			return;
		}
		
		try {
			server.close();
			server=null;
			
			selector.wakeup();
			selector=null;
			
			while (processors.size()>0) {
				processors.pop().shutdown();
			}
		} catch (Exception e) {
			error(null, e);
		} 
		finally
		{
			running = false;
		}
	}
		
	public void error(String message, Throwable t) {
		if (message!=null) {
			System.err.println(message);
		}
		if (t!=null) {
			t.printStackTrace();
		}
	}

	public void debug(String message) {
		if (message!=null) {
			System.err.println(message);
		}
	}

	public boolean isRunning() {
		return running;
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception {

		HttpTrackProxy p = new HttpTrackProxy();
		p.getRequestFilters().add(new RequestFilter() {

			public boolean filter(Request request) {
				request.getHeaders().put("X-Proxy", "ProxyLight");
				return false;
			}
			
		});
		p.start();
		
		Thread.sleep(100000);
		
		p.stop();

	}

	public void setRemoteProxy(String host, int port) {
		this.remoteProxyHost=host;
		this.remoteProxyPort=port;
	}

	public String getRemoteProxyHost() {
		return remoteProxyHost;
	}
	public int getRemoteProxyPort() {
		return remoteProxyPort;
	}

	public void recycle(RequestProcessor processor) {		
		synchronized(processors) {
			processors.add(processor);
		}
	}
	
	private List<RequestFilter> requestFilters = new ArrayList<RequestFilter>();
	public List<RequestFilter> getRequestFilters() {
		return requestFilters;
	}

  private List<ResponseFilter> responseFilters = new ArrayList<ResponseFilter>();
	public List<ResponseFilter> getResponseFilters() {
		return responseFilters;
	}

}
