package com.ebay.maui.util.proxy;
/**
 * 
* RequestFilter.java Create on Feb 6, 2012    
*     
* Copyright (c) Feb 6, 2012  
*     
* @author doxie@ebay.com   
* @version 1.0
*
 */
public interface RequestFilter {

  /**
   * Filter request
   * @param request
   * @return true if proxy should filter/block the request
   */
	public boolean filter(Request request);
}
