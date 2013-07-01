/*
 * Licensed to ElasticSearch under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. ElasticSearch licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.cloud.gce;

import com.google.api.client.googleapis.compute.ComputeCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.compute.Compute;
import org.elasticsearch.ElasticSearchException;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.discovery.DiscoveryException;

/**
 *
 */
public class GceComputeService extends AbstractLifecycleComponent<GceComputeService> {

    static final class Fields {
        private static final String VERSION = "Elasticsearch/GceCloud/1.0";
    }

    private Compute client;
    private TimeValue refreshInterval = null;
    private long lastRefresh;

    /** Global instance of the HTTP transport. */
    private static HttpTransport HTTP_TRANSPORT;

    /** Global instance of the JSON factory. */
    private static JsonFactory JSON_FACTORY;

    @Inject
    public GceComputeService(Settings settings, SettingsFilter settingsFilter) {
        super(settings);
        settingsFilter.addFilter(new GceSettingsFilter());
    }

    public synchronized Compute client() {
        if (refreshInterval != null && refreshInterval.millis() != 0) {
            if (client != null &&
                    (refreshInterval.millis() < 0 || (System.currentTimeMillis() - lastRefresh) < refreshInterval.millis())) {
                if (logger.isTraceEnabled()) logger.trace("using cache to retrieve client");
                return client;
            }
            lastRefresh = System.currentTimeMillis();
        }

        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            JSON_FACTORY = new JacksonFactory();

            logger.debug("starting GCE discovery service");
            ComputeCredential credential = new ComputeCredential.Builder(HTTP_TRANSPORT, JSON_FACTORY).build();
            credential.refreshToken();

            logger.debug("token [{}] will expire in [{}] s", credential.getAccessToken(), credential.getExpiresInSeconds());
            refreshInterval = TimeValue.timeValueSeconds(credential.getExpiresInSeconds()-1);

            // Once done, let's use this token
            this.client = new Compute.Builder(HTTP_TRANSPORT, JSON_FACTORY, null)
                    .setApplicationName(Fields.VERSION)
                    .setHttpRequestInitializer(credential)
                    .build();
        } catch (Exception e) {
            logger.warn("unable to start GCE discovery service: {} : {}", e.getClass().getName(), e.getMessage());
            throw new DiscoveryException("unable to start GCE discovery service", e);
        }

        return this.client;
    }

    @Override
    protected void doStart() throws ElasticSearchException {
    }

    @Override
    protected void doStop() throws ElasticSearchException {
    }

    @Override
    protected void doClose() throws ElasticSearchException {
    }
}
