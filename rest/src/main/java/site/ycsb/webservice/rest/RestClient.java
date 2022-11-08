/**
 * Copyright (c) 2016 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package site.ycsb.webservice.rest;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import site.ycsb.*;

import javax.ws.rs.HttpMethod;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Class responsible for making web service requests for benchmarking purpose.
 * Using Apache HttpClient over standard Java HTTP API as this is more flexible
 * and provides better functionality. For example HttpClient can automatically
 * handle redirects and proxy authentication which the standard Java API can't.
 */
public class RestClient extends DB {

  private static final String URL_PREFIX = "url.prefix";
  private static final String CON_TIMEOUT = "timeout.con";
  private static final String READ_TIMEOUT = "timeout.read";
  private static final String EXEC_TIMEOUT = "timeout.exec";
  private static final String LOG_ENABLED = "log.enable";
  private static final String HEADERS = "headers";
  private static final String COMPRESSED_RESPONSE = "response.compression";
  private boolean compressedResponse;
  private boolean logEnabled;
  private String urlPrefix;
  private Properties props;
  private String[] headers;
  private CloseableHttpClient client;
  private int conTimeout = 10000;
  private int readTimeout = 10000;
  private int execTimeout = 10000;
  private volatile Criteria requestTimedout = new Criteria(false);

  @Override
  public void init() throws DBException {
    props = getProperties();
    urlPrefix = props.getProperty(URL_PREFIX, "http://127.0.0.1:8080");
    conTimeout = Integer.valueOf(props.getProperty(CON_TIMEOUT, "10")) * 1000;
    readTimeout = Integer.valueOf(props.getProperty(READ_TIMEOUT, "10")) * 1000;
    execTimeout = Integer.valueOf(props.getProperty(EXEC_TIMEOUT, "10")) * 1000;
    logEnabled = Boolean.valueOf(props.getProperty(LOG_ENABLED, "false").trim());
    compressedResponse = Boolean.valueOf(props.getProperty(COMPRESSED_RESPONSE, "false").trim());
    headers = props.getProperty(HEADERS, "Accept */* Content-Type application/xml user-agent Mozilla/5.0 ").trim()
          .split(" ");
    setupClient();
  }

  private void setupClient() {
    RequestConfig.Builder requestBuilder = RequestConfig.custom();
    requestBuilder = requestBuilder.setConnectTimeout(conTimeout);
    requestBuilder = requestBuilder.setConnectionRequestTimeout(readTimeout);
    requestBuilder = requestBuilder.setSocketTimeout(readTimeout);
    HttpClientBuilder clientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(requestBuilder.build());
    this.client = clientBuilder.setConnectionManagerShared(true).build();
  }

  @Override
  public Status read(String table, String endpoint, Set<String> fields, Map<String, ByteIterator> result) {
    int responseCode;
    try {
      responseCode = httpGet(urlPrefix + endpoint, result);
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + endpoint, HttpMethod.GET);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("GET Request: ").append(urlPrefix).append(endpoint)
            .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status read(String table, String endpoint, Set<String> fields, Map<String, ByteIterator> result, String tid) {
    int responseCode;
    try {
      responseCode = httpGet(urlPrefix + tid + '/' + endpoint, result);
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + tid + '/' + endpoint, HttpMethod.GET);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("GET Request: ").append(urlPrefix).append(endpoint)
          .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status insert(String table, String endpoint, Map<String, ByteIterator> values) {
    int responseCode;
    try {
//      System.out.println("> values.get(\"data\")=" + values.get("data"));
//      System.out.println("> values" + values);
      responseCode = httpExecute(new HttpPost(urlPrefix + endpoint), values.toString());
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + endpoint, HttpMethod.POST);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("POST Request: ").append(urlPrefix).append(endpoint)
            .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status insert(String table, String endpoint, Map<String, ByteIterator> values, String tid) {
    int responseCode;
    try {
      responseCode = httpExecute(new HttpPost(urlPrefix + tid + '/' + endpoint), values.toString());
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + tid + '/' + endpoint, HttpMethod.POST);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("POST Request: ").append(urlPrefix).append(endpoint)
          .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status delete(String table, String endpoint) {
    int responseCode;
    try {
      responseCode = httpDelete(urlPrefix + endpoint);
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + endpoint, HttpMethod.DELETE);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("DELETE Request: ").append(urlPrefix).append(endpoint)
            .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status delete(String table, String endpoint, String tid) {
    int responseCode;
    try {
      responseCode = httpDelete(urlPrefix + tid + '/' + endpoint);
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + tid + '/' + endpoint, HttpMethod.DELETE);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("DELETE Request: ").append(urlPrefix).append(endpoint)
          .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status update(String table, String endpoint, Map<String, ByteIterator> values) {
    int responseCode;
    try {
      responseCode = httpExecute(new HttpPut(urlPrefix + endpoint), values.toString());
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + endpoint, HttpMethod.PUT);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("PUT Request: ").append(urlPrefix).append(endpoint)
            .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status update(String table, String endpoint, Map<String, ByteIterator> values, String tid) {
    int responseCode;
    try {
      responseCode = httpExecute(new HttpPut(urlPrefix + tid + '/' + endpoint), values.toString());
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + tid + '/' + endpoint, HttpMethod.PUT);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("PUT Request: ").append(urlPrefix).append(endpoint)
          .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status scan(String table, String startkey, int recordcount, Set<String> fields,
      Vector<HashMap<String, ByteIterator>> result) {
    return Status.NOT_IMPLEMENTED;
  }

  @Override
  public String prepare() {
    final String prepare = "prepare";
    Map<String, ByteIterator> result = new HashMap<>();

    int responseCode;
    try {
      responseCode = httpGet(urlPrefix + prepare, result);
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + prepare, HttpMethod.GET);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("GET Request: ").append(urlPrefix).append(prepare)
          .append(" | Response Code: ").append(responseCode).toString());
    }
    return result.get("response").toString();
  }

  @Override
  public Status commit(String tid) {
    final String commit = "commit/";
    Map<String, ByteIterator> result = new HashMap<>();

    int responseCode;
    try {
      responseCode = httpGet(urlPrefix + commit + tid, result);
    } catch (Exception e) {
      responseCode = handleExceptions(e, urlPrefix + commit, HttpMethod.GET);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("GET Request: ").append(urlPrefix).append(commit).append(tid)
          .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  @Override
  public Status abort(String tid) {
    return Status.NOT_IMPLEMENTED;
  }

  public Status message(String tid, String dest, String msg) {
    final String messagePath = "message/";
    Map<String, ByteIterator> result = new HashMap<>();

    final String path = urlPrefix + messagePath + tid.concat("/") + dest.concat("/") + msg;

    int responseCode;
    try {
      responseCode = httpGet(path, result);

//      // TODO: check result. Note: messages are sent on commit
//      if(!result.get("response").toString().equals(msg)) {
//        System.out.println("MESSAGE ERROR: "+ result.get("response").toString());
//        return Status.UNEXPECTED_STATE;
//      }
    } catch (Exception e) {
      responseCode = handleExceptions(e, path, HttpMethod.GET);
    }
    if (logEnabled) {
      System.err.println(new StringBuilder("GET Request: ").append(path)
          .append(" | Response Code: ").append(responseCode).toString());
    }
    return getStatus(responseCode);
  }

  // Maps HTTP status codes to YCSB status codes.
  private Status getStatus(int responseCode) {
    int rc = responseCode / 100;
    if (responseCode == 400) {
      return Status.BAD_REQUEST;
    } else if (responseCode == 403) {
      return Status.FORBIDDEN;
    } else if (responseCode == 404) {
      return Status.NOT_FOUND;
    } else if (responseCode == 501) {
      return Status.NOT_IMPLEMENTED;
    } else if (responseCode == 503) {
      return Status.SERVICE_UNAVAILABLE;
    } else if (rc == 5) {
      return Status.ERROR;
    }
    return Status.OK;
  }

  private int handleExceptions(Exception e, String url, String method) {
    if (logEnabled) {
      System.err.println(new StringBuilder(method).append(" Request: ").append(url).append(" | ")
          .append(e.getClass().getName()).append(" occured | Error message: ")
          .append(e.getMessage()).toString());

      e.printStackTrace();
    }
      
    if (e instanceof ClientProtocolException) {
      return 400;
    }
    return 500;
  }

  // Connection is automatically released back in case of an exception.
  private int httpGet(String endpoint, Map<String, ByteIterator> result) throws IOException {
    requestTimedout.setIsSatisfied(false);
    Thread timer = new Thread(new Timer(execTimeout, requestTimedout));
    timer.start();
    int responseCode = 200;
    HttpGet request = new HttpGet(endpoint);
    for (int i = 0; i < headers.length; i = i + 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }
    CloseableHttpResponse response = client.execute(request);
    responseCode = response.getStatusLine().getStatusCode();
    HttpEntity responseEntity = response.getEntity();
    // If null entity don't bother about connection release.
    if (responseEntity != null) {
      InputStream stream = responseEntity.getContent();
      /*
       * TODO: Gzip Compression must be supported in the future. Header[]
       * header = response.getAllHeaders();
       * if(response.getHeaders("Content-Encoding")[0].getValue().contains
       * ("gzip")) stream = new GZIPInputStream(stream);
       */
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      StringBuffer responseContent = new StringBuffer();
      String line = "";
      while ((line = reader.readLine()) != null) {
        if (requestTimedout.isSatisfied()) {
          // Must avoid memory leak.
          reader.close();
          stream.close();
          EntityUtils.consumeQuietly(responseEntity);
          response.close();
          client.close();
          throw new TimeoutException();
        }
        responseContent.append(line);
      }
      timer.interrupt();
      result.put("response", new StringByteIterator(responseContent.toString()));
      // Closing the input stream will trigger connection release.
      stream.close();
    }
    EntityUtils.consumeQuietly(responseEntity);
    response.close();
    client.close();
    return responseCode;
  }

  private int httpExecute(HttpEntityEnclosingRequestBase request, String data) throws IOException {
    requestTimedout.setIsSatisfied(false);
    Thread timer = new Thread(new Timer(execTimeout, requestTimedout));
    timer.start();
    int responseCode = 200;
    for (int i = 0; i < headers.length; i = i + 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }
    InputStreamEntity reqEntity = new InputStreamEntity(new ByteArrayInputStream(data.getBytes()),
          ContentType.APPLICATION_FORM_URLENCODED);
    reqEntity.setChunked(true);
    request.setEntity(reqEntity);
    CloseableHttpResponse response = client.execute(request);
    responseCode = response.getStatusLine().getStatusCode();
    HttpEntity responseEntity = response.getEntity();
    // If null entity don't bother about connection release.
    if (responseEntity != null) {
      InputStream stream = responseEntity.getContent();
      if (compressedResponse) {
        stream = new GZIPInputStream(stream); 
      }
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      StringBuffer responseContent = new StringBuffer();
      String line = "";
      while ((line = reader.readLine()) != null) {
        if (requestTimedout.isSatisfied()) {
          // Must avoid memory leak.
          reader.close();
          stream.close();
          EntityUtils.consumeQuietly(responseEntity);
          response.close();
          client.close();
          throw new TimeoutException();
        }
        responseContent.append(line);
      }
      timer.interrupt();
      // Closing the input stream will trigger connection release.
      stream.close();
    }
    EntityUtils.consumeQuietly(responseEntity);
    response.close();
    client.close();
    return responseCode;
  }
  
  private int httpDelete(String endpoint) throws IOException {
    requestTimedout.setIsSatisfied(false);
    Thread timer = new Thread(new Timer(execTimeout, requestTimedout));
    timer.start();
    int responseCode = 200;
    HttpDelete request = new HttpDelete(endpoint);
    for (int i = 0; i < headers.length; i = i + 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }
    CloseableHttpResponse response = client.execute(request);
    responseCode = response.getStatusLine().getStatusCode();
    response.close();
    client.close();
    return responseCode;
  }

  /**
   * Marks the input {@link Criteria} as satisfied when the input time has elapsed.
   */
  class Timer implements Runnable {

    private long timeout;
    private Criteria timedout;

    public Timer(long timeout, Criteria timedout) {
      this.timedout = timedout;
      this.timeout = timeout;
    }

    @Override
    public void run() {
      try {
        Thread.sleep(timeout);
        this.timedout.setIsSatisfied(true);
      } catch (InterruptedException e) {
        // Do nothing.
      }
    }

  }

  /**
   * Sets the flag when a criteria is fulfilled.
   */
  class Criteria {

    private boolean isSatisfied;

    public Criteria(boolean isSatisfied) {
      this.isSatisfied = isSatisfied;
    }

    public boolean isSatisfied() {
      return isSatisfied;
    }

    public void setIsSatisfied(boolean satisfied) {
      this.isSatisfied = satisfied;
    }

  }

  /**
   * Private exception class for execution timeout.
   */
  class TimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    
    public TimeoutException() {
      super("HTTP Request exceeded execution time limit.");
    }

  }

}
