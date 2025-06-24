/*
 * jabsorb - a Java to JavaScript Advanced Object Request Broker
 * http://www.jabsorb.org
 *
 * Copyright 2007-2009 The jabsorb team
 *
 * based on original code from
 * JSON-RPC-Client, a Java client extension to JSON-RPC-Java
 * (C) Copyright CodeBistro 2007, Sasha Ovsankin <sasha at codebistro dot com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package org.jabsorb.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.jabsorb.client.TransportRegistry.SessionFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transport session straightforwardly implemented in HTTP. As compared to the built-in 
 * URLConnectionSession, it allows more control over HTTP transport parameters, for
 * example, proxies and the support for HTTPS.
 * 
 * <p>To use this transport you need to first register it in the TransportRegistry, for example: <p>
 * <code>
 * 		HTTPSession.register(TransportRegistry.i());
 * </code>
 */
public class HTTPSession implements Session
{
  private final static Logger log = LoggerFactory.getLogger(HTTPSession.class);

  protected HttpClient  client;

  protected URI         uri;

  public HTTPSession(URI uri)
  {
    this.uri = uri;
  }


  /**
   * As per JSON-RPC Working Draft
   * http://json-rpc.org/wd/JSON-RPC-1-1-WD-20060807.html#RequestHeaders
   */
  static final String JSON_CONTENT_TYPE = "application/json";

  public JSONObject sendAndReceive(JSONObject message)
  {
    try
    {
      if (log.isDebugEnabled())
      {
        log.debug("Sending: " + message.toString(2));
      }
      
      HttpRequest request = HttpRequest.newBuilder()
              .uri(uri)
              .header("Content-Type", "application/json") // Set the Content-Type header
              .POST(HttpRequest.BodyPublishers.ofString(message.toString())) // Specify POST method and body
              .build();
      

      HttpResponse<String> postMethod = http().send(request,  HttpResponse.BodyHandlers.ofString());
      int statusCode = postMethod.statusCode();
      if (statusCode != 200)
        throw new ClientError("HTTP Status - (" + statusCode + ")");
      JSONTokener tokener = new JSONTokener(postMethod.body());
      Object rawResponseMessage = tokener.nextValue();
      JSONObject responseMessage = (JSONObject) rawResponseMessage;
      if (responseMessage == null)
        throw new ClientError("Invalid response type - "
            + rawResponseMessage.getClass());
      return responseMessage;
    }
    catch (IOException | JSONException | InterruptedException e)
    {
      throw new ClientError(e);
    }
  }
  
  HttpClient http()
  {
    if (client == null)
    {
      client  = HttpClient.newBuilder()
              .version(HttpClient.Version.HTTP_2) // Optional: Use HTTP/2 if available
              .followRedirects(HttpClient.Redirect.NORMAL) // Optional: How to handle redirects
              .connectTimeout(Duration.ofSeconds(10)) // Optional: Connection timeout
              .build();
    }
    return client;
  }

  public void close()
  {
    client = null;
  }

  static class Factory implements SessionFactory
  {
    public Session newSession(URI uri)
    {
      return new HTTPSession(uri);
    }
  }

  /**
   * Register this transport in 'registry'
   */
  public static void register(TransportRegistry registry)
  {
    registry.registerTransport("http", new Factory());
  }
}
