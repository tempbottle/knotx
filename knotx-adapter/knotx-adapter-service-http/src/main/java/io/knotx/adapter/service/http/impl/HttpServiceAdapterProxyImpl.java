/*
 * Copyright (C) 2016 Cognifide Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.knotx.adapter.service.http.impl;

import io.knotx.adapter.AbstractAdapterProxy;
import io.knotx.adapter.common.http.HttpAdapterConfiguration;
import io.knotx.adapter.common.http.HttpClientFacade;
import io.knotx.dataobjects.AdapterRequest;
import io.knotx.dataobjects.AdapterResponse;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpClient;
import rx.Observable;

public class HttpServiceAdapterProxyImpl extends AbstractAdapterProxy {

  private HttpClientFacade httpClientFacade;

  public HttpServiceAdapterProxyImpl(Vertx vertx, HttpAdapterConfiguration configuration) {
    this.httpClientFacade = new HttpClientFacade(getHttpClient(vertx, configuration),
        configuration.getServices());
  }

  @Override
  protected Observable<AdapterResponse> processRequest(AdapterRequest message) {
    return httpClientFacade.process(message, HttpMethod.GET)
        .map(new AdapterResponse()::setResponse);
  }

  private HttpClient getHttpClient(Vertx vertx, HttpAdapterConfiguration configuration) {
    JsonObject clientOptions = configuration.getClientOptions();
    return clientOptions.isEmpty() ?
        vertx.createHttpClient() : vertx.createHttpClient(new HttpClientOptions(clientOptions));
  }
}
