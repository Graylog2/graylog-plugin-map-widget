package org.graylog.plugins.map.geoip;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableMap;
import com.maxmind.geoip2.DatabaseReader;
import org.graylog.plugins.map.config.DatabaseType;
import org.graylog2.plugin.lookup.LookupResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        MaxmindDataAdapterTest.CityDatabaseTest.class,
        MaxmindDataAdapterTest.CountryDatabaseTest.class
})
public class MaxmindDataAdapterTest {
    private static final Map<DatabaseType, String> DB_PATH = ImmutableMap.of(
            DatabaseType.MAXMIND_CITY, "/GeoLite2-City.mmdb",
            DatabaseType.MAXMIND_COUNTRY, "/GeoLite2-Country.mmdb"
    );

    abstract static class Base {
        MaxmindDataAdapter adapter;
        private final DatabaseType databaseType;

        Base(DatabaseType databaseType) {
            this.databaseType = databaseType;
        }

        @Before
        public void setUp() throws Exception {
            final MaxmindDataAdapter.Config config = MaxmindDataAdapter.Config.builder()
                    .checkInterval(1L)
                    .checkIntervalUnit(TimeUnit.HOURS)
                    .dbType(databaseType)
                    .path(getTestDatabasePath(DB_PATH.get(databaseType)))
                    .type("test")
                    .build();
            adapter = new MaxmindDataAdapter("test", "test", config, new MetricRegistry());

            adapter.doStart();
        }

        @After
        public void tearDown() throws Exception {
            adapter.doStop();
        }

        private String getTestDatabasePath(String name) throws URISyntaxException {
            return this.getClass().getResource(name).toURI().getPath();
        }

        @Test
        public void doGetSuccessfullyResolvesLoopBackToEmptyResult() {
            final LookupResult lookupResult = adapter.doGet("127.0.0.1");
            assertThat(lookupResult.isEmpty()).isTrue();
        }

        @Test
        public void doGetSuccessfullyResolvesRFC1918AddressToEmptyResult() {
            final LookupResult lookupResult = adapter.doGet("192.168.23.42");
            assertThat(lookupResult.isEmpty()).isTrue();
        }

        @Test
        public void doGetReturnsEmptyResultIfDatabaseReaderReturnsNull() throws Exception {
            final DatabaseReader mockDatabaseReader = mock(DatabaseReader.class);
            when(mockDatabaseReader.city(any())).thenReturn(null);
            final DatabaseReader oldDatabaseReader = adapter.getDatabaseReader();

            try {
                adapter.setDatabaseReader(mockDatabaseReader);

                final LookupResult lookupResult = adapter.doGet("127.0.0.1");
                assertThat(lookupResult.isEmpty()).isTrue();
            } finally {
                adapter.setDatabaseReader(oldDatabaseReader);
            }
        }

        @Test
        public void doGetReturnsEmptyResultForInvalidIPAddress() {
            final LookupResult lookupResult = adapter.doGet("Foobar");
            assertThat(lookupResult.isEmpty()).isTrue();
        }
    }

    public static class CityDatabaseTest extends Base {
        public CityDatabaseTest() {
            super(DatabaseType.MAXMIND_CITY);
        }

        @Test
        public void doGetSuccessfullyResolvesGooglePublicDNSAddress() {
            // This test will possibly get flaky when the entry for 8.8.8.8 changes!
            final LookupResult lookupResult = adapter.doGet("8.8.8.8");
            assertThat(lookupResult.isEmpty()).isFalse();
            assertThat(lookupResult.multiValue())
                    .extracting("city")
                    .extracting("geoNameId")
                    .containsExactly(5375480);
            assertThat(lookupResult.multiValue())
                    .extracting("location")
                    .extracting("metroCode")
                    .containsExactly(807);
        }
    }

    public static class CountryDatabaseTest extends Base {
        public CountryDatabaseTest() {
            super(DatabaseType.MAXMIND_COUNTRY);
        }

        @Test
        public void doGetSuccessfullyResolvesGooglePublicDNSAddress() {
            // This test will possibly get flaky when the entry for 8.8.8.8 changes!
            final LookupResult lookupResult = adapter.doGet("8.8.8.8");
            assertThat(lookupResult.isEmpty()).isFalse();
            assertThat(lookupResult.multiValue())
                    .extracting("country")
                    .extracting("geoNameId")
                    .containsExactly(6252001);
        }
    }
}