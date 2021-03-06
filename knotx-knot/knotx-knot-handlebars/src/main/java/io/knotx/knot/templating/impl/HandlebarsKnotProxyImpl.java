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
package io.knotx.knot.templating.impl;

import io.knotx.dataobjects.ClientResponse;
import io.knotx.dataobjects.KnotContext;
import io.knotx.knot.AbstractKnotProxy;
import io.knotx.knot.templating.HandlebarsKnotConfiguration;
import io.knotx.knot.templating.handlebars.CustomHandlebarsHelper;
import io.knotx.knot.templating.helpers.DefaultHandlebarsHelpers;
import com.github.jknack.handlebars.Handlebars;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import rx.Observable;

public class HandlebarsKnotProxyImpl extends AbstractKnotProxy {

  private static final Logger LOGGER = LoggerFactory.getLogger(HandlebarsKnotProxyImpl.class);

  private static final String START_WEBSERVICE_CALL_DEBUG_MARKER = "<!-- start compiled snippet -->";

  private static final String END_WEBSERVICE_CALL_DEBUG_MARKER = "<!-- end compiled snippet -->";

  private static final String DEFAULT_HANDLEBARS_TRANSITION = "next";

  private static final String SUPPORTED_FRAGMENT_KNOT = "handlebars";

  private Handlebars handlebars;

  private HandlebarsKnotConfiguration configuration;

  public HandlebarsKnotProxyImpl(HandlebarsKnotConfiguration configuration) {
    this.configuration = configuration;
    this.handlebars = createHandlebars();
  }

  @Override
  protected Observable<KnotContext> processRequest(KnotContext knotContext) {
    return Observable.create(observer -> {
      try {
        knotContext.setTransition(DEFAULT_HANDLEBARS_TRANSITION);
        Optional.ofNullable(knotContext.getFragments()).ifPresent(fragments ->
            fragments.stream()
                .filter(fragment -> fragment.knots().contains(SUPPORTED_FRAGMENT_KNOT))
                .forEach(fragment -> fragment.content(startComment() +
                    new HandlebarsFragment(fragment).compileWith(handlebars)
                    + endComment()))
        );
        observer.onNext(knotContext);
        observer.onCompleted();
      } catch (Exception e) {
        observer.onError(e);
      }
    });
  }

  @Override
  protected boolean shouldProcess(Set<String> knots) {
    return knots.contains(SUPPORTED_FRAGMENT_KNOT);
  }

  @Override
  protected KnotContext processError(KnotContext knotContext, Throwable error) {
    LOGGER.error("Error happened during Template processing", error);
    ClientResponse errorResponse = new ClientResponse()
        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

    return new KnotContext()
        .setClientRequest(knotContext.getClientRequest())
        .setClientResponse(errorResponse);
  }

  private String startComment() {
    return snippetComment(START_WEBSERVICE_CALL_DEBUG_MARKER);
  }

  private String endComment() {
    return snippetComment(END_WEBSERVICE_CALL_DEBUG_MARKER);
  }

  private String snippetComment(String commentTemplate) {
    String debugLine = StringUtils.EMPTY;
    if (configuration.templateDebug()) {
      debugLine = commentTemplate;
    }
    return debugLine;
  }

  private Handlebars createHandlebars() {
    Handlebars handlebars = new Handlebars();
    DefaultHandlebarsHelpers.registerFor(handlebars);

    ServiceLoader.load(CustomHandlebarsHelper.class)
        .iterator().forEachRemaining(helper -> {
      handlebars.registerHelper(helper.getName(), helper);
      LOGGER.info("Registered custom Handlebars helper: {}", helper.getName());
    });

    return handlebars;
  }
}
