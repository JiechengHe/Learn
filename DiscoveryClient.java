//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package com.netflix.discovery;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.HealthCheckCallback;
import com.netflix.appinfo.HealthCheckCallbackToHandlerBridge;
import com.netflix.appinfo.HealthCheckHandler;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.ApplicationInfoManager.StatusChangeListener;
import com.netflix.appinfo.InstanceInfo.ActionType;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.AbstractDiscoveryClientOptionalArgs;
import com.netflix.discovery.AzToRegionMapper;
import com.netflix.discovery.BackupRegistry;
import com.netflix.discovery.CacheRefreshedEvent;
import com.netflix.discovery.DNSBasedAzToRegionMapper;
import com.netflix.discovery.DiscoveryManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.discovery.EurekaEvent;
import com.netflix.discovery.EurekaEventListener;
import com.netflix.discovery.InstanceInfoReplicator;
import com.netflix.discovery.InstanceRegionChecker;
import com.netflix.discovery.NotImplementedRegistryImpl;
import com.netflix.discovery.PreRegistrationHandler;
import com.netflix.discovery.PropertyBasedAzToRegionMapper;
import com.netflix.discovery.StatusChangeEvent;
import com.netflix.discovery.TimedSupervisorTask;
import com.netflix.discovery.endpoint.EndpointUtils;
import com.netflix.discovery.endpoint.EndpointUtils.DiscoveryUrlType;
import com.netflix.discovery.endpoint.EndpointUtils.InstanceInfoBasedUrlRandomizer;
import com.netflix.discovery.endpoint.EndpointUtils.ServiceUrlRandomizer;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.resolver.ClosableResolver;
import com.netflix.discovery.shared.resolver.aws.ApplicationsResolver.ApplicationsSource;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpClientFactory;
import com.netflix.discovery.shared.transport.EurekaHttpClients;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;
import com.netflix.discovery.shared.transport.EurekaTransportConfig;
import com.netflix.discovery.shared.transport.TransportClientFactory;
import com.netflix.discovery.shared.transport.jersey.EurekaJerseyClient;
import com.netflix.discovery.shared.transport.jersey.Jersey1DiscoveryClientOptionalArgs;
import com.netflix.discovery.shared.transport.jersey.Jersey1TransportClientFactories;
import com.netflix.discovery.shared.transport.jersey.TransportClientFactories;
import com.netflix.discovery.util.ThresholdLevelsMetric;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Monitors;
import com.netflix.servo.monitor.Stopwatch;
import com.netflix.servo.monitor.Timer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.core.Response.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
    netflix的DiscoveryClient
 */
@Singleton
public class DiscoveryClient implements EurekaClient {
    private static final Logger logger = LoggerFactory.getLogger(DiscoveryClient.class);
    public static final String HTTP_X_DISCOVERY_ALLOW_REDIRECT = "X-Discovery-AllowRedirect";
    private static final String VALUE_DELIMITER = ",";
    private static final String COMMA_STRING = ",";
    /** @deprecated */
    @Deprecated
    private static EurekaClientConfig staticClientConfig;
    private static final String PREFIX = "DiscoveryClient_";
    private final Counter RECONCILE_HASH_CODES_MISMATCH;
    private final Timer FETCH_REGISTRY_TIMER;
    private final Counter REREGISTER_COUNTER;
    private final ScheduledExecutorService scheduler;
    private final ThreadPoolExecutor heartbeatExecutor;
    private final ThreadPoolExecutor cacheRefreshExecutor;
    private final Provider<HealthCheckHandler> healthCheckHandlerProvider;
    private final Provider<HealthCheckCallback> healthCheckCallbackProvider;
    private final PreRegistrationHandler preRegistrationHandler;
    private final AtomicReference<Applications> localRegionApps;
    private final Lock fetchRegistryUpdateLock;
    private final AtomicLong fetchRegistryGeneration;
    private final ApplicationInfoManager applicationInfoManager;
    private final InstanceInfo instanceInfo;
    private final AtomicReference<String> remoteRegionsToFetch;
    private final AtomicReference<String[]> remoteRegionsRef;
    private final InstanceRegionChecker instanceRegionChecker;
    private final ServiceUrlRandomizer urlRandomizer;
    private final Provider<BackupRegistry> backupRegistryProvider;
    private final DiscoveryClient.EurekaTransport eurekaTransport;
    private volatile HealthCheckHandler healthCheckHandler;
    private volatile Map<String, Applications> remoteRegionVsApps;
    private volatile InstanceStatus lastRemoteInstanceStatus;
    private final CopyOnWriteArraySet<EurekaEventListener> eventListeners;
    private String appPathIdentifier;
    private StatusChangeListener statusChangeListener;
    private InstanceInfoReplicator instanceInfoReplicator;
    private volatile int registrySize;
    private volatile long lastSuccessfulRegistryFetchTimestamp;
    private volatile long lastSuccessfulHeartbeatTimestamp;
    private final ThresholdLevelsMetric heartbeatStalenessMonitor;
    private final ThresholdLevelsMetric registryStalenessMonitor;
    private final AtomicBoolean isShutdown;
    protected final EurekaClientConfig clientConfig;
    protected final EurekaTransportConfig transportConfig;
    private final long initTimestampMs;

    /** @deprecated */
    @Deprecated
    public DiscoveryClient(InstanceInfo myInfo, EurekaClientConfig config) {
        this((InstanceInfo)myInfo, config, (DiscoveryClient.DiscoveryClientOptionalArgs)null);
    }

    /** @deprecated */
    @Deprecated
    public DiscoveryClient(InstanceInfo myInfo, EurekaClientConfig config, DiscoveryClient.DiscoveryClientOptionalArgs args) {
        this(ApplicationInfoManager.getInstance(), config, args);
    }

    /** @deprecated */
    @Deprecated
    public DiscoveryClient(InstanceInfo myInfo, EurekaClientConfig config, AbstractDiscoveryClientOptionalArgs args) {
        this(ApplicationInfoManager.getInstance(), config, args);
    }

    public DiscoveryClient(ApplicationInfoManager applicationInfoManager, EurekaClientConfig config) {
        this((ApplicationInfoManager)applicationInfoManager, config, (DiscoveryClient.DiscoveryClientOptionalArgs)null);
    }

    /** @deprecated */
    @Deprecated
    public DiscoveryClient(ApplicationInfoManager applicationInfoManager, EurekaClientConfig config, DiscoveryClient.DiscoveryClientOptionalArgs args) {
        this((ApplicationInfoManager)applicationInfoManager, config, (AbstractDiscoveryClientOptionalArgs)args);
    }

    public DiscoveryClient(ApplicationInfoManager applicationInfoManager, final EurekaClientConfig config, AbstractDiscoveryClientOptionalArgs args) {
        this(applicationInfoManager, config, args, new Provider() {
            private volatile BackupRegistry backupRegistryInstance;

            public synchronized BackupRegistry get() {
                if(this.backupRegistryInstance == null) {
                    String backupRegistryClassName = config.getBackupRegistryImpl();
                    if(null != backupRegistryClassName) {
                        try {
                            this.backupRegistryInstance = (BackupRegistry)Class.forName(backupRegistryClassName).newInstance();
                            DiscoveryClient.logger.info("Enabled backup registry of type {}", this.backupRegistryInstance.getClass());
                        } catch (InstantiationException var3) {
                            DiscoveryClient.logger.error("Error instantiating BackupRegistry.", var3);
                        } catch (IllegalAccessException var4) {
                            DiscoveryClient.logger.error("Error instantiating BackupRegistry.", var4);
                        } catch (ClassNotFoundException var5) {
                            DiscoveryClient.logger.error("Error instantiating BackupRegistry.", var5);
                        }
                    }

                    if(this.backupRegistryInstance == null) {
                        DiscoveryClient.logger.warn("Using default backup registry implementation which does not do anything.");
                        this.backupRegistryInstance = new NotImplementedRegistryImpl();
                    }
                }

                return this.backupRegistryInstance;
            }
        });
    }

    @Inject
    DiscoveryClient(ApplicationInfoManager applicationInfoManager, EurekaClientConfig config, AbstractDiscoveryClientOptionalArgs args, Provider<BackupRegistry> backupRegistryProvider) {
        this.RECONCILE_HASH_CODES_MISMATCH = Monitors.newCounter("DiscoveryClient_ReconcileHashCodeMismatch");
        this.FETCH_REGISTRY_TIMER = Monitors.newTimer("DiscoveryClient_FetchRegistry");
        this.REREGISTER_COUNTER = Monitors.newCounter("DiscoveryClient_Reregister");
        this.localRegionApps = new AtomicReference();
        this.fetchRegistryUpdateLock = new ReentrantLock();
        this.remoteRegionVsApps = new ConcurrentHashMap();
        this.lastRemoteInstanceStatus = InstanceStatus.UNKNOWN;
        this.eventListeners = new CopyOnWriteArraySet();
        this.registrySize = 0;
        this.lastSuccessfulRegistryFetchTimestamp = -1L;
        this.lastSuccessfulHeartbeatTimestamp = -1L;
        this.isShutdown = new AtomicBoolean(false);
        if(args != null) {
            this.healthCheckHandlerProvider = args.healthCheckHandlerProvider;
            this.healthCheckCallbackProvider = args.healthCheckCallbackProvider;
            this.eventListeners.addAll(args.getEventListeners());
            this.preRegistrationHandler = args.preRegistrationHandler;
        } else {
            this.healthCheckCallbackProvider = null;
            this.healthCheckHandlerProvider = null;
            this.preRegistrationHandler = null;
        }

        this.applicationInfoManager = applicationInfoManager;
        InstanceInfo myInfo = applicationInfoManager.getInfo();
        this.clientConfig = config;
        staticClientConfig = this.clientConfig;
        this.transportConfig = config.getTransportConfig();
        this.instanceInfo = myInfo;
        if(myInfo != null) {
            this.appPathIdentifier = this.instanceInfo.getAppName() + "/" + this.instanceInfo.getId();
        } else {
            logger.warn("Setting instanceInfo to a passed in null value");
        }

        this.backupRegistryProvider = backupRegistryProvider;
        this.urlRandomizer = new InstanceInfoBasedUrlRandomizer(this.instanceInfo);
        this.localRegionApps.set(new Applications());
        this.fetchRegistryGeneration = new AtomicLong(0L);
        this.remoteRegionsToFetch = new AtomicReference(this.clientConfig.fetchRegistryForRemoteRegions());
        this.remoteRegionsRef = new AtomicReference(this.remoteRegionsToFetch.get() == null?null:((String)this.remoteRegionsToFetch.get()).split(","));
        if(config.shouldFetchRegistry()) {
            this.registryStalenessMonitor = new ThresholdLevelsMetric(this, "eurekaClient.registry.lastUpdateSec_", new long[]{15L, 30L, 60L, 120L, 240L, 480L});
        } else {
            this.registryStalenessMonitor = ThresholdLevelsMetric.NO_OP_METRIC;
        }

        if(config.shouldRegisterWithEureka()) {
            this.heartbeatStalenessMonitor = new ThresholdLevelsMetric(this, "eurekaClient.registration.lastHeartbeatSec_", new long[]{15L, 30L, 60L, 120L, 240L, 480L});
        } else {
            this.heartbeatStalenessMonitor = ThresholdLevelsMetric.NO_OP_METRIC;
        }

        logger.info("Initializing Eureka in region {}", this.clientConfig.getRegion());
        if(!config.shouldRegisterWithEureka() && !config.shouldFetchRegistry()) {
            logger.info("Client configured to neither register nor query for data.");
            this.scheduler = null;
            this.heartbeatExecutor = null;
            this.cacheRefreshExecutor = null;
            this.eurekaTransport = null;
            this.instanceRegionChecker = new InstanceRegionChecker(new PropertyBasedAzToRegionMapper(config), this.clientConfig.getRegion());
            DiscoveryManager.getInstance().setDiscoveryClient(this);
            DiscoveryManager.getInstance().setEurekaClientConfig(config);
            this.initTimestampMs = System.currentTimeMillis();
            logger.info("Discovery Client initialized at timestamp {} with initial instances count: {}", Long.valueOf(this.initTimestampMs), Integer.valueOf(this.getApplications().size()));
        } else {
            try {
                this.scheduler = Executors.newScheduledThreadPool(2, (new ThreadFactoryBuilder()).setNameFormat("DiscoveryClient-%d").setDaemon(true).build());
                // 发送心跳包的线程池
                this.heartbeatExecutor = new ThreadPoolExecutor(1, this.clientConfig.getHeartbeatExecutorThreadPoolSize(), 0L, TimeUnit.SECONDS, new SynchronousQueue(), (new ThreadFactoryBuilder()).setNameFormat("DiscoveryClient-HeartbeatExecutor-%d").setDaemon(true).build());
                this.cacheRefreshExecutor = new ThreadPoolExecutor(1, this.clientConfig.getCacheRefreshExecutorThreadPoolSize(), 0L, TimeUnit.SECONDS, new SynchronousQueue(), (new ThreadFactoryBuilder()).setNameFormat("DiscoveryClient-CacheRefreshExecutor-%d").setDaemon(true).build());
                // 与server进行http交互的工具
                this.eurekaTransport = new DiscoveryClient.EurekaTransport(null);
                this.scheduleServerEndpointTask(this.eurekaTransport, args);
                Object e;
                if(this.clientConfig.shouldUseDnsForFetchingServiceUrls()) {
                    e = new DNSBasedAzToRegionMapper(this.clientConfig);
                } else {
                    e = new PropertyBasedAzToRegionMapper(this.clientConfig);
                }

                if(null != this.remoteRegionsToFetch.get()) {
                    ((AzToRegionMapper)e).setRegionsToFetch(((String)this.remoteRegionsToFetch.get()).split(","));
                }

                this.instanceRegionChecker = new InstanceRegionChecker((AzToRegionMapper)e, this.clientConfig.getRegion());
            } catch (Throwable var9) {
                throw new RuntimeException("Failed to initialize DiscoveryClient!", var9);
            }

            if(this.clientConfig.shouldFetchRegistry() && !this.fetchRegistry(false)) {
                this.fetchRegistryFromBackup();
            }

            if(this.preRegistrationHandler != null) {
                this.preRegistrationHandler.beforeRegistration();
            }

            if(this.clientConfig.shouldRegisterWithEureka() && this.clientConfig.shouldEnforceRegistrationAtInit()) {
                try {
                    if(!this.register()) {
                        throw new IllegalStateException("Registration error at startup. Invalid server response.");
                    }
                } catch (Throwable var8) {
                    logger.error("Registration error at startup: {}", var8.getMessage());
                    throw new IllegalStateException(var8);
                }
            }

            this.initScheduledTasks();

            try {
                Monitors.registerObject(this);
            } catch (Throwable var7) {
                logger.warn("Cannot register timers", var7);
            }

            DiscoveryManager.getInstance().setDiscoveryClient(this);
            DiscoveryManager.getInstance().setEurekaClientConfig(config);
            this.initTimestampMs = System.currentTimeMillis();
            logger.info("Discovery Client initialized at timestamp {} with initial instances count: {}", Long.valueOf(this.initTimestampMs), Integer.valueOf(this.getApplications().size()));
        }
    }

    private void scheduleServerEndpointTask(DiscoveryClient.EurekaTransport eurekaTransport, AbstractDiscoveryClientOptionalArgs args) {
        Object additionalFilters = args == null?Collections.emptyList():args.additionalFilters;
        EurekaJerseyClient providedJerseyClient = args == null?null:args.eurekaJerseyClient;
        TransportClientFactories argsTransportClientFactories = null;
        if(args != null && args.getTransportClientFactories() != null) {
            argsTransportClientFactories = args.getTransportClientFactories();
        }

        Object transportClientFactories = argsTransportClientFactories == null?new Jersey1TransportClientFactories():argsTransportClientFactories;
        Optional sslContext = args == null?Optional.empty():args.getSSLContext();
        Optional hostnameVerifier = args == null?Optional.empty():args.getHostnameVerifier();
        eurekaTransport.transportClientFactory = providedJerseyClient == null?((TransportClientFactories)transportClientFactories).newTransportClientFactory(this.clientConfig, (Collection)additionalFilters, this.applicationInfoManager.getInfo(), sslContext, hostnameVerifier):((TransportClientFactories)transportClientFactories).newTransportClientFactory((Collection)additionalFilters, providedJerseyClient);
        ApplicationsSource applicationsSource = new ApplicationsSource() {
            public Applications getApplications(int stalenessThreshold, TimeUnit timeUnit) {
                long thresholdInMs = TimeUnit.MILLISECONDS.convert((long)stalenessThreshold, timeUnit);
                long delay = DiscoveryClient.this.getLastSuccessfulRegistryFetchTimePeriod();
                if(delay > thresholdInMs) {
                    DiscoveryClient.logger.info("Local registry is too stale for local lookup. Threshold:{}, actual:{}", Long.valueOf(thresholdInMs), Long.valueOf(delay));
                    return null;
                } else {
                    return (Applications)DiscoveryClient.this.localRegionApps.get();
                }
            }
        };
        eurekaTransport.bootstrapResolver = EurekaHttpClients.newBootstrapResolver(this.clientConfig, this.transportConfig, eurekaTransport.transportClientFactory, this.applicationInfoManager.getInfo(), applicationsSource);
        EurekaHttpClientFactory newQueryClientFactory;
        EurekaHttpClient newQueryClient;
        if(this.clientConfig.shouldRegisterWithEureka()) {
            newQueryClientFactory = null;
            newQueryClient = null;

            try {
                newQueryClientFactory = EurekaHttpClients.registrationClientFactory(eurekaTransport.bootstrapResolver, eurekaTransport.transportClientFactory, this.transportConfig);
                newQueryClient = newQueryClientFactory.newClient();
            } catch (Exception var14) {
                logger.warn("Transport initialization failure", var14);
            }

            eurekaTransport.registrationClientFactory = newQueryClientFactory;
            eurekaTransport.registrationClient = newQueryClient;
        }

        if(this.clientConfig.shouldFetchRegistry()) {
            newQueryClientFactory = null;
            newQueryClient = null;

            try {
                newQueryClientFactory = EurekaHttpClients.queryClientFactory(eurekaTransport.bootstrapResolver, eurekaTransport.transportClientFactory, this.clientConfig, this.transportConfig, this.applicationInfoManager.getInfo(), applicationsSource);
                newQueryClient = newQueryClientFactory.newClient();
            } catch (Exception var13) {
                logger.warn("Transport initialization failure", var13);
            }

            eurekaTransport.queryClientFactory = newQueryClientFactory;
            eurekaTransport.queryClient = newQueryClient;
        }

    }

    public EurekaClientConfig getEurekaClientConfig() {
        return this.clientConfig;
    }

    public ApplicationInfoManager getApplicationInfoManager() {
        return this.applicationInfoManager;
    }

    public Application getApplication(String appName) {
        return this.getApplications().getRegisteredApplications(appName);
    }

    public Applications getApplications() {
        return (Applications)this.localRegionApps.get();
    }

    public Applications getApplicationsForARegion(@Nullable String region) {
        return this.instanceRegionChecker.isLocalRegion(region)?(Applications)this.localRegionApps.get():(Applications)this.remoteRegionVsApps.get(region);
    }

    public Set<String> getAllKnownRegions() {
        String localRegion = this.instanceRegionChecker.getLocalRegion();
        if(!this.remoteRegionVsApps.isEmpty()) {
            Set regions = this.remoteRegionVsApps.keySet();
            HashSet toReturn = new HashSet(regions);
            toReturn.add(localRegion);
            return toReturn;
        } else {
            return Collections.singleton(localRegion);
        }
    }

    public List<InstanceInfo> getInstancesById(String id) {
        ArrayList instancesList = new ArrayList();
        Iterator var3 = this.getApplications().getRegisteredApplications().iterator();

        while(var3.hasNext()) {
            Application app = (Application)var3.next();
            InstanceInfo instanceInfo = app.getByInstanceId(id);
            if(instanceInfo != null) {
                instancesList.add(instanceInfo);
            }
        }

        return instancesList;
    }

    /** @deprecated */
    @Deprecated
    public void registerHealthCheckCallback(HealthCheckCallback callback) {
        if(this.instanceInfo == null) {
            logger.error("Cannot register a listener for instance info since it is null!");
        }

        if(callback != null) {
            this.healthCheckHandler = new HealthCheckCallbackToHandlerBridge(callback);
        }

    }

    public void registerHealthCheck(HealthCheckHandler healthCheckHandler) {
        if(this.instanceInfo == null) {
            logger.error("Cannot register a healthcheck handler when instance info is null!");
        }

        if(healthCheckHandler != null) {
            this.healthCheckHandler = healthCheckHandler;
            if(this.instanceInfoReplicator != null) {
                this.instanceInfoReplicator.onDemandUpdate();
            }
        }

    }

    public void registerEventListener(EurekaEventListener eventListener) {
        this.eventListeners.add(eventListener);
    }

    public boolean unregisterEventListener(EurekaEventListener eventListener) {
        return this.eventListeners.remove(eventListener);
    }

    public List<InstanceInfo> getInstancesByVipAddress(String vipAddress, boolean secure) {
        return this.getInstancesByVipAddress(vipAddress, secure, this.instanceRegionChecker.getLocalRegion());
    }

    public List<InstanceInfo> getInstancesByVipAddress(String vipAddress, boolean secure, @Nullable String region) {
        if(vipAddress == null) {
            throw new IllegalArgumentException("Supplied VIP Address cannot be null");
        } else {
            Applications applications;
            if(this.instanceRegionChecker.isLocalRegion(region)) {
                applications = (Applications)this.localRegionApps.get();
            } else {
                applications = (Applications)this.remoteRegionVsApps.get(region);
                if(null == applications) {
                    logger.debug("No applications are defined for region {}, so returning an empty instance list for vip address {}.", region, vipAddress);
                    return Collections.emptyList();
                }
            }

            return !secure?applications.getInstancesByVirtualHostName(vipAddress):applications.getInstancesBySecureVirtualHostName(vipAddress);
        }
    }

    public List<InstanceInfo> getInstancesByVipAddressAndAppName(String vipAddress, String appName, boolean secure) {
        Object result = new ArrayList();
        if(vipAddress == null && appName == null) {
            throw new IllegalArgumentException("Supplied VIP Address and application name cannot both be null");
        } else if(vipAddress != null && appName == null) {
            return this.getInstancesByVipAddress(vipAddress, secure);
        } else if(vipAddress == null && appName != null) {
            Application var15 = this.getApplication(appName);
            if(var15 != null) {
                result = var15.getInstances();
            }

            return (List)result;
        } else {
            Iterator var6 = this.getApplications().getRegisteredApplications().iterator();

            label67:
            while(var6.hasNext()) {
                Application app = (Application)var6.next();
                Iterator var8 = app.getInstances().iterator();

                while(true) {
                    while(true) {
                        String instanceVipAddress;
                        InstanceInfo instance;
                        do {
                            if(!var8.hasNext()) {
                                continue label67;
                            }

                            instance = (InstanceInfo)var8.next();
                            if(secure) {
                                instanceVipAddress = instance.getSecureVipAddress();
                            } else {
                                instanceVipAddress = instance.getVIPAddress();
                            }
                        } while(instanceVipAddress == null);

                        String[] instanceVipAddresses = instanceVipAddress.split(",");
                        String[] var11 = instanceVipAddresses;
                        int var12 = instanceVipAddresses.length;

                        for(int var13 = 0; var13 < var12; ++var13) {
                            String vipAddressFromList = var11[var13];
                            if(vipAddress.equalsIgnoreCase(vipAddressFromList.trim()) && appName.equalsIgnoreCase(instance.getAppName())) {
                                ((List)result).add(instance);
                                break;
                            }
                        }
                    }
                }
            }

            return (List)result;
        }
    }

    public InstanceInfo getNextServerFromEureka(String virtualHostname, boolean secure) {
        List instanceInfoList = this.getInstancesByVipAddress(virtualHostname, secure);
        if(instanceInfoList != null && !instanceInfoList.isEmpty()) {
            Applications apps = (Applications)this.localRegionApps.get();
            int index = (int)(apps.getNextIndex(virtualHostname, secure).incrementAndGet() % (long)instanceInfoList.size());
            return (InstanceInfo)instanceInfoList.get(index);
        } else {
            throw new RuntimeException("No matches for the virtual host name :" + virtualHostname);
        }
    }

    public Applications getApplications(String serviceUrl) {
        try {
            EurekaHttpResponse th = this.clientConfig.getRegistryRefreshSingleVipAddress() == null?this.eurekaTransport.queryClient.getApplications(new String[0]):this.eurekaTransport.queryClient.getVip(this.clientConfig.getRegistryRefreshSingleVipAddress(), new String[0]);
            if(th.getStatusCode() == 200) {
                logger.debug("DiscoveryClient_{} -  refresh status: {}", this.appPathIdentifier, Integer.valueOf(th.getStatusCode()));
                return (Applications)th.getEntity();
            }

            logger.error("DiscoveryClient_{} - was unable to refresh its cache! status = {}", this.appPathIdentifier, Integer.valueOf(th.getStatusCode()));
        } catch (Throwable var3) {
            logger.error("DiscoveryClient_{} - was unable to refresh its cache! status = {}", new Object[]{this.appPathIdentifier, var3.getMessage(), var3});
        }

        return null;
    }

    /*
        将当前的application实例注册到server，如果响应结果为204则表示成功。
     */
    boolean register() throws Throwable {
        logger.info("DiscoveryClient_{}: registering service...", this.appPathIdentifier);

        EurekaHttpResponse httpResponse;
        try {
            httpResponse = this.eurekaTransport.registrationClient.register(this.instanceInfo);
        } catch (Exception var3) {
            logger.warn("DiscoveryClient_{} - registration failed {}", new Object[]{this.appPathIdentifier, var3.getMessage(), var3});
            throw var3;
        }

        if(logger.isInfoEnabled()) {
            logger.info("DiscoveryClient_{} - registration status: {}", this.appPathIdentifier, Integer.valueOf(httpResponse.getStatusCode()));
        }

        return httpResponse.getStatusCode() == 204;
    }

    /*
        向服务端续约
     */
    boolean renew() {
        try {
            /*
                发送心跳请求
             */
            EurekaHttpResponse httpResponse = this.eurekaTransport.registrationClient.sendHeartBeat(this.instanceInfo.getAppName(), this.instanceInfo.getId(), this.instanceInfo, (InstanceStatus)null);
            logger.debug("DiscoveryClient_{} - Heartbeat status: {}", this.appPathIdentifier, Integer.valueOf(httpResponse.getStatusCode()));
            if(httpResponse.getStatusCode() == 404) {
                /*
                    返回404说明续约失败，需要重新注册
                 */
                this.REREGISTER_COUNTER.increment();
                logger.info("DiscoveryClient_{} - Re-registering apps/{}", this.appPathIdentifier, this.instanceInfo.getAppName());
                long e = this.instanceInfo.setIsDirtyWithTime();
                boolean success = this.register();
                if(success) {
                    this.instanceInfo.unsetIsDirty(e);
                }

                return success;
            } else {
                return httpResponse.getStatusCode() == 200;
            }
        } catch (Throwable var5) {
            logger.error("DiscoveryClient_{} - was unable to send heartbeat!", this.appPathIdentifier, var5);
            return false;
        }
    }

    /** @deprecated */
    @Deprecated
    public List<String> getServiceUrlsFromConfig(String instanceZone, boolean preferSameZone) {
        return EndpointUtils.getServiceUrlsFromConfig(this.clientConfig, instanceZone, preferSameZone);
    }

    @PreDestroy
    public synchronized void shutdown() {
        if(this.isShutdown.compareAndSet(false, true)) {
            logger.info("Shutting down DiscoveryClient ...");
            if(this.statusChangeListener != null && this.applicationInfoManager != null) {
                this.applicationInfoManager.unregisterStatusChangeListener(this.statusChangeListener.getId());
            }

            this.cancelScheduledTasks();
            if(this.applicationInfoManager != null && this.clientConfig.shouldRegisterWithEureka() && this.clientConfig.shouldUnregisterOnShutdown()) {
                this.applicationInfoManager.setInstanceStatus(InstanceStatus.DOWN);
                this.unregister();
            }

            if(this.eurekaTransport != null) {
                this.eurekaTransport.shutdown();
            }

            this.heartbeatStalenessMonitor.shutdown();
            this.registryStalenessMonitor.shutdown();
            logger.info("Completed shut down of DiscoveryClient");
        }

    }

    void unregister() {
        if(this.eurekaTransport != null && this.eurekaTransport.registrationClient != null) {
            try {
                logger.info("Unregistering ...");
                EurekaHttpResponse e = this.eurekaTransport.registrationClient.cancel(this.instanceInfo.getAppName(), this.instanceInfo.getId());
                logger.info("DiscoveryClient_{} - deregister  status: {}", this.appPathIdentifier, Integer.valueOf(e.getStatusCode()));
            } catch (Exception var2) {
                logger.error("DiscoveryClient_{} - de-registration failed{}", new Object[]{this.appPathIdentifier, var2.getMessage(), var2});
            }
        }

    }

    private boolean fetchRegistry(boolean forceFullRegistryFetch) {
        Stopwatch tracer = this.FETCH_REGISTRY_TIMER.start();

        label122: {
            boolean var4;
            try {
                Applications e = this.getApplications();
                if(!this.clientConfig.shouldDisableDelta() && Strings.isNullOrEmpty(this.clientConfig.getRegistryRefreshSingleVipAddress()) && !forceFullRegistryFetch && e != null && e.getRegisteredApplications().size() != 0 && e.getVersion().longValue() != -1L) {
                    this.getAndUpdateDelta(e);
                } else {
                    logger.info("Disable delta property : {}", Boolean.valueOf(this.clientConfig.shouldDisableDelta()));
                    logger.info("Single vip registry refresh property : {}", this.clientConfig.getRegistryRefreshSingleVipAddress());
                    logger.info("Force full registry fetch : {}", Boolean.valueOf(forceFullRegistryFetch));
                    logger.info("Application is null : {}", Boolean.valueOf(e == null));
                    logger.info("Registered Applications size is zero : {}", Boolean.valueOf(e.getRegisteredApplications().size() == 0));
                    logger.info("Application version is -1: {}", Boolean.valueOf(e.getVersion().longValue() == -1L));
                    this.getAndStoreFullRegistry();
                }

                e.setAppsHashCode(e.getReconcileHashCode());
                this.logTotalInstances();
                break label122;
            } catch (Throwable var8) {
                logger.error("DiscoveryClient_{} - was unable to refresh its cache! status = {}", new Object[]{this.appPathIdentifier, var8.getMessage(), var8});
                var4 = false;
            } finally {
                if(tracer != null) {
                    tracer.stop();
                }

            }

            return var4;
        }

        this.onCacheRefreshed();
        this.updateInstanceRemoteStatus();
        return true;
    }

    private synchronized void updateInstanceRemoteStatus() {
        InstanceStatus currentRemoteInstanceStatus = null;
        if(this.instanceInfo.getAppName() != null) {
            Application app = this.getApplication(this.instanceInfo.getAppName());
            if(app != null) {
                InstanceInfo remoteInstanceInfo = app.getByInstanceId(this.instanceInfo.getId());
                if(remoteInstanceInfo != null) {
                    currentRemoteInstanceStatus = remoteInstanceInfo.getStatus();
                }
            }
        }

        if(currentRemoteInstanceStatus == null) {
            currentRemoteInstanceStatus = InstanceStatus.UNKNOWN;
        }

        if(this.lastRemoteInstanceStatus != currentRemoteInstanceStatus) {
            this.onRemoteStatusChanged(this.lastRemoteInstanceStatus, currentRemoteInstanceStatus);
            this.lastRemoteInstanceStatus = currentRemoteInstanceStatus;
        }

    }

    public InstanceStatus getInstanceRemoteStatus() {
        return this.lastRemoteInstanceStatus;
    }

    private String getReconcileHashCode(Applications applications) {
        TreeMap instanceCountMap = new TreeMap();
        if(this.isFetchingRemoteRegionRegistries()) {
            Iterator var3 = this.remoteRegionVsApps.values().iterator();

            while(var3.hasNext()) {
                Applications remoteApp = (Applications)var3.next();
                remoteApp.populateInstanceCountMap(instanceCountMap);
            }
        }

        applications.populateInstanceCountMap(instanceCountMap);
        return Applications.getReconcileHashCode(instanceCountMap);
    }

    private void getAndStoreFullRegistry() throws Throwable {
        long currentUpdateGeneration = this.fetchRegistryGeneration.get();
        logger.info("Getting all instance registry info from the eureka server");
        Applications apps = null;
        EurekaHttpResponse httpResponse = this.clientConfig.getRegistryRefreshSingleVipAddress() == null?this.eurekaTransport.queryClient.getApplications((String[])this.remoteRegionsRef.get()):this.eurekaTransport.queryClient.getVip(this.clientConfig.getRegistryRefreshSingleVipAddress(), (String[])this.remoteRegionsRef.get());
        if(httpResponse.getStatusCode() == Status.OK.getStatusCode()) {
            apps = (Applications)httpResponse.getEntity();
        }

        logger.info("The response status is {}", Integer.valueOf(httpResponse.getStatusCode()));
        if(apps == null) {
            logger.error("The application is null for some reason. Not storing this information");
        } else if(this.fetchRegistryGeneration.compareAndSet(currentUpdateGeneration, currentUpdateGeneration + 1L)) {
            this.localRegionApps.set(this.filterAndShuffle(apps));
            logger.debug("Got full registry with apps hashcode {}", apps.getAppsHashCode());
        } else {
            logger.warn("Not updating applications as another thread is updating it already");
        }

    }

    private void getAndUpdateDelta(Applications applications) throws Throwable {
        long currentUpdateGeneration = this.fetchRegistryGeneration.get();
        Applications delta = null;
        EurekaHttpResponse httpResponse = this.eurekaTransport.queryClient.getDelta((String[])this.remoteRegionsRef.get());
        if(httpResponse.getStatusCode() == Status.OK.getStatusCode()) {
            delta = (Applications)httpResponse.getEntity();
        }

        if(delta == null) {
            logger.warn("The server does not allow the delta revision to be applied because it is not safe. Hence got the full registry.");
            this.getAndStoreFullRegistry();
        } else if(this.fetchRegistryGeneration.compareAndSet(currentUpdateGeneration, currentUpdateGeneration + 1L)) {
            logger.debug("Got delta update with apps hashcode {}", delta.getAppsHashCode());
            String reconcileHashCode = "";
            if(this.fetchRegistryUpdateLock.tryLock()) {
                try {
                    this.updateDelta(delta);
                    reconcileHashCode = this.getReconcileHashCode(applications);
                } finally {
                    this.fetchRegistryUpdateLock.unlock();
                }
            } else {
                logger.warn("Cannot acquire update lock, aborting getAndUpdateDelta");
            }

            if(!reconcileHashCode.equals(delta.getAppsHashCode()) || this.clientConfig.shouldLogDeltaDiff()) {
                this.reconcileAndLogDifference(delta, reconcileHashCode);
            }
        } else {
            logger.warn("Not updating application delta as another thread is updating it already");
            logger.debug("Ignoring delta update with apps hashcode {}, as another thread is updating it already", delta.getAppsHashCode());
        }

    }

    private void logTotalInstances() {
        if(logger.isDebugEnabled()) {
            int totInstances = 0;

            Application application;
            for(Iterator var2 = this.getApplications().getRegisteredApplications().iterator(); var2.hasNext(); totInstances += application.getInstancesAsIsFromEureka().size()) {
                application = (Application)var2.next();
            }

            logger.debug("The total number of all instances in the client now is {}", Integer.valueOf(totInstances));
        }

    }

    private void reconcileAndLogDifference(Applications delta, String reconcileHashCode) throws Throwable {
        logger.debug("The Reconcile hashcodes do not match, client : {}, server : {}. Getting the full registry", reconcileHashCode, delta.getAppsHashCode());
        this.RECONCILE_HASH_CODES_MISMATCH.increment();
        long currentUpdateGeneration = this.fetchRegistryGeneration.get();
        EurekaHttpResponse httpResponse = this.clientConfig.getRegistryRefreshSingleVipAddress() == null?this.eurekaTransport.queryClient.getApplications((String[])this.remoteRegionsRef.get()):this.eurekaTransport.queryClient.getVip(this.clientConfig.getRegistryRefreshSingleVipAddress(), (String[])this.remoteRegionsRef.get());
        Applications serverApps = (Applications)httpResponse.getEntity();
        if(serverApps == null) {
            logger.warn("Cannot fetch full registry from the server; reconciliation failure");
        } else {
            if(this.fetchRegistryGeneration.compareAndSet(currentUpdateGeneration, currentUpdateGeneration + 1L)) {
                this.localRegionApps.set(this.filterAndShuffle(serverApps));
                this.getApplications().setVersion(delta.getVersion());
                logger.debug("The Reconcile hashcodes after complete sync up, client : {}, server : {}.", this.getApplications().getReconcileHashCode(), delta.getAppsHashCode());
            } else {
                logger.warn("Not setting the applications map as another thread has advanced the update generation");
            }

        }
    }

    private void updateDelta(Applications delta) {
        int deltaCount = 0;
        Iterator var3 = delta.getRegisteredApplications().iterator();

        while(var3.hasNext()) {
            Application applications = (Application)var3.next();
            Iterator var5 = applications.getInstances().iterator();

            while(var5.hasNext()) {
                InstanceInfo instance = (InstanceInfo)var5.next();
                Applications applications1 = this.getApplications();
                String instanceRegion = this.instanceRegionChecker.getInstanceRegion(instance);
                if(!this.instanceRegionChecker.isLocalRegion(instanceRegion)) {
                    Applications existingApp = (Applications)this.remoteRegionVsApps.get(instanceRegion);
                    if(null == existingApp) {
                        existingApp = new Applications();
                        this.remoteRegionVsApps.put(instanceRegion, existingApp);
                    }

                    applications1 = existingApp;
                }

                ++deltaCount;
                Application var11;
                if(ActionType.ADDED.equals(instance.getActionType())) {
                    var11 = applications1.getRegisteredApplications(instance.getAppName());
                    if(var11 == null) {
                        applications1.addApplication(applications);
                    }

                    logger.debug("Added instance {} to the existing apps in region {}", instance.getId(), instanceRegion);
                    applications1.getRegisteredApplications(instance.getAppName()).addInstance(instance);
                } else if(ActionType.MODIFIED.equals(instance.getActionType())) {
                    var11 = applications1.getRegisteredApplications(instance.getAppName());
                    if(var11 == null) {
                        applications1.addApplication(applications);
                    }

                    logger.debug("Modified instance {} to the existing apps ", instance.getId());
                    applications1.getRegisteredApplications(instance.getAppName()).addInstance(instance);
                } else if(ActionType.DELETED.equals(instance.getActionType())) {
                    var11 = applications1.getRegisteredApplications(instance.getAppName());
                    if(var11 == null) {
                        applications1.addApplication(applications);
                    }

                    logger.debug("Deleted instance {} to the existing apps ", instance.getId());
                    applications1.getRegisteredApplications(instance.getAppName()).removeInstance(instance);
                }
            }
        }

        logger.debug("The total number of instances fetched by the delta processor : {}", Integer.valueOf(deltaCount));
        this.getApplications().setVersion(delta.getVersion());
        this.getApplications().shuffleInstances(this.clientConfig.shouldFilterOnlyUpInstances());
        var3 = this.remoteRegionVsApps.values().iterator();

        while(var3.hasNext()) {
            Applications var10 = (Applications)var3.next();
            var10.setVersion(delta.getVersion());
            var10.shuffleInstances(this.clientConfig.shouldFilterOnlyUpInstances());
        }

    }

    /*
        初始化定时任务
     */
    private void initScheduledTasks() {
        int renewalIntervalInSecs;
        int expBackOffBound;
        if(this.clientConfig.shouldFetchRegistry()) {
            renewalIntervalInSecs = this.clientConfig.getRegistryFetchIntervalSeconds();
            expBackOffBound = this.clientConfig.getCacheRefreshExecutorExponentialBackOffBound();
            this.scheduler.schedule(new TimedSupervisorTask("cacheRefresh", this.scheduler, this.cacheRefreshExecutor, renewalIntervalInSecs, TimeUnit.SECONDS, expBackOffBound, new DiscoveryClient.CacheRefreshThread()), (long)renewalIntervalInSecs, TimeUnit.SECONDS);
        }

        if(this.clientConfig.shouldRegisterWithEureka()) {
            renewalIntervalInSecs = this.instanceInfo.getLeaseInfo().getRenewalIntervalInSecs();
            expBackOffBound = this.clientConfig.getHeartbeatExecutorExponentialBackOffBound();
            logger.info("Starting heartbeat executor: renew interval is: {}", Integer.valueOf(renewalIntervalInSecs));
            this.scheduler.schedule(new TimedSupervisorTask("heartbeat", this.scheduler, this.heartbeatExecutor, renewalIntervalInSecs, TimeUnit.SECONDS, expBackOffBound, new DiscoveryClient.HeartbeatThread(null)), (long)renewalIntervalInSecs, TimeUnit.SECONDS);
            this.instanceInfoReplicator = new InstanceInfoReplicator(this, this.instanceInfo, this.clientConfig.getInstanceInfoReplicationIntervalSeconds(), 2);
            this.statusChangeListener = new StatusChangeListener() {
                public String getId() {
                    return "statusChangeListener";
                }

                public void notify(StatusChangeEvent statusChangeEvent) {
                    if(InstanceStatus.DOWN != statusChangeEvent.getStatus() && InstanceStatus.DOWN != statusChangeEvent.getPreviousStatus()) {
                        DiscoveryClient.logger.info("Saw local status change event {}", statusChangeEvent);
                    } else {
                        DiscoveryClient.logger.warn("Saw local status change event {}", statusChangeEvent);
                    }

                    DiscoveryClient.this.instanceInfoReplicator.onDemandUpdate();
                }
            };
            if(this.clientConfig.shouldOnDemandUpdateStatusChange()) {
                this.applicationInfoManager.registerStatusChangeListener(this.statusChangeListener);
            }

            this.instanceInfoReplicator.start(this.clientConfig.getInitialInstanceInfoReplicationIntervalSeconds());
        } else {
            logger.info("Not registering with Eureka server per configuration");
        }

    }

    private void cancelScheduledTasks() {
        if(this.instanceInfoReplicator != null) {
            this.instanceInfoReplicator.stop();
        }

        if(this.heartbeatExecutor != null) {
            this.heartbeatExecutor.shutdownNow();
        }

        if(this.cacheRefreshExecutor != null) {
            this.cacheRefreshExecutor.shutdownNow();
        }

        if(this.scheduler != null) {
            this.scheduler.shutdownNow();
        }

    }

    /** @deprecated */
    @Deprecated
    public List<String> getServiceUrlsFromDNS(String instanceZone, boolean preferSameZone) {
        return EndpointUtils.getServiceUrlsFromDNS(this.clientConfig, instanceZone, preferSameZone, this.urlRandomizer);
    }

    /** @deprecated */
    @Deprecated
    public List<String> getDiscoveryServiceUrls(String zone) {
        return EndpointUtils.getDiscoveryServiceUrls(this.clientConfig, zone, this.urlRandomizer);
    }

    /** @deprecated */
    @Deprecated
    public static Set<String> getEC2DiscoveryUrlsFromZone(String dnsName, DiscoveryUrlType type) {
        return EndpointUtils.getEC2DiscoveryUrlsFromZone(dnsName, type);
    }

    void refreshInstanceInfo() {
        this.applicationInfoManager.refreshDataCenterInfoIfRequired();
        this.applicationInfoManager.refreshLeaseInfoIfRequired();

        InstanceStatus status;
        try {
            status = this.getHealthCheckHandler().getStatus(this.instanceInfo.getStatus());
        } catch (Exception var3) {
            logger.warn("Exception from healthcheckHandler.getStatus, setting status to DOWN", var3);
            status = InstanceStatus.DOWN;
        }

        if(null != status) {
            this.applicationInfoManager.setInstanceStatus(status);
        }

    }

    @VisibleForTesting
    InstanceInfoReplicator getInstanceInfoReplicator() {
        return this.instanceInfoReplicator;
    }

    @VisibleForTesting
    InstanceInfo getInstanceInfo() {
        return this.instanceInfo;
    }

    public HealthCheckHandler getHealthCheckHandler() {
        if(this.healthCheckHandler == null) {
            if(null != this.healthCheckHandlerProvider) {
                this.healthCheckHandler = (HealthCheckHandler)this.healthCheckHandlerProvider.get();
            } else if(null != this.healthCheckCallbackProvider) {
                this.healthCheckHandler = new HealthCheckCallbackToHandlerBridge((HealthCheckCallback)this.healthCheckCallbackProvider.get());
            }

            if(null == this.healthCheckHandler) {
                this.healthCheckHandler = new HealthCheckCallbackToHandlerBridge((HealthCheckCallback)null);
            }
        }

        return this.healthCheckHandler;
    }

    @VisibleForTesting
    void refreshRegistry() {
        try {
            boolean e = this.isFetchingRemoteRegionRegistries();
            boolean remoteRegionsModified = false;
            String latestRemoteRegions = this.clientConfig.fetchRegistryForRemoteRegions();
            if(null != latestRemoteRegions) {
                String success = (String)this.remoteRegionsToFetch.get();
                if(!latestRemoteRegions.equals(success)) {
                    synchronized(this.instanceRegionChecker.getAzToRegionMapper()) {
                        if(this.remoteRegionsToFetch.compareAndSet(success, latestRemoteRegions)) {
                            String[] remoteRegions = latestRemoteRegions.split(",");
                            this.remoteRegionsRef.set(remoteRegions);
                            this.instanceRegionChecker.getAzToRegionMapper().setRegionsToFetch(remoteRegions);
                            remoteRegionsModified = true;
                        } else {
                            logger.info("Remote regions to fetch modified concurrently, ignoring change from {} to {}", success, latestRemoteRegions);
                        }
                    }
                } else {
                    this.instanceRegionChecker.getAzToRegionMapper().refreshMapping();
                }
            }

            boolean success1 = this.fetchRegistry(remoteRegionsModified);
            if(success1) {
                this.registrySize = ((Applications)this.localRegionApps.get()).size();
                this.lastSuccessfulRegistryFetchTimestamp = System.currentTimeMillis();
            }

            if(logger.isDebugEnabled()) {
                StringBuilder allAppsHashCodes = new StringBuilder();
                allAppsHashCodes.append("Local region apps hashcode: ");
                allAppsHashCodes.append(((Applications)this.localRegionApps.get()).getAppsHashCode());
                allAppsHashCodes.append(", is fetching remote regions? ");
                allAppsHashCodes.append(e);
                Iterator remoteRegions1 = this.remoteRegionVsApps.entrySet().iterator();

                while(remoteRegions1.hasNext()) {
                    Entry entry = (Entry)remoteRegions1.next();
                    allAppsHashCodes.append(", Remote region: ");
                    allAppsHashCodes.append((String)entry.getKey());
                    allAppsHashCodes.append(" , apps hashcode: ");
                    allAppsHashCodes.append(((Applications)entry.getValue()).getAppsHashCode());
                }

                logger.debug("Completed cache refresh task for discovery. All Apps hash code is {} ", allAppsHashCodes);
            }
        } catch (Throwable var9) {
            logger.error("Cannot fetch registry from server", var9);
        }

    }

    private void fetchRegistryFromBackup() {
        try {
            BackupRegistry e = this.newBackupRegistryInstance();
            if(null == e) {
                e = (BackupRegistry)this.backupRegistryProvider.get();
            }

            if(null != e) {
                Applications apps = null;
                if(this.isFetchingRemoteRegionRegistries()) {
                    String applications = (String)this.remoteRegionsToFetch.get();
                    if(null != applications) {
                        apps = e.fetchRegistry(applications.split(","));
                    }
                } else {
                    apps = e.fetchRegistry();
                }

                if(apps != null) {
                    Applications applications1 = this.filterAndShuffle(apps);
                    applications1.setAppsHashCode(applications1.getReconcileHashCode());
                    this.localRegionApps.set(applications1);
                    this.logTotalInstances();
                    logger.info("Fetched registry successfully from the backup");
                }
            } else {
                logger.warn("No backup registry instance defined & unable to find any discovery servers.");
            }
        } catch (Throwable var4) {
            logger.warn("Cannot fetch applications from apps although backup registry was specified", var4);
        }

    }

    /** @deprecated */
    @Deprecated
    @Nullable
    protected BackupRegistry newBackupRegistryInstance() throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        return null;
    }

    private Applications filterAndShuffle(Applications apps) {
        if(apps != null) {
            if(this.isFetchingRemoteRegionRegistries()) {
                ConcurrentHashMap remoteRegionVsApps = new ConcurrentHashMap();
                apps.shuffleAndIndexInstances(remoteRegionVsApps, this.clientConfig, this.instanceRegionChecker);
                Iterator var3 = remoteRegionVsApps.values().iterator();

                while(var3.hasNext()) {
                    Applications applications = (Applications)var3.next();
                    applications.shuffleInstances(this.clientConfig.shouldFilterOnlyUpInstances());
                }

                this.remoteRegionVsApps = remoteRegionVsApps;
            } else {
                apps.shuffleInstances(this.clientConfig.shouldFilterOnlyUpInstances());
            }
        }

        return apps;
    }

    private boolean isFetchingRemoteRegionRegistries() {
        return null != this.remoteRegionsToFetch.get();
    }

    protected void onRemoteStatusChanged(InstanceStatus oldStatus, InstanceStatus newStatus) {
        this.fireEvent(new StatusChangeEvent(oldStatus, newStatus));
    }

    protected void onCacheRefreshed() {
        this.fireEvent(new CacheRefreshedEvent());
    }

    protected void fireEvent(EurekaEvent event) {
        Iterator var2 = this.eventListeners.iterator();

        while(var2.hasNext()) {
            EurekaEventListener listener = (EurekaEventListener)var2.next();

            try {
                listener.onEvent(event);
            } catch (Exception var5) {
                logger.info("Event {} throw an exception for listener {}", new Object[]{event, listener, var5.getMessage()});
            }
        }

    }

    /** @deprecated */
    @Deprecated
    public static String getZone(InstanceInfo myInfo) {
        String[] availZones = staticClientConfig.getAvailabilityZones(staticClientConfig.getRegion());
        return InstanceInfo.getZone(availZones, myInfo);
    }

    /** @deprecated */
    @Deprecated
    public static String getRegion() {
        String region = staticClientConfig.getRegion();
        if(region == null) {
            region = "default";
        }

        region = region.trim().toLowerCase();
        return region;
    }

    /** @deprecated */
    @Deprecated
    public static List<String> getEurekaServiceUrlsFromConfig(String instanceZone, boolean preferSameZone) {
        return EndpointUtils.getServiceUrlsFromConfig(staticClientConfig, instanceZone, preferSameZone);
    }

    public long getLastSuccessfulHeartbeatTimePeriod() {
        return this.lastSuccessfulHeartbeatTimestamp < 0L?this.lastSuccessfulHeartbeatTimestamp:System.currentTimeMillis() - this.lastSuccessfulHeartbeatTimestamp;
    }

    public long getLastSuccessfulRegistryFetchTimePeriod() {
        return this.lastSuccessfulRegistryFetchTimestamp < 0L?this.lastSuccessfulRegistryFetchTimestamp:System.currentTimeMillis() - this.lastSuccessfulRegistryFetchTimestamp;
    }

    @Monitor(
        name = "eurekaClient.registration.lastSuccessfulHeartbeatTimePeriod",
        description = "How much time has passed from last successful heartbeat",
        type = DataSourceType.GAUGE
    )
    private long getLastSuccessfulHeartbeatTimePeriodInternal() {
        long delay = this.getLastSuccessfulHeartbeatTimePeriod();
        this.heartbeatStalenessMonitor.update(this.computeStalenessMonitorDelay(delay));
        return delay;
    }

    @Monitor(
        name = "eurekaClient.registry.lastSuccessfulRegistryFetchTimePeriod",
        description = "How much time has passed from last successful local registry update",
        type = DataSourceType.GAUGE
    )
    private long getLastSuccessfulRegistryFetchTimePeriodInternal() {
        long delay = this.getLastSuccessfulRegistryFetchTimePeriod();
        this.registryStalenessMonitor.update(this.computeStalenessMonitorDelay(delay));
        return delay;
    }

    @Monitor(
        name = "eurekaClient.registry.localRegistrySize",
        description = "Count of instances in the local registry",
        type = DataSourceType.GAUGE
    )
    public int localRegistrySize() {
        return this.registrySize;
    }

    private long computeStalenessMonitorDelay(long delay) {
        return delay < 0L?System.currentTimeMillis() - this.initTimestampMs:delay;
    }

    class CacheRefreshThread implements Runnable {
        CacheRefreshThread() {
        }

        public void run() {
            DiscoveryClient.this.refreshRegistry();
        }
    }

    private class HeartbeatThread implements Runnable {
        private HeartbeatThread() {
        }

        public void run() {
            if(DiscoveryClient.this.renew()) {
                DiscoveryClient.this.lastSuccessfulHeartbeatTimestamp = System.currentTimeMillis();
            }

        }
    }

    public static class DiscoveryClientOptionalArgs extends Jersey1DiscoveryClientOptionalArgs {
        public DiscoveryClientOptionalArgs() {
        }
    }

    private static final class EurekaTransport {
        private ClosableResolver bootstrapResolver;
        private TransportClientFactory transportClientFactory;
        private EurekaHttpClient registrationClient;
        private EurekaHttpClientFactory registrationClientFactory;
        private EurekaHttpClient queryClient;
        private EurekaHttpClientFactory queryClientFactory;

        private EurekaTransport() {
        }

        void shutdown() {
            if(this.registrationClientFactory != null) {
                this.registrationClientFactory.shutdown();
            }

            if(this.queryClientFactory != null) {
                this.queryClientFactory.shutdown();
            }

            if(this.registrationClient != null) {
                this.registrationClient.shutdown();
            }

            if(this.queryClient != null) {
                this.queryClient.shutdown();
            }

            if(this.transportClientFactory != null) {
                this.transportClientFactory.shutdown();
            }

            if(this.bootstrapResolver != null) {
                this.bootstrapResolver.shutdown();
            }

        }
    }
}
