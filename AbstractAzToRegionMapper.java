// 这个类是用于给定域抓取相应的服务列表

package com.netflix.discovery;

import com.google.common.base.Supplier;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractAzToRegionMapper implements AzToRegionMapper {
    private static final Logger logger = LoggerFactory.getLogger(InstanceRegionChecker.class);
    private static final String[] EMPTY_STR_ARRAY = new String[0];
    protected final EurekaClientConfig clientConfig;
    private final Multimap<String, String> defaultRegionVsAzMap = Multimaps.newListMultimap(new HashMap(), new Supplier<List<String>>() {
        public List<String> get() {
            return new ArrayList();
        }
    });
    private final Map<String, String> availabilityZoneVsRegion = new ConcurrentHashMap();
    private String[] regionsToFetch;

    protected AbstractAzToRegionMapper(EurekaClientConfig clientConfig) {
        this.clientConfig = clientConfig;
        this.populateDefaultAZToRegionMap();
    }

    public synchronized void setRegionsToFetch(String[] regionsToFetch) {
        if (null != regionsToFetch) {
            this.regionsToFetch = regionsToFetch;
            logger.info("Fetching availability zone to region mapping for regions {}", regionsToFetch);
            this.availabilityZoneVsRegion.clear();
            String[] var2 = regionsToFetch;
            int var3 = regionsToFetch.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                // 每一个远端region
                String remoteRegion = var2[var4];
                // 从远端获取相应的Zone
                Set<String> availabilityZones = this.getZonesForARegion(remoteRegion);
                if (null != availabilityZones && (availabilityZones.size() != 1 || !availabilityZones.contains("defaultZone")) && !availabilityZones.isEmpty()) {
                    // 至少有一个且不为zone
                    Iterator var11 = availabilityZones.iterator();

                    while(var11.hasNext()) {
                        String availabilityZone = (String)var11.next();
                        this.availabilityZoneVsRegion.put(availabilityZone, remoteRegion);
                    }
                } else {
                    // defaultZone
                    logger.info("No availability zone information available for remote region: {}. Now checking in the default mapping.", remoteRegion);
                    if (!this.defaultRegionVsAzMap.containsKey(remoteRegion)) {
                        String msg = "No availability zone information available for remote region: " + remoteRegion + ". This is required if registry information for this region is configured to be fetched.";
                        logger.error(msg);
                        throw new RuntimeException(msg);
                    }

                    Collection<String> defaultAvailabilityZones = this.defaultRegionVsAzMap.get(remoteRegion);
                    Iterator var8 = defaultAvailabilityZones.iterator();

                    while(var8.hasNext()) {
                        String defaultAvailabilityZone = (String)var8.next();
                        this.availabilityZoneVsRegion.put(defaultAvailabilityZone, remoteRegion);
                    }
                }
            }

            logger.info("Availability zone to region mapping for all remote regions: {}", this.availabilityZoneVsRegion);
        } else {
            logger.info("Regions to fetch is null. Erasing older mapping if any.");
            this.availabilityZoneVsRegion.clear();
            this.regionsToFetch = EMPTY_STR_ARRAY;
        }

    }

    // 不同的类不同的实现方法，均是从远端获取相应region的Zone
    protected abstract Set<String> getZonesForARegion(String var1);

    public String getRegionForAvailabilityZone(String availabilityZone) {
        String region = (String)this.availabilityZoneVsRegion.get(availabilityZone);
        return null == region ? this.parseAzToGetRegion(availabilityZone) : region;
    }

    public synchronized void refreshMapping() {
        logger.info("Refreshing availability zone to region mappings.");
        this.setRegionsToFetch(this.regionsToFetch);
    }

    protected String parseAzToGetRegion(String availabilityZone) {
        if (!availabilityZone.isEmpty()) {
            String possibleRegion = availabilityZone.substring(0, availabilityZone.length() - 1);
            if (this.availabilityZoneVsRegion.containsValue(possibleRegion)) {
                return possibleRegion;
            }
        }

        return null;
    }

    private void populateDefaultAZToRegionMap() {
        this.defaultRegionVsAzMap.put("us-east-1", "us-east-1a");
        this.defaultRegionVsAzMap.put("us-east-1", "us-east-1c");
        this.defaultRegionVsAzMap.put("us-east-1", "us-east-1d");
        this.defaultRegionVsAzMap.put("us-east-1", "us-east-1e");
        this.defaultRegionVsAzMap.put("us-west-1", "us-west-1a");
        this.defaultRegionVsAzMap.put("us-west-1", "us-west-1c");
        this.defaultRegionVsAzMap.put("us-west-2", "us-west-2a");
        this.defaultRegionVsAzMap.put("us-west-2", "us-west-2b");
        this.defaultRegionVsAzMap.put("us-west-2", "us-west-2c");
        this.defaultRegionVsAzMap.put("eu-west-1", "eu-west-1a");
        this.defaultRegionVsAzMap.put("eu-west-1", "eu-west-1b");
        this.defaultRegionVsAzMap.put("eu-west-1", "eu-west-1c");
    }
}
