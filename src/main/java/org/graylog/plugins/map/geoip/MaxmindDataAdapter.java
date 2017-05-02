package org.graylog.plugins.map.geoip;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.InetAddresses;
import com.google.inject.assistedinject.Assisted;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;

import org.graylog.autovalue.WithBeanGetter;
import org.graylog.plugins.map.config.DatabaseType;
import org.graylog2.plugin.lookup.LookupDataAdapter;
import org.graylog2.plugin.lookup.LookupDataAdapterConfiguration;
import org.graylog2.plugin.lookup.LookupResult;
import org.hibernate.validator.constraints.NotEmpty;
import org.slf4j.Logger;

import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import static org.slf4j.LoggerFactory.getLogger;

public class MaxmindDataAdapter extends LookupDataAdapter {

    private static final Logger LOG = getLogger(MaxmindDataAdapter.class);

    public static final String NAME = "maxmind_geoip";
    private final Config config;
    private DatabaseReader databaseReader;

    @Inject
    protected MaxmindDataAdapter(@Assisted LookupDataAdapterConfiguration config) {
        super(config);
        this.config = (Config) config;
    }

    @Override
    protected void doStart() throws Exception {
        Path path = Paths.get(config.path());
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException("Cannot read database file: " + config.path());
        }
        this.databaseReader = new DatabaseReader.Builder(path.toFile()).build();
    }

    @Override
    protected void doStop() throws Exception {
        databaseReader.close();
    }

    @Override
    protected LookupResult doGet(Object key) {
        InetAddress addr;
        if (key instanceof InetAddress) {
            addr = (InetAddress) key;
        } else {
            // try to convert it somehow
            try {
                addr = InetAddresses.forString(key.toString());
            } catch (IllegalArgumentException e) {
                LOG.warn("Unable to parse IP address, returning empty result.");
                return LookupResult.empty();
            }
        }
        switch (config.dbType()) {
            case MAXMIND_CITY:
                try {
                    final CityResponse city = databaseReader.city(addr);
                    final ImmutableMap.Builder<Object, Object> map = ImmutableMap.builder();
                    map.put("city", city.getCity());
                    map.put("continent", city.getContinent());
                    map.put("country", city.getCountry());
                    map.put("location", city.getLocation());
                    map.put("postal", city.getPostal());
                    map.put("registered_country", city.getRegisteredCountry());
                    map.put("represented_country", city.getRepresentedCountry());
                    map.put("subdivisions", city.getSubdivisions());
                    map.put("traits", city.getTraits());
                    return new LookupResult(map.build());
                } catch (AddressNotFoundException nfe) {
                    return LookupResult.empty();
                } catch (Exception e) {
                    LOG.warn("Unable too look up IP address, returning empty result.", e);
                    return LookupResult.empty();
                }
            case MAXMIND_COUNTRY:
                try {
                    final CountryResponse country = databaseReader.country(addr);
                    final ImmutableMap.Builder<Object, Object> map = ImmutableMap.builder();
                    map.put("continent", country.getContinent());
                    map.put("country", country.getCountry());
                    map.put("registered_country", country.getRegisteredCountry());
                    map.put("represented_country", country.getRepresentedCountry());
                    map.put("traits", country.getTraits());
                    return new LookupResult(map.build());
                } catch (AddressNotFoundException nfe) {
                    return LookupResult.empty();
                } catch (Exception e) {
                    LOG.warn("Unable too look up IP address, returning empty result.", e);
                    return LookupResult.empty();
                }
        }

        return LookupResult.empty();
    }

    @Override
    public void set(Object key, Object value) {
        throw new UnsupportedOperationException();
    }

    public interface Factory extends LookupDataAdapter.Factory<MaxmindDataAdapter> {
        @Override
        MaxmindDataAdapter create(LookupDataAdapterConfiguration configuration);

        @Override
        MaxmindDataAdapter.Descriptor getDescriptor();
    }

    public static class Descriptor extends LookupDataAdapter.Descriptor<MaxmindDataAdapter.Config> {
        public Descriptor() {
            super(NAME, MaxmindDataAdapter.Config.class);
        }

        @Override
        public MaxmindDataAdapter.Config defaultConfiguration() {
            return MaxmindDataAdapter.Config.builder()
                    .type(NAME)
                    .checkInterval(1)
                    .checkIntervalUnit(TimeUnit.MINUTES)
                    .path("/etc/graylog/server/GeoLite2-City.mmdb")
                    .dbType(DatabaseType.MAXMIND_CITY)
                    .build();
        }
    }

    @AutoValue
    @WithBeanGetter
    @JsonAutoDetect
    @JsonDeserialize(builder = AutoValue_MaxmindDataAdapter_Config.Builder.class)
    @JsonTypeName(NAME)
    public static abstract class Config implements LookupDataAdapterConfiguration {

        @Override
        @JsonProperty(TYPE_FIELD)
        public abstract String type();

        @JsonProperty("path")
        @NotEmpty
        public abstract String path();

        @JsonProperty("database_type")
        @NotNull
        public abstract DatabaseType dbType();

        @JsonProperty("check_interval")
        @Min(0)
        public abstract long checkInterval();

        @Nullable
        @JsonProperty("check_interval_unit")
        public abstract TimeUnit checkIntervalUnit();

        public static Config.Builder builder() {
            return new AutoValue_MaxmindDataAdapter_Config.Builder();
        }

        @AutoValue.Builder
        public abstract static class Builder {
            @JsonProperty(TYPE_FIELD)
            public abstract Config.Builder type(String type);

            @JsonProperty("path")
            public abstract Config.Builder path(String path);

            @JsonProperty("database_type")
            public abstract Builder dbType(DatabaseType dbType);

            @JsonProperty("check_interval")
            public abstract Builder checkInterval(long checkInterval);

            @JsonProperty("check_interval_unit")
            public abstract Builder checkIntervalUnit(@Nullable TimeUnit checkIntervalUnit);


            public abstract Config build();
        }
    }
}
