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
package io.knotx.adapter.service.http;


import io.knotx.adapter.common.http.HttpAdapterConfiguration;
import io.knotx.adapter.service.http.impl.HttpServiceAdapterProxyImpl;
import io.knotx.proxy.AdapterProxy;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.serviceproxy.ProxyHelper;

public class HttpServiceAdapterVerticle extends AbstractVerticle {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpServiceAdapterVerticle.class);

  private HttpAdapterConfiguration configuration;

  private MessageConsumer<JsonObject> consumer;

  @Override
  public void init(Vertx vertx, Context context) {
    super.init(vertx, context);
    this.configuration = new HttpAdapterConfiguration(config());
  }

  @Override
  public void start() throws Exception {
    LOGGER.info("Starting <{}>", this.getClass().getSimpleName());

    //register the service proxy on event bus
    consumer = ProxyHelper
        .registerService(AdapterProxy.class, vertx,
            new HttpServiceAdapterProxyImpl(new io.vertx.rxjava.core.Vertx(vertx), configuration),
            configuration.getAddress());
  }

  @Override
  public void stop() throws Exception {
    ProxyHelper.unregisterService(consumer);
  }
}
