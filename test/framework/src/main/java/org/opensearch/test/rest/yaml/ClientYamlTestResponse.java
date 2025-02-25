/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.test.rest.yaml;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.util.EntityUtils;
import org.opensearch.client.Response;
import org.opensearch.common.Strings;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Response obtained from a REST call, eagerly reads the response body into a string for later optional parsing.
 * Supports parsing the response body when needed and returning specific values extracted from it.
 */
public class ClientYamlTestResponse {

    private final Response response;
    private final byte[] body;
    private final XContentType bodyContentType;
    private ObjectPath parsedResponse;
    private String bodyAsString;

    public ClientYamlTestResponse(Response response) throws IOException {
        this.response = response;
        if (response.getEntity() != null) {
            String contentType = response.getHeader("Content-Type");
            this.bodyContentType = XContentType.fromMediaType(contentType);
            try {
                byte[] bytes = EntityUtils.toByteArray(response.getEntity());
                // skip parsing if we got text back (e.g. if we called _cat apis)
                if (bodyContentType != null) {
                    this.parsedResponse = ObjectPath.createFromXContent(bodyContentType.xContent(), new BytesArray(bytes));
                }
                this.body = bytes;
            } catch (IOException e) {
                EntityUtils.consumeQuietly(response.getEntity());
                throw e;
            }
        } else {
            this.body = null;
            this.bodyContentType = null;
        }
    }

    public int getStatusCode() {
        return response.getStatusLine().getStatusCode();
    }

    public String getReasonPhrase() {
        return response.getStatusLine().getReasonPhrase();
    }

    /**
     * Get a list of all of the values of all warning headers returned in the response.
     */
    public List<String> getWarningHeaders() {
        List<String> warningHeaders = new ArrayList<>();
        for (Header header : response.getHeaders()) {
            if (header.getName().equals("Warning")) {
                warningHeaders.add(header.getValue());
            }
        }
        return warningHeaders;
    }

    /**
     * Returns the body properly parsed depending on the content type.
     * Might be a string or a json object parsed as a map.
     */
    public Object getBody() throws IOException {
        if (parsedResponse != null) {
            return parsedResponse.evaluate("");
        }
        // we only get here if there is no response body or the body is text
        assert bodyContentType == null;
        return getBodyAsString();
    }

    /**
     * Returns the body as a string
     */
    public String getBodyAsString() {
        if (bodyAsString == null && body != null) {
            // content-type null means that text was returned
            if (bodyContentType == null || bodyContentType == XContentType.JSON || bodyContentType == XContentType.YAML) {
                bodyAsString = new String(body, StandardCharsets.UTF_8);
            } else {
                // if the body is in a binary format and gets requested as a string (e.g. to log a test failure), we convert it to json
                try (XContentBuilder jsonBuilder = XContentFactory.jsonBuilder()) {
                    try (
                        XContentParser parser = bodyContentType.xContent()
                            .createParser(NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, body)
                    ) {
                        jsonBuilder.copyCurrentStructure(parser);
                    }
                    bodyAsString = Strings.toString(jsonBuilder);
                } catch (IOException e) {
                    throw new UncheckedIOException("unable to convert response body to a string format", e);
                }
            }
        }
        return bodyAsString;
    }

    public boolean isError() {
        return response.getStatusLine().getStatusCode() >= 400;
    }

    /**
     * Parses the response body and extracts a specific value from it (identified by the provided path)
     */
    public Object evaluate(String path) throws IOException {
        return evaluate(path, Stash.EMPTY);
    }

    /**
     * Parses the response body and extracts a specific value from it (identified by the provided path)
     */
    public Object evaluate(String path, Stash stash) throws IOException {
        if (response == null) {
            return null;
        }

        if (parsedResponse == null) {
            // special case: api that don't support body (e.g. exists) return true if 200, false if 404, even if no body
            // is_true: '' means the response had no body but the client returned true (caused by 200)
            // is_false: '' means the response had no body but the client returned false (caused by 404)
            if ("".equals(path) && HttpHead.METHOD_NAME.equals(response.getRequestLine().getMethod())) {
                return isError() == false;
            }
            return null;
        }

        return parsedResponse.evaluate(path, stash);
    }
}
