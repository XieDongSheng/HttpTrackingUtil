package com.ebay.maui.util.proxy;

import com.google.common.base.Objects;
import org.browsermob.core.har.Har;
import org.browsermob.core.har.HarEntry;
import org.browsermob.core.har.HarNameValuePair;
import org.browsermob.proxy.ProxyServer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class HttpTracker {
    private static final int DEFAULT_PROXY_PORT = 9527;
    private static final String PAGE_NAME = "maui";
    private ProxyServer proxyServer;

    public HttpTracker() {
    }

    public void start(int port) throws Exception {
        this.proxyServer = new ProxyServer(port);
        this.proxyServer.start();
        this.proxyServer.setCaptureHeaders(true);
        this.proxyServer.newHar(PAGE_NAME);
    }

    public void start() throws Exception {
        start(DEFAULT_PROXY_PORT);
    }

    public void stop() throws Exception {
        this.proxyServer.stop();
    }

    public Map<String, String> getRequestHeader(String url) {
        Map<String, Map<String, String>> allHeaders = getAllRequestHeaders();
        Set<String> allUrls = allHeaders.keySet();
        if (allUrls.contains(url)) {
            return allHeaders.get(url);
        } else if (allUrls.contains(appendSlash(url))) {
            return allHeaders.get(appendSlash(url));
        } else {
            return Collections.emptyMap();
        }
    }

    public Map<String, String> getResponseHeader(String url) {
        Map<String, Map<String, String>> allHeaders = getAllResponseHeaders();
        Set<String> allUrls = allHeaders.keySet();
        if (allUrls.contains(url)) {
            return allHeaders.get(url);
        } else if (allUrls.contains(appendSlash(url))) {
            return allHeaders.get(appendSlash(url));
        } else {
            return Collections.emptyMap();
        }
    }

    public ResponseStatus getResponseStatus(String url) {
        Map<String, ResponseStatus> allResponseStatus = getAllResponseStatus();
        Set<String> allUrls = allResponseStatus.keySet();
        if (allUrls.contains(url)) {
            return allResponseStatus.get(url);
        } else if (allUrls.contains(appendSlash(url))) {
            return allResponseStatus.get(appendSlash(url));
        } else {
            return null;
        }
    }

    public List<String> getAllRequestUrls() {
        List<String> result = new ArrayList<String>();
        List<HarEntry> entryList = getHttpEntries();
        for (HarEntry entry : entryList) {
            result.add(entry.getRequest().getUrl());
        }
        return result;
    }

    public Har getHar() {
        return this.proxyServer.getHar();
    }

    public List<HarEntry> getHttpEntries() {
        return getHar().getLog().getEntries();
    }

    public Map<String, Map<String, String>> getAllResponseHeaders() {
        Map<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();

        List<HarEntry> entryList = getHttpEntries();
        for (HarEntry entry : entryList) {
            String url = entry.getRequest().getUrl();
            Map<String, String> headers = new HashMap<String, String>();
            List<HarNameValuePair> pairList = entry.getResponse().getHeaders();
            for (HarNameValuePair p : pairList) {
                headers.put(p.getName(), p.getValue());
            }
            result.put(url, headers);
        }

        return result;
    }

    public Map<String, Map<String, String>> getAllRequestHeaders() {
        Map<String, Map<String, String>> result = new HashMap<String, Map<String, String>>();

        List<HarEntry> entryList = getHttpEntries();
        for (HarEntry entry : entryList) {
            String url = entry.getRequest().getUrl();
            Map<String, String> headers = new HashMap<String, String>();
            List<HarNameValuePair> pairList = entry.getRequest().getHeaders();
            for (HarNameValuePair p : pairList) {
                headers.put(p.getName(), p.getValue());
            }
            result.put(url, headers);
        }

        return result;
    }

    public Map<String, ResponseStatus> getAllResponseStatus() {
        Map<String, ResponseStatus> result = new HashMap<String, ResponseStatus>();
        List<HarEntry> entryList = getHttpEntries();
        for (HarEntry entry : entryList) {
            String url = entry.getRequest().getUrl();
            ResponseStatus status = new ResponseStatus(entry.getResponse().getStatus(), entry.getResponse().getStatusText());
            result.put(url, status);
        }
        return result;
    }

    public static class ResponseStatus {
        private final int code;
        private final String text;

        public ResponseStatus(int code, String text) {
            this.code = code;
            this.text = text;
        }

        public int getCode() {
            return code;
        }

        public String getText() {
            return text;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ResponseStatus) {
                ResponseStatus that = (ResponseStatus) o;
                return Objects.equal(this.code, that.code)
                        && Objects.equal(this.text, that.text);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(this.code, this.text);
        }

        @Override
        public String toString() {
            return "ResponseStatus{code=" + code + ", text=" + text + '}';
        }
    }

    private String appendSlash(String url) {
        if (url.endsWith("/")) return url;

        return url + "/";
    }

}
