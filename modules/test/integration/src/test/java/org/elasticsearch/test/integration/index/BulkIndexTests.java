/*
 * Licensed to Elastic Search and Shay Banon under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Elastic Search licenses this
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

package org.elasticsearch.test.integration.index;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.action.bulk.BulkRequestBuilder;
import org.elasticsearch.common.collect.Sets;
import org.elasticsearch.common.settings.ImmutableSettings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.test.integration.AbstractNodesTests;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.Set;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;
import static org.elasticsearch.index.query.QueryBuilders.matchAllQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author drewr (Drew Raines)
 */
public class BulkIndexTests extends AbstractNodesTests {
    private Client client;

    @BeforeClass public void createNodes() throws Exception {
        startNode("node1", ImmutableSettings.settingsBuilder()
                .put("node.local", "true")
                .put("index.store.type", "ram")
                .build());
        client = getClient();
    }

    @AfterClass public void closeNodes() {
        client.close();
        closeAllNodes();
    }

    protected Client getClient() {
        return client("node1");
    }

    @Test public void testBulkIndexing() throws Exception {
        try {
            client.admin().indices().prepareDelete("test").execute().actionGet();
        } catch (Exception e) {
            // ignore
        }
        client.admin().indices().prepareCreate("test").setSettings(ImmutableSettings.settingsBuilder().put("index.number_of_shards", 1)).execute().actionGet();
        client.admin().cluster().prepareHealth().setWaitForGreenStatus().execute().actionGet();

        Set<String> ids = Sets.newHashSet();
        Set<String> expectedIds = Sets.newHashSet();

        BulkRequestBuilder bulk = client.prepareBulk();

        for (int i = 0; i < 100; i++) {
            expectedIds.add(Integer.toString(i));
            HashMap map = new HashMap();
            map.put("id", Integer.toString(i));
            map.put("text", "The quick brown fox jumps over the lazy dog");

            bulk.add(client.prepareIndex("test", "tweet").setSource(map));
        }

        bulk.execute().actionGet();
        client.admin().indices().prepareRefresh().execute().actionGet();

        SearchResponse searchResponse = client.prepareSearch()
                .setQuery(matchAllQuery())
                .setSize(100)
                .execute()
                .actionGet();

        assertThat(searchResponse.hits().getTotalHits(), equalTo(100l));
        for (SearchHit hit : searchResponse.hits()) {
            //assertThat(hit.id() + "should not exists in the result set", ids.contains(hit.id()), equalTo(false));
            ids.add((String) hit.sourceAsMap().get("id"));
        }
        assertThat(expectedIds, equalTo(ids));
    }

}
