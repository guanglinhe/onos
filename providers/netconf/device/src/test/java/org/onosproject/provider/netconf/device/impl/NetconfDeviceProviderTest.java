/*
 * Copyright 2016-present Open Networking Laboratory
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

package org.onosproject.provider.netconf.device.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.onlab.packet.ChassisId;
import org.onlab.packet.IpAddress;
import org.onosproject.cfg.ComponentConfigAdapter;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.incubator.net.config.basics.ConfigException;
import org.onosproject.mastership.MastershipService;
import org.onosproject.mastership.MastershipServiceAdapter;
import org.onosproject.net.AbstractProjectableModel;
import org.onosproject.net.Annotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.ConfigApplyDelegate;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.config.NetworkConfigRegistryAdapter;
import org.onosproject.net.config.basics.BasicDeviceConfig;
import org.onosproject.net.device.DeviceDescription;
import org.onosproject.net.device.DeviceDescriptionDiscovery;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceProvider;
import org.onosproject.net.device.DeviceProviderRegistry;
import org.onosproject.net.device.DeviceProviderRegistryAdapter;
import org.onosproject.net.device.DeviceProviderService;
import org.onosproject.net.device.DeviceProviderServiceAdapter;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.device.DeviceServiceAdapter;
import org.onosproject.net.device.DeviceStore;
import org.onosproject.net.device.DeviceStoreAdapter;
import org.onosproject.net.device.PortDescription;
import org.onosproject.net.driver.AbstractHandlerBehaviour;
import org.onosproject.net.driver.Behaviour;
import org.onosproject.net.driver.Driver;
import org.onosproject.net.driver.DriverAdapter;
import org.onosproject.net.driver.DriverHandler;
import org.onosproject.net.driver.DriverServiceAdapter;
import org.onosproject.net.key.DeviceKeyAdminService;
import org.onosproject.net.key.DeviceKeyAdminServiceAdapter;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.netconf.NetconfController;
import org.onosproject.netconf.NetconfDeviceListener;
import org.onosproject.netconf.config.NetconfDeviceConfig;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import static org.easymock.EasyMock.*;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;
import static org.onlab.junit.TestTools.assertAfter;
import static org.onosproject.provider.netconf.device.impl.NetconfDeviceProvider.APP_NAME;

/**
 * Netconf device provider basic test.
 */
public class NetconfDeviceProviderTest {

    private final NetconfDeviceProvider provider = new NetconfDeviceProvider();
    private final NetconfController controller = new MockNetconfController();

    //Provider Mock
    private final DeviceProviderRegistry deviceRegistry = new MockDeviceProviderRegistry();
    private final DeviceProviderService providerService = new MockDeviceProviderService();
    private final DeviceService deviceService = new MockDeviceService();
    private final MastershipService mastershipService = new MockMastershipService();
    private final Driver driver = new MockDriver();
    private final NetworkConfigRegistry cfgService = new MockNetworkConfigRegistry();
    private final Set<ConfigFactory> cfgFactories = new HashSet<>();
    private final DeviceKeyAdminService deviceKeyAdminService = new DeviceKeyAdminServiceAdapter();
    private final DeviceStore deviceStore = new MockDeviceStore();

    //Class for testing
    private final NetconfDeviceConfig netconfDeviceConfig = new NetconfDeviceConfig();
    private final NetconfDeviceConfig netconfDeviceConfigSshKey = new NetconfDeviceConfig();
    private final NetconfDeviceConfig netconfDeviceConfigEmptyIpv4 = new NetconfDeviceConfig();
    private final NetconfDeviceConfig netconfDeviceConfigEmptyIpv6 = new NetconfDeviceConfig();
    private final NetworkConfigEvent deviceAddedEvent =
            new NetworkConfigEvent(NetworkConfigEvent.Type.CONFIG_ADDED,
                                   DeviceId.deviceId(NETCONF_DEVICE_ID_STRING),
                                   netconfDeviceConfig, null,
                                   NetconfDeviceConfig.class);

    private final NetworkConfigEvent deviceAddedEventOld =
            new NetworkConfigEvent(NetworkConfigEvent.Type.CONFIG_ADDED,
                                   null, NetconfProviderConfig.class);
    private final NetworkConfigEvent deviceAddedEventTranslated =
            new NetworkConfigEvent(NetworkConfigEvent.Type.CONFIG_ADDED,
                                   DeviceId.deviceId(NETCONF_DEVICE_ID_STRING_OLD),
                                   NetconfDeviceConfig.class);
    private final NetconfProviderConfig netconfProviderConfig = new MockNetconfProviderConfig();
    private static final String NETCONF_DEVICE_ID_STRING = "netconf:1.1.1.1:830";
    private static final String NETCONF_DEVICE_ID_STRING_OLD = "netconf:1.1.1.2:1";
    private static final String NETCONF_DEVICE_ID_STRING_IPV6 = "netconf:2001:0db8:0000:0000:0000:ff00:0042:8329:830";
    private static final String IP_STRING = "1.1.1.1";
    private static final String IP_STRING_OLD = "1.1.1.2";
    private static final String IP_STRING_IPV6 = "2001:0db8:0000:0000:0000:ff00:0042:8329";
    private static final IpAddress IP = IpAddress.valueOf(IP_STRING);
    private static final IpAddress IP_OLD = IpAddress.valueOf(IP_STRING_OLD);
    private static final IpAddress IP_V6 = IpAddress.valueOf(IP_STRING_IPV6);
    private static final int PORT = 830;
    private static final String TEST = "test";
    private static final int DELAY_DISCOVERY = 500;
    private static final int DELAY_DURATION_DISCOVERY = 3000;

    //Testing Files
    InputStream jsonStream = NetconfDeviceProviderTest.class
            .getResourceAsStream("/device.json");
    InputStream jsonStreamSshKey = NetconfDeviceProviderTest.class
            .getResourceAsStream("/deviceSshKey.json");

    //Provider related classes
    private CoreService coreService;
    private ApplicationId appId =
            new DefaultApplicationId(100, APP_NAME);
    private DeviceDescriptionDiscovery descriptionDiscovery = new TestDescription();
    private Set<DeviceListener> deviceListeners = new HashSet<>();
    private Set<NetworkConfigListener> netCfgListeners = new HashSet<>();
    private HashMap<DeviceId, Device> devices = new HashMap<>();

    //Controller related classes
    private Set<NetconfDeviceListener> netconfDeviceListeners = new CopyOnWriteArraySet<>();
    private boolean available = false;
    private boolean firstRequest = true;

    @Before
    public void setUp() throws IOException {
        coreService = createMock(CoreService.class);
        expect(coreService.registerApplication(APP_NAME))
                .andReturn(appId).anyTimes();
        replay(coreService);
        provider.coreService = coreService;
        provider.providerRegistry = deviceRegistry;
        provider.mastershipService = mastershipService;
        provider.deviceService = deviceService;
        provider.cfgService = cfgService;
        provider.controller = controller;
        provider.deviceKeyAdminService = deviceKeyAdminService;
        provider.componentConfigService = new ComponentConfigAdapter();
        AbstractProjectableModel.setDriverService(null, new DriverServiceAdapter());
        provider.activate(null);
        devices.clear();
        available = false;
        firstRequest = true;
        DeviceId subject = DeviceId.deviceId(NETCONF_DEVICE_ID_STRING);
        DeviceId subjectIpv6 = DeviceId.deviceId(NETCONF_DEVICE_ID_STRING_IPV6);
        String key = "netconf";
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(jsonStream);
        ConfigApplyDelegate delegate = new MockDelegate();
        netconfDeviceConfig.init(subject, key, jsonNode, mapper, delegate);
        JsonNode jsonNodesshKey = mapper.readTree(jsonStreamSshKey);
        netconfDeviceConfigSshKey.init(subject, key, jsonNodesshKey, mapper, delegate);
        JsonNode jsonNodeEmpty = mapper.createObjectNode();
        netconfDeviceConfigEmptyIpv4.init(subject, key, jsonNodeEmpty, mapper, delegate);
        netconfDeviceConfigEmptyIpv6.init(subjectIpv6, key, jsonNodeEmpty, mapper, delegate);
    }

    @Test
    public void activate() throws Exception {
        assertTrue("Provider should be registered", deviceRegistry.getProviders().contains(provider.id()));
        assertEquals("Incorrect device service", deviceService, provider.deviceService);
        assertEquals("Incorrect provider service", providerService, provider.providerService);
        assertTrue("Incorrect config factories", cfgFactories.containsAll(provider.factories));
        assertEquals("Device listener should be added", 1, deviceListeners.size());
        assertFalse("Thread to connect device should be running",
                    provider.executor.isShutdown() || provider.executor.isTerminated());
        assertFalse("Scheduled task to update device should be running", provider.scheduledTask.isCancelled());
    }

    @Test
    public void deactivate() throws Exception {
        provider.deactivate();
        assertEquals("Device listener should be removed", 0, deviceListeners.size());
        assertFalse("Provider should not be registered", deviceRegistry.getProviders().contains(provider));
        assertTrue("Thread to connect device should be shutdown", provider.executor.isShutdown());
        assertTrue("Scheduled task to update device should be shutdown", provider.scheduledTask.isCancelled());
        assertNull("Provider service should be null", provider.providerService);
        assertTrue("Network config factories not removed", cfgFactories.isEmpty());
        assertEquals("Controller listener should be removed", 0, netconfDeviceListeners.size());
    }

    @Test
    public void configuration() {
        assertTrue("Configuration should be valid", netconfDeviceConfig.isValid());
        assertThat(netconfDeviceConfig.ip(), is(IP));
        assertThat(netconfDeviceConfig.port(), is(PORT));
        assertThat(netconfDeviceConfig.username(), is(TEST));
        assertThat(netconfDeviceConfig.password(), is(TEST));
        assertThat(netconfDeviceConfigSshKey.sshKey(), is(TEST));
    }

    @Test
    public void configurationDeviceIdIpv4() {
        assertTrue("Configuration should be valid", netconfDeviceConfigEmptyIpv4.isValid());
        assertThat(netconfDeviceConfigEmptyIpv4.ip(), is(IP));
        assertThat(netconfDeviceConfigEmptyIpv4.port(), is(PORT));
        assertThat(netconfDeviceConfigEmptyIpv4.username(), is(StringUtils.EMPTY));
        assertThat(netconfDeviceConfigEmptyIpv4.password(), is(StringUtils.EMPTY));
        assertThat(netconfDeviceConfigEmptyIpv4.sshKey(), is(StringUtils.EMPTY));
    }

    @Test
    public void configurationDeviceIdIpv6() {
        assertTrue("Configuration should be valid", netconfDeviceConfigEmptyIpv6.isValid());
        assertThat(netconfDeviceConfigEmptyIpv6.ip(), is(IP_V6));
        assertThat(netconfDeviceConfigEmptyIpv6.port(), is(PORT));
        assertThat(netconfDeviceConfigEmptyIpv6.username(), is(StringUtils.EMPTY));
        assertThat(netconfDeviceConfigEmptyIpv6.password(), is(StringUtils.EMPTY));
        assertThat(netconfDeviceConfigEmptyIpv6.sshKey(), is(StringUtils.EMPTY));
    }

    @Test
    @Ignore("Test is brittle")
    public void addDeviceOld() {
        assertNotNull(providerService);
        assertTrue("Event should be relevant", provider.cfgListener.isRelevant(deviceAddedEvent));
        assertTrue("Event should be relevant", provider.cfgListener.isRelevant(deviceAddedEventOld));
        available = true;
        provider.cfgListener.event(deviceAddedEventOld);

        assertAfter(DELAY_DISCOVERY, DELAY_DURATION_DISCOVERY, () -> {
            assertEquals("Device should be added", 1, deviceStore.getDeviceCount());
            assertTrue("Device incorrectly added" + NETCONF_DEVICE_ID_STRING_OLD,
                       devices.containsKey(DeviceId.deviceId(NETCONF_DEVICE_ID_STRING_OLD)));
        });
        devices.clear();
    }

    @Test
    public void addDeviceNew() {
        assertNotNull(providerService);
        assertTrue("Event should be relevant", provider.cfgListener.isRelevant(deviceAddedEvent));
        assertTrue("Event should be relevant", provider.cfgListener.isRelevant(deviceAddedEventOld));
        available = true;
        provider.cfgListener.event(deviceAddedEvent);

        assertAfter(DELAY_DISCOVERY, DELAY_DURATION_DISCOVERY, () -> {
            assertEquals("Device should be added", 1, deviceStore.getDeviceCount());
            assertTrue("Device incorrectly added" + NETCONF_DEVICE_ID_STRING,
                       devices.containsKey(DeviceId.deviceId(NETCONF_DEVICE_ID_STRING)));
        });
        devices.clear();
    }

    //TODO: implement ports discovery and check updates of the device description


    //Mock classes
    private class MockNetconfController extends NetconfControllerAdapter {

        @Override
        public void addDeviceListener(NetconfDeviceListener listener) {
            if (!netconfDeviceListeners.contains(listener)) {
                netconfDeviceListeners.add(listener);
            }
        }

        @Override
        public void removeDeviceListener(NetconfDeviceListener listener) {
            netconfDeviceListeners.remove(listener);
        }
    }

    private class MockDeviceProviderRegistry extends DeviceProviderRegistryAdapter {

        Set<ProviderId> providers = new HashSet<>();

        @Override
        public DeviceProviderService register(DeviceProvider provider) {
            providers.add(provider.id());
            return providerService;
        }

        @Override
        public void unregister(DeviceProvider provider) {
            providers.remove(provider.id());
        }

        @Override
        public Set<ProviderId> getProviders() {
            return providers;
        }

    }

    private class MockDeviceService extends DeviceServiceAdapter {
        @Override
        public void addListener(DeviceListener listener) {
            deviceListeners.add(listener);
        }

        @Override
        public void removeListener(DeviceListener listener) {
            deviceListeners.remove(listener);
        }
    }

    private class MockDeviceProviderService extends DeviceProviderServiceAdapter {

        @Override
        public void deviceConnected(DeviceId deviceId, DeviceDescription desc) {
            assertNotNull("DeviceId should be not null", deviceId);
            assertNotNull("DeviceDescription should be not null", desc);
            deviceStore.createOrUpdateDevice(ProviderId.NONE, deviceId, desc);
        }
    }

    private class MockDeviceStore extends DeviceStoreAdapter {

        @Override
        public DeviceEvent createOrUpdateDevice(ProviderId providerId, DeviceId deviceId,
                                                DeviceDescription desc) {

            devices.put(deviceId, new DefaultDevice(providerId, deviceId, desc.type(),
                                                    desc.manufacturer(), desc.hwVersion(),
                                                    desc.swVersion(), desc.serialNumber(),
                                                    desc.chassisId(), desc.annotations()));
            return null;
        }

        @Override
        public Device getDevice(DeviceId deviceId) {
            return devices.get(deviceId);
        }

        @Override
        public int getDeviceCount() {
            return devices.size();
        }

    }

    private class MockMastershipService extends MastershipServiceAdapter {

        @Override
        public boolean isLocalMaster(DeviceId deviceId) {
            return true;
        }
    }

    private class MockNetworkConfigRegistry extends NetworkConfigRegistryAdapter {
        NetconfDeviceConfig cfg = null;

        @Override
        public void registerConfigFactory(ConfigFactory configFactory) {
            cfgFactories.add(configFactory);
        }

        @Override
        public void unregisterConfigFactory(ConfigFactory configFactory) {
            cfgFactories.remove(configFactory);
        }

        @Override
        public void addListener(NetworkConfigListener listener) {
            netCfgListeners.add(listener);
        }

        @Override
        public void removeListener(NetworkConfigListener listener) {
            netCfgListeners.remove(listener);
        }


        @Override
        public <S, C extends Config<S>> C getConfig(S subject, Class<C> configClass) {
            if (available) {
                if (configClass.equals(NetconfProviderConfig.class)) {
                    return (C) netconfProviderConfig;
                }
                DeviceId did = (DeviceId) subject;
                if (configClass.equals(NetconfDeviceConfig.class)
                        && did.equals(DeviceId.deviceId(NETCONF_DEVICE_ID_STRING))) {
                    return (C) netconfDeviceConfig;
                } else if (configClass.equals(NetconfDeviceConfig.class)
                        && did.equals(DeviceId.deviceId(NETCONF_DEVICE_ID_STRING_OLD))) {
                    if (firstRequest) {
                        firstRequest = false;
                        return null;
                    }
                    return (C) cfg;
                } else {
                    return (C) new BasicDeviceConfig();
                }
            }
            return null;
        }

        @Override
        public <S, C extends Config<S>> C applyConfig(S subject, Class<C> configClass,
                                                      JsonNode json) {
            cfg = new NetconfDeviceConfig();
            ObjectMapper mapper = new ObjectMapper();
            cfg.init((DeviceId) subject, "netconf", mapper.createObjectNode(), mapper, null);
            cfg.setIp(json.get("ip").asText())
                    .setPort(json.get("port").asInt())
                    .setUsername(json.get("username").asText())
                    .setPassword(json.get("password").asText())
                    .setSshKey(json.get("sshkey").asText());
            provider.cfgListener.event(deviceAddedEventTranslated);
            return (C) cfg;
        }

        @Override
        public <S, C extends Config<S>> Set<S> getSubjects(Class<S> subjectClass, Class<C> configClass) {
            Set<S> subjects = new HashSet<>();
            if (available) {
                if (cfg != null) {
                    subjects.add((S) DeviceId.deviceId(NETCONF_DEVICE_ID_STRING_OLD));
                } else {
                    subjects.add((S) DeviceId.deviceId(NETCONF_DEVICE_ID_STRING));
                }
            }
            return subjects;
        }

    }

    private class MockNetconfProviderConfig extends NetconfProviderConfig {
        protected NetconfDeviceAddress deviceInfo =
                new NetconfDeviceAddress(IP_OLD, 1, TEST, TEST);

        @Override
        public Set<NetconfProviderConfig.NetconfDeviceAddress> getDevicesAddresses() throws ConfigException {
            return ImmutableSet.of(deviceInfo);
        }

    }

    private class MockDevice extends DefaultDevice {

        public MockDevice(ProviderId providerId, DeviceId id, Type type,
                          String manufacturer, String hwVersion, String swVersion,
                          String serialNumber, ChassisId chassisId, Annotations... annotations) {
            super(providerId, id, type, manufacturer, hwVersion, swVersion, serialNumber,
                  chassisId, annotations);
        }

        @Override
        protected Driver locateDriver() {
            return driver;
        }

        @Override
        public Driver driver() {
            return driver;
        }
    }

    private class MockDriver extends DriverAdapter {
        @Override
        public <T extends Behaviour> T createBehaviour(DriverHandler handler, Class<T> behaviourClass) {

            return (T) descriptionDiscovery;
        }
    }

    private class TestDescription extends AbstractHandlerBehaviour implements DeviceDescriptionDiscovery {

        List<PortDescription> portDescriptions = new ArrayList<>();

        @Override
        public DeviceDescription discoverDeviceDetails() {
            return null;
        }

        @Override
        public List<PortDescription> discoverPortDetails() {
            return portDescriptions;
        }

        private void addDeviceDetails() {

        }

        private void addPortDesc(PortDescription portDescription) {
            portDescriptions.add(portDescription);
        }
    }

    private class MockDelegate implements ConfigApplyDelegate {
        @Override
        public void onApply(Config configFile) {
        }
    }
}
