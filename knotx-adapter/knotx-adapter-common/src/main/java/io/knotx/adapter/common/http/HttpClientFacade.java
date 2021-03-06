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
package io.knotx.adapter.common.http;

import io.knotx.adapter.common.exception.AdapterServiceContractException;
import io.knotx.adapter.common.exception.UnsupportedServiceException;
import io.knotx.adapter.common.placeholders.UriTransformer;
import io.knotx.adapter.common.post.UrlEncodedBodyBuilder;
import io.knotx.dataobjects.AdapterRequest;
import io.knotx.dataobjects.ClientRequest;
import io.knotx.dataobjects.ClientResponse;
import io.knotx.http.AllowedHeadersFilter;
import io.knotx.http.MultiMapCollector;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.rxjava.core.MultiMap;
import io.vertx.rxjava.core.buffer.Buffer;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.HttpClientRequest;
import io.vertx.rxjava.core.http.HttpClientResponse;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.apache.commons.lang3.tuple.Pair;
import rx.Observable;

public class HttpClientFacade {

  private static final Logger LOGGER = LoggerFactory.getLogger(HttpClientFacade.class);
  private static final String PATH_PROPERTY_KEY = "path";
  private static final ClientResponse INTERNAL_SERVER_ERROR_RESPONSE = new ClientResponse()
      .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code());

  private final List<ServiceMetadata> services;

  private final HttpClient httpClient;

  public HttpClientFacade(HttpClient httpClient, List<ServiceMetadata> services) {
    this.httpClient = httpClient;
    this.services = services;
  }

  public Observable<ClientResponse> process(AdapterRequest message, HttpMethod method) {
    return Observable.just(message)
        .doOnNext(this::validateContract)
        .map(this::prepareRequestData)
        .flatMap(serviceRequest -> callService(serviceRequest, method))
        .flatMap(this::wrapResponse)
        .defaultIfEmpty(INTERNAL_SERVER_ERROR_RESPONSE);
  }

  /**
   * Method to validate contract or params JsonObject for the AdapterProxy Service<br>
   * The contract checks if all required fields exists in the object.
   * throwing AdapterServiceContractException in case of contract violation.<br>
   *
   * @param message - Event Bus Json Object message that contains 'clientRequest' and 'params'
   * objects.
   */
  protected void validateContract(AdapterRequest message) {
    if (message.getParams() == null || !message.getParams().containsKey(PATH_PROPERTY_KEY)) {
      throw new AdapterServiceContractException("Parameter `path` was not defined in `params`!");
    }
  }

  /**
   * Method responsible for building request to the service.
   * <br>
   * <br>
   * The responsibility of the method is to build ClientRequest based on the original Http
   * Request<br>
   * - It must set path property of the request based on the params<br>
   * - It might set headers of the request if needed.<br>
   * <br>
   * In case of headers created modified in this method, ensure that your service configuration
   * allows passing those headers to the target service. See 'allowedRequestHeaders' section
   * of the configuration <br>
   *
   * @param originalRequest - ClientRequest representing original request comming to the Knot.x
   * @param params - JsonObject of the params to be used to build request.
   * @return ClientRequest representing Http request to the target service
   */
  protected ClientRequest buildServiceRequest(ClientRequest originalRequest, JsonObject params) {
    return new ClientRequest(originalRequest)
        .setPath(UriTransformer
            .resolveServicePath(params.getString(PATH_PROPERTY_KEY), originalRequest));
  }

  private Pair<ClientRequest, ServiceMetadata> prepareRequestData(AdapterRequest adapterRequest) {
    final Pair<ClientRequest, ServiceMetadata> serviceData;

    final ClientRequest serviceRequest = buildServiceRequest(adapterRequest.getRequest(),
        adapterRequest.getParams());
    final Optional<ServiceMetadata> serviceMetadata = findServiceMetadata(serviceRequest.getPath());

    if (serviceMetadata.isPresent()) {
      serviceData = Pair.of(serviceRequest, serviceMetadata.get());
    } else {
      final String error = String
          .format("No matching service definition for the requested path '%s'",
              serviceRequest.getPath());
      throw new UnsupportedServiceException(error);
    }
    return serviceData;
  }

  private Optional<ServiceMetadata> findServiceMetadata(String servicePath) {
    return services.stream().filter(metadata -> servicePath.matches(metadata.getPath())).findAny();
  }

  private Observable<HttpClientResponse> callService(
      Pair<ClientRequest, ServiceMetadata> serviceData, HttpMethod method) {
    final ClientRequest serviceRequest = serviceData.getLeft();
    final ServiceMetadata serviceMetadata = serviceData.getRight();

    return Observable.create(subscriber -> {
      HttpClientRequest httpRequest = httpClient
          .request(method, serviceMetadata.getPort(), serviceMetadata.getDomain(),
              serviceRequest.getPath());
      Observable<HttpClientResponse> resp = httpRequest.toObservable();
      resp.subscribe(subscriber);

      MultiMap filteredHeaders = getFilteredHeaders(serviceRequest.getHeaders(),
          serviceMetadata.getAllowedRequestHeaderPatterns());
      filteredHeaders.names().forEach(
          headerName -> httpRequest.putHeader(headerName, filteredHeaders.get(headerName)));
      if (!serviceRequest.getFormAttributes().isEmpty()) {
        httpRequest.end(UrlEncodedBodyBuilder.encodeBody(serviceRequest.getFormAttributes()));
      } else {
        httpRequest.end();
      }
    });
  }

  private MultiMap getFilteredHeaders(MultiMap headers, List<Pattern> allowedHeaders) {
    return headers.names().stream()
        .filter(AllowedHeadersFilter.create(allowedHeaders))
        .collect(MultiMapCollector.toMultimap(o -> o, headers::getAll));
  }

  private Observable<ClientResponse> wrapResponse(HttpClientResponse response) {
    return Observable.just(Buffer.buffer())
        .mergeWith(response.toObservable())
        .reduce(Buffer::appendBuffer)
        .doOnNext(this::traceServiceCall)
        .map(buffer -> new ClientResponse()
            .setBody((io.vertx.core.buffer.Buffer) buffer.getDelegate())
            .setHeaders(response.headers())
            .setStatusCode(response.statusCode())
        );
  }

  private void traceServiceCall(Buffer results) {
    if (LOGGER.isTraceEnabled()) {
      LOGGER.trace("Service call returned <{}>", results.toString());
    }
  }
}
