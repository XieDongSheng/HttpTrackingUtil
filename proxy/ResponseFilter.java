package com.ebay.maui.util.proxy;

/**
 * 
* ResponseFilter.java Create on Feb 6, 2012    
*     
* Copyright (c) Feb 6, 2012  
*     
* @author doxie@ebay.com   
* @version 1.0
*
 */
public interface ResponseFilter {
   /**
   * Filter response
   * @param response
   */
  public void filter(Response response);
}
