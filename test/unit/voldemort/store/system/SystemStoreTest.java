/*
 * Copyright 2012-2013 LinkedIn, Inc
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package voldemort.store.system;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import voldemort.ClusterTestUtils;
import voldemort.ServerTestUtils;
import voldemort.TestUtils;
import voldemort.client.AbstractStoreClientFactory;
import voldemort.client.ClientConfig;
import voldemort.client.SocketStoreClientFactory;
import voldemort.client.SystemStoreClient;
import voldemort.client.SystemStoreClientFactory;
import voldemort.cluster.Cluster;
import voldemort.server.VoldemortConfig;
import voldemort.server.VoldemortServer;
import voldemort.store.StoreDefinition;
import voldemort.store.metadata.MetadataStore;
import voldemort.store.socket.SocketStoreFactory;
import voldemort.store.socket.clientrequest.ClientRequestExecutorPool;

/**
 * Test class to verify the SystemStore (used to interact with the system
 * metadata stores managed by the cluster).
 * 
 * @author csoman
 * 
 */
@RunWith(Parameterized.class)
public class SystemStoreTest {

    private static String storesXmlfile = "test/common/voldemort/config/stores.xml";
    String[] bootStrapUrls = null;
    private String clusterXml;
    private SocketStoreFactory socketStoreFactory = new ClientRequestExecutorPool(2,
                                                                                  10000,
                                                                                  100000,
                                                                                  32 * 1024);

    private VoldemortServer[] servers;
    private Cluster cluster;
    public static String socketUrl = "";
    private final Integer clientZoneId;
    private StoreDefinition storeDef;
    private List<StoreDefinition> storeDefs;


    @Parameterized.Parameters
    public static Collection<Object[]> configs() {
        return Arrays.asList(new Object[][]{
                {0, ClusterTestUtils.getZZZCluster(), ClusterTestUtils.getZZZ322StoreDefs("memory")},
                {1, ClusterTestUtils.getZZZCluster(), ClusterTestUtils.getZZZ322StoreDefs("memory")},
                {2, ClusterTestUtils.getZZZCluster(), ClusterTestUtils.getZZZ322StoreDefs("memory")},
                {1, ClusterTestUtils.getZ1Z3ClusterWithNonContiguousNodeIds(), ClusterTestUtils.getZ1Z3322StoreDefs("memory")},
                {3, ClusterTestUtils.getZ1Z3ClusterWithNonContiguousNodeIds(), ClusterTestUtils.getZ1Z3322StoreDefs("memory")}});
    }

    public SystemStoreTest(Integer clientZoneId, Cluster cluster, List<StoreDefinition> storeDefs) {
        this.clientZoneId = clientZoneId;
        this.cluster = cluster;
        this.storeDefs = storeDefs;
        this.storeDef = storeDefs.get(0);
    }

    @Before
    public void setUp() throws Exception {
        servers = new VoldemortServer[cluster.getNodeIds().size()];

        int i = 0;

        for(Integer nodeId: cluster.getNodeIds()) {
            VoldemortConfig config = ServerTestUtils.createServerConfigWithDefs(true,
                                                                                nodeId,
                                                                                TestUtils.createTempDir()
                                                                                         .getAbsolutePath(),
                                                                                cluster,
                                                                                storeDefs,
                                                                                new Properties());
            VoldemortServer server = ServerTestUtils.startVoldemortServer(socketStoreFactory,
                                                                          config);
            servers[i++] = server;
        }

        socketUrl = servers[0].getIdentityNode().getSocketUrl().toString();

        ClientConfig clientConfig = new ClientConfig().setMaxTotalConnections(4)
                                                      .setMaxConnectionsPerNode(4)
                                                      .setBootstrapUrls(socketUrl);

        SocketStoreClientFactory socketFactory = new SocketStoreClientFactory(clientConfig);
        bootStrapUrls = new String[1];
        bootStrapUrls[0] = socketUrl;
        clusterXml = ((AbstractStoreClientFactory) socketFactory).bootstrapMetadataWithRetries(MetadataStore.CLUSTER_KEY);
    }

    @After
    public void tearDown() throws Exception {
        for(VoldemortServer server: servers) {
            ServerTestUtils.stopVoldemortServer(server);
        }
        ClusterTestUtils.reset();
    }

    @Test
    public void testBasicStore() {
        try {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.setBootstrapUrls(bootStrapUrls).setClientZoneId(this.clientZoneId);
            SystemStoreClientFactory<String, String> systemStoreFactory = new SystemStoreClientFactory<String, String>(clientConfig);
            SystemStoreClient<String, String> sysVersionStore = systemStoreFactory.createSystemStore(SystemStoreConstants.SystemStoreName.voldsys$_metadata_version_persistence.name());

            long storesVersion = 1;
            sysVersionStore.putSysStore("stores.xml", Long.toString(storesVersion));
            long version = Long.parseLong(sysVersionStore.getValueSysStore("stores.xml"));
            assertEquals("Received incorrect version from the voldsys$_metadata_version system store",
                         storesVersion,
                         version);
        } catch(Exception e) {
            fail("Failed to create the default System Store : " + e.getMessage());
        }
    }

    @Test
    public void testCustomClusterXmlStore() {
        try {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.setBootstrapUrls(bootStrapUrls).setClientZoneId(this.clientZoneId);
            SystemStoreClientFactory<String, String> systemStoreFactory = new SystemStoreClientFactory<String, String>(clientConfig);
            SystemStoreClient<String, String> sysVersionStore = systemStoreFactory.createSystemStore(SystemStoreConstants.SystemStoreName.voldsys$_metadata_version_persistence.name(),
                                                                                               this.clusterXml,
                                                                                               null);

            long storesVersion = 1;
            sysVersionStore.putSysStore("stores.xml", Long.toString(storesVersion));
            long version = Long.parseLong(sysVersionStore.getValueSysStore("stores.xml"));
            assertEquals("Received incorrect version from the voldsys$_metadata_version system store",
                         storesVersion,
                         version);
        } catch(Exception e) {
            fail("Failed to create System Store with custom cluster Xml: " + e.getMessage());
        }
    }

    @Test
    public void testIllegalSystemStore() {
        try {
            ClientConfig clientConfig = new ClientConfig();
            clientConfig.setBootstrapUrls(bootStrapUrls).setClientZoneId(this.clientZoneId);
            SystemStoreClientFactory<String, String> systemStoreFactory = new SystemStoreClientFactory<String, String>(clientConfig);
            SystemStoreClient sysVersionStore = systemStoreFactory.createSystemStore("test-store",
                                                                               this.clusterXml,
                                                                               null);

            fail("Should not execute this. We can only connect to system store with a 'voldsys$' prefix.");
        } catch(Exception e) {
            // This is fine.
        }
    }
}
