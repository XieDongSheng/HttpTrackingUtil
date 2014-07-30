package com.ebay.maui.util.proxy;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import org.testng.Assert;

import com.ebay.maui.controller.Logging;

/**
 * 
* HttpTrackUtil.java Create on Apr 24, 2012    
*     
* Copyright (c) Apr 24, 2012  
*     
* @author doxie@ebay.com   
* @version 1.0
*
 */
public class HttpTrackUtil {

	protected static HttpTrackProxy proxy;
	protected static int localProxyPort = 5368;
	private static Map<String, Vector<Map<String, String>>> values = new HashMap<String, Vector<Map<String, String>>>();
	private static Map<String, Map<String, String>> headers = new HashMap<String, Map<String, String>>();
	
	public static void captureHttpRequest(final String url) {

		proxy.getRequestFilters().add(new RequestFilter() {
			public boolean filter(Request request) {
				if (request.getUrl().startsWith(url)) {
					int index = url.length();
					Assert.assertTrue(index != request.getUrl().length() - 1, "Total url:" + request.getUrl() + " missed!");
					String querys = request.getUrl().substring(index);
					String[] temps = querys.split("&");
					for (int i = 0; i < temps.length; i++) {
						Map<String, String> localValues = new HashMap<String, String>();
						int in = temps[i].indexOf("=");
						localValues.put(temps[i].substring(0, in), temps[i].substring(in + 1));
						Vector<Map<String, String>> vec = values.get(url);
						if (vec != null && vec.size() > 0) {
							vec.add(localValues);
							values.put(url, vec);
						} else {
							Vector<Map<String, String>> newVec = new Vector<Map<String, String>>();
							newVec.add(localValues);
							values.put(url, newVec);
						}
					}

					Logging.logWebStep(null, "Query value list of " + url + "\n" + querys.replace("&", "\n"), false);
					return true;
				} else
					return false;
			}
		});
	}
	
	public static void  captureHttpResponse(final String[] urlArray) {

		proxy.getResponseFilters().add(new ResponseFilter() {

			public void filter(Response response) {
				for(String url : urlArray)
				{
					if(response.getRequest().getUrl().startsWith(url))
					{
						headers.put(url, response.getHeaders());
					}
				}
				
			}});
	}

	private static void initProxy() {
		proxy = new HttpTrackProxy();

		proxy.setPort(localProxyPort);

		try {
			proxy.start();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void startProxy() throws Exception {
		if (proxy == null) {
			initProxy();
		} else if (!proxy.isRunning()) {
			proxy.start();
		}
	}

	public static void stopProxy() {
		try {
			if (proxy != null)
				proxy.stop();
			values.clear();
			headers.clear();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static Map<String, String> getHttpResponseHeaders(String url)
	{
		return headers.get(url);
	}
	
	public static void verifyHttpRequest(String url, String parameter, String value) throws Exception {

		Vector<Map<String, String>> vec = values.get(url);
		if (vec == null || vec.size() == 0) {
			Assert.assertTrue(values.get(parameter).contains(value), "Check parameter {" + parameter + "} with value {" + value + "} failed.\n");
			Logging.logWebStep(null, "Parameter{" + parameter + "} not found!", false);
			return;
		}
		for (Map<String, String> map : vec) {

			if (map.get(parameter) != null && map.get(parameter).contains(value)) {
				if (map.get(parameter).equals(value)) {
					Logging.logWebStep(null, "Check parameter {" + parameter + "} with value {" + value + "} passed", false);
					return;
				} else {
					Logging.logWebStep(null, "Attention: {" + parameter + "} with value {" + value + "} not exactly equals,only 'Contains' level!", false);
					return;
				}
			}
		}
		Assert.assertTrue(values.get(parameter).contains(value), "Check parameter {" + parameter + "} with value {" + value + "} failed.\n");
		Logging.logWebStep(null, "Parameter{" + parameter + "} not found!", false);
	}

	public static void verifyHttpRequest(String url, String refParameter, String parameter, String value) throws Exception {

		Vector<Map<String, String>> vec = values.get(url);
		if (vec == null || vec.size() == 0) {
			Assert.assertTrue(values.get(parameter).contains(value), "Check parameter {" + parameter + "} with value {" + value + "} failed.\n");
			Logging.logWebStep(null, "Parameter{" + parameter + "} not found!", false);
			return;
		}
		for (Map<String, String> map : vec) {

			if (map.get(parameter) != null && map.get(refParameter) != null && map.get(parameter).contains(value)) {
				if (map.get(parameter).equals(value)) {
					Logging.logWebStep(null, "Check parameter {" + parameter + "} with value {" + value + "} passed", false);
					return;
				} else {
					Logging.logWebStep(null, "Attention: {" + parameter + "} with value {" + value + "} not exactly equals,only 'Contains' level!", false);
					return;
				}
			}
		}
		Assert.assertTrue(values.get(parameter).contains(value), "Check parameter {" + parameter + "} with value {" + value + "} failed.\n");
		Logging.logWebStep(null, "Parameter{" + parameter + "} not found!", false);
	}

}
