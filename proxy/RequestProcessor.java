package com.ebay.maui.util.proxy;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
* RequestProcessor.java Create on Feb 6, 2012    
*     
* Copyright (c) Feb 6, 2012  
*     
* @author doxie@ebay.com   
* @version 1.0
*
 */
public abstract class RequestProcessor {
  private enum REQUEST_STEP {
    STATUS_LINE, REQUEST_HEADERS, REQUEST_CONTENT, TRANSFER
  }
  private enum RESPONSE_STEP {
    RESPONSE_HEADERS, RESPONSE_CONTENT, RESPONSE_DONE
  }
  private static int processorsCpt = 1;

  private static int processorsCount = 0;
  private static long SOCKET_TIMEOUT = 50 * 1000; // 50 seconds
  private Thread t = null;

  private boolean alive = false;

  private boolean shutdown = false;
  private int processorIdx = 1;
  // max.
  private Selector selector = null;
  private ByteBuffer readBuffer = ByteBuffer.allocate(4096);
  private Socket inSocket = null;

  private Map<String, Socket> outSockets = new ConcurrentHashMap<String, Socket>();
  private Socket currentOutSocket = null;

  private static final String CRLF = "\r\n";

  private static final byte[] CONNECT_OK = ("HTTP/1.0 200 Connection established" + CRLF + "Proxy-agent: ProxyLight" + CRLF + CRLF).getBytes();

  private char[] read_buf = new char[128];

  int read_offset = 0;

  public RequestProcessor() {

    t = new Thread(new Runnable() {
      public void run() {
        processorsCount++;
        try {
          while (true) {
            synchronized (RequestProcessor.this) {
              alive = true;
              if (selector == null && !shutdown) {
                try {
                  // We'll wait at most for 20 seconds.
                  // If nothing for 20 seconds, we'll exit.
                  RequestProcessor.this.wait(40 * 1000);
                } catch (InterruptedException e) {
                  error(null, e);
                  return;
                }
              }
              if (shutdown) {
                return;
              }
            }

            try {
              Request request = null;
              Response response = null;
              int contentLength = 0;
              REQUEST_STEP requestStep = REQUEST_STEP.STATUS_LINE;
              RESPONSE_STEP responseStep = RESPONSE_STEP.RESPONSE_HEADERS;
              while (selector != null) {
                selector.select(5000);
                if (inSocket == null) {
                  break;
                }
                long now = System.currentTimeMillis();
                if (selector.selectedKeys().size() == 0) {
                  long limit = now - SOCKET_TIMEOUT;
                  for (Iterator<Entry<String, Socket>> i = outSockets.entrySet().iterator(); i.hasNext();) {
                    Entry<String, Socket> e = i.next();
                    Socket so = e.getValue();
                    long lastOp = Math.max(so.lastRead, so.lastWrite);
                    if (lastOp < limit) {
                      debug("Process " + processorIdx + " : Close socket " + e.getKey());
                      if (request != null && "CONNECT".equals(request.getMethod())) {
                        closeAll();
                        break;
                      }
                      i.remove();
                      try {
                        so.socket.close();
                      } catch (Exception es) {
                        error("", es);
                      }
                      if (so == currentOutSocket) {
                        currentOutSocket = null;
                      }
                    }
                  }
                  if (outSockets.size() == 0) {
                    // Nothing
                    closeAll();
                  }
                  if (inSocket == null) {
                    break;
                  }
                }
                Iterator<SelectionKey> selectedKeys = selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                  SelectionKey key = (SelectionKey) selectedKeys.next();
                  selectedKeys.remove();
                  if (key.isValid()) {
                    if (key.isReadable()) {
                      Socket socket = (Socket) key.attachment();
                      if (socket == inSocket) {
                        readBuffer.clear();
                        int numRead = read(inSocket, readBuffer, now);
                        if (numRead <= 0) {
                          // Socket closed ...
                          closeAll();
                          break;
                        }
                        if (numRead > 0) {
                          readBuffer.flip();
                          while (readBuffer.remaining() > 0) {
                            if (requestStep == REQUEST_STEP.STATUS_LINE) {
                              String s = readNext(readBuffer);
                              if (s != null) {
                                request = new Request();
                                request.setStatusline(s);
                                requestStep = REQUEST_STEP.REQUEST_HEADERS;
                              }
                            } else if (requestStep == REQUEST_STEP.REQUEST_HEADERS) {
                              String s = readNext(readBuffer);
                              if (s != null) {
                                if (s.length() == 0) {
                                  if (filterRequest(request)) {
                                	  debug("Request >>>>>>>>>>>>>>>> " + request.getUrl());
                                   // return;
                                  }
                                  boolean isGet = "GET".equals(request.getMethod());
                                  boolean isPost = !isGet && "POST".equals(request.getMethod());
                                  boolean isConnect = !isGet && !isPost && "CONNECT".equals(request.getMethod());

                                  if (!isGet && !isPost && !isConnect) {
                                    throw new RuntimeException("Unknown method : " + request.getMethod());
                                  }

                                  String oh = request.getHost() + ":" + request.getPort();
                                  Socket outSocket = outSockets.get(oh);
                                  if (outSocket == null) {
                                    outSocket = new Socket();
                                    outSocket.socket = SocketChannel.open();
                                    outSocket.socket.configureBlocking(false);
                                    if (!outSocket.socket.connect(new InetSocketAddress(resolve(request.getHost()), request.getPort()))) {
                                      do {
                                        Thread.sleep(50);
                                      } while (!outSocket.socket.finishConnect());
                                    }
                                    outSocket.socket.register(selector, SelectionKey.OP_READ, outSocket);
                                    outSockets.put(oh, outSocket);
                                    debug("Use socket " + oh + " and process " + processorIdx + ". Socket count=" + outSockets.size());
                                  }
                                  currentOutSocket = outSocket;

                                  if (isConnect) {
                                    ByteBuffer b = ByteBuffer.wrap(CONNECT_OK);
                                    write(inSocket, b, now);
                                    requestStep = REQUEST_STEP.TRANSFER;
                                  } else {
                                	  
                                    StringBuffer send = new StringBuffer(request.getMethod()).append(" ");
                                    String url = request.getUrl();
                                    if (!url.startsWith("/")) {
                                      url = url.substring(url.indexOf('/', 8));
                                    }
                                    send.append(url).append(" ").append(request.getProtocol()).append(CRLF);
                                    for (Entry<String, String> h : request.getHeaders().entrySet()) {
                                      send.append(h.getKey()).append(": ").append(h.getValue()).append(CRLF);
                                    }
                                    send.append(CRLF);
                                    byte[] sendBytes = send.toString().getBytes(); // TEMP
                                    // ...
                                    ByteBuffer b = ByteBuffer.wrap(sendBytes);
                                    write(outSocket, b, now);

                                    contentLength = 0;
                                    if (isPost) {
                                      contentLength = Integer.parseInt(request.getHeaders().get("Content-Length"));
                                    }
                                    requestStep = contentLength == 0 ? REQUEST_STEP.STATUS_LINE : REQUEST_STEP.REQUEST_CONTENT;
                                  }
                                } else {
                                  request.addHeader(s);
                                }
                              }
                            } else if (requestStep == REQUEST_STEP.REQUEST_CONTENT) {
                              contentLength -= readBuffer.remaining();
                              if (contentLength <= 0) {
                                requestStep = REQUEST_STEP.STATUS_LINE;
                              }
                              currentOutSocket.socket.write(readBuffer);
                            } else if (requestStep == REQUEST_STEP.TRANSFER) {
                              write(currentOutSocket, readBuffer, now);
                            }
                          }
                        }
                      } else {
                        if (socket != currentOutSocket) {
                          closeOutSocket(socket);

                        } else {
                          if (response == null) response = new Response(request);
                          if (transfer(socket, inSocket, now, response, responseStep) == RESPONSE_STEP.RESPONSE_DONE) {
                            if ("CONNECT".equals(request.getMethod())) {
                              closeAll();
                            } else {
                              closeOutSocket(socket);
                              currentOutSocket = null;
                            }
                            break;
                          }
                        }
                      }
                    }
                  }
                }
                if(response != null) filterResponse(response);
              }
            } catch (Exception e) {
              error(null, e);
            } finally {
              closeAll();
              recycle();
            }
          }
        } finally {
          closeAll();
          processorsCount--;
          error("Close processor " + processorIdx, null);
        }
      }
    });
    t.setName("ProxyLight processor - " + (processorIdx = processorsCpt++));
    t.setDaemon(true);

    t.start();

    while (!isAlive()) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
      }
    }

    debug("Process " + processorIdx + " begin.");
  }

  private void closeAll() {
    if (inSocket != null) {
      try {
    	  if(inSocket.socket != null)
    		  inSocket.socket.close();
      } catch (Exception e) {
        error(null, e);
      }
      inSocket = null;
    }
    if(outSockets != null && outSockets.size() > 0)
    {
    	for (Socket outSocket : outSockets.values()) {
    		try {
    	    	if(outSocket.socket != null)
    	    		outSocket.socket.close();
    		} catch (Exception e) {
    			error(null, e);
    		}
    	outSocket = null;
    	}
    }
    outSockets.clear();
    currentOutSocket = null;
    if (selector != null) {
      try {
        selector.wakeup();
      } catch (Exception e) {
        error(null, e);
      }
      selector = null;
    }
  }

  private void closeOutSocket(Socket out) {
    try {
      for (Entry<String, Socket> e : outSockets.entrySet()) {
        if (e.getValue() == out) {
          outSockets.remove(e.getKey());
          debug("Remove socket " + e.getKey());
          break;
        }
      }
      if (out.socket.isOpen()) {
        out.socket.close();
      }
    } catch (Exception e) {
      error("", e);
    }
  }

  public abstract void debug(String message);

  public abstract void error(String message, Throwable t);

  /**
   * Filter request
   *
   * @param request
   * @return true if request should be filtered/blocked, false otherwise
   */
  private boolean filterRequest(Request request) {
    List<RequestFilter> filters = getRequestFilters();
    if (filters.size() > 0) {
      for (int i = 0; i < filters.size(); i++) {
        RequestFilter filter = filters.get(i);
        if (filter.filter(request)) {
          return true;
        }
      }
    }
    return false;
  }

  private void filterResponse(Response response) {
    List<ResponseFilter> filters = getResponseFilters();
    if (filters.size() > 0) {
      for (int i = 0; i < filters.size(); i++) {
        ResponseFilter filter = filters.get(i);
        filter.filter(response);
      }
    }
  }

  public abstract String getRemoteProxyHost();

  public abstract int getRemoteProxyPort();
  public abstract List<RequestFilter> getRequestFilters();

  public abstract List<ResponseFilter> getResponseFilters();

  public boolean isAlive() {
    return alive;
  }

  public void process(SelectionKey key){
    synchronized (this) {
      ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

      inSocket = new Socket();
      try {
		inSocket.socket = serverSocketChannel.accept();
	    inSocket.socket.configureBlocking(false);
	    selector = SelectorProvider.provider().openSelector();
	    inSocket.socket.register(selector, SelectionKey.OP_READ, inSocket);
	    notify();
      } catch (Exception e) {
	    debug("RequestProcessor >>>>>> can't get serverSocketChannel.");
	}
    }
  }

  private int read(Socket socket, ByteBuffer b, long when){
    int retour = -1;
	try {
		retour = socket.socket.read(b);
		socket.lastWrite = when;
	} catch (IOException e) {
	    debug("RequestProcessor >>>>>> read socket can't be used.");
	}

    return retour;
  }

  private String readNext(ByteBuffer buffer) {
    int ch;
    boolean atCR = false;
    while (buffer.remaining() > 0) {
      ch = buffer.get();
      if (ch == -1 || ch == '\n') {
        atCR = true;
        break;
      }

      if (ch != '\r') {
        if (read_offset == read_buf.length) {
          char tmpbuf[] = read_buf;
          read_buf = new char[tmpbuf.length * 2];
          System.arraycopy(tmpbuf, 0, read_buf, 0, read_offset);
        }
        read_buf[read_offset++] = (char) ch;
      }
    }
    if (!atCR) {
      return null;
    }
    String s = String.copyValueOf(read_buf, 0, read_offset);
    read_offset = 0;
    return s;
  }

  public abstract void recycle();

  public abstract InetAddress resolve(String host);

  public void shutdown() {
    closeAll();
    shutdown = true;
    synchronized (this) {
      notify();
    }
  }

  private RESPONSE_STEP transfer(Socket inSocket, Socket outSocket, long when, Response response, RESPONSE_STEP step) {
    RESPONSE_STEP step_result = step;
    readBuffer.clear();
    int numRead = read(inSocket, readBuffer, when);
    if (numRead == -1) {
      return RESPONSE_STEP.RESPONSE_DONE;
    }
    if (numRead > 0) {
      readBuffer.flip();
      if (step == RESPONSE_STEP.RESPONSE_HEADERS) {
        if (response.addRaw(numRead, readBuffer)) step_result = RESPONSE_STEP.RESPONSE_CONTENT;
      }
      write(outSocket, readBuffer, when);
    }
    return step_result;
  }

  private void write(Socket socket, ByteBuffer b, long when) {
    try {
		socket.socket.write(b);
	    socket.lastWrite = when;
	} catch (IOException e) {
	    debug("RequestProcessor >>>>>> write socket can't be used.");
	}
  }
}
