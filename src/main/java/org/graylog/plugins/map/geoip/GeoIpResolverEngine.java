/**
 * This file is part of Graylog.
 *
 * Graylog is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Graylog is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Graylog.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.graylog.plugins.map.geoip;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.collect.Lists;
import com.google.common.net.InetAddresses;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Location;
import org.graylog.plugins.map.config.GeoIpResolverConfig;
import org.graylog2.plugin.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static com.codahale.metrics.MetricRegistry.name;
import static com.google.common.base.Strings.isNullOrEmpty;

public class GeoIpResolverEngine {
	private static final Logger LOG = LoggerFactory.getLogger(GeoIpResolverEngine.class);

	private final Timer resolveTime;
	private DatabaseReader databaseReader;
	private boolean enabled;

	public GeoIpResolverEngine(GeoIpResolverConfig config, MetricRegistry metricRegistry) {
		this.resolveTime = metricRegistry.timer(name(GeoIpResolverEngine.class, "resolveTime"));

		try {
			final File database = new File(config.dbPath());
			if (Files.exists(database.toPath())) {
				this.databaseReader = new DatabaseReader.Builder(database).build();
				this.enabled = config.enabled();
			} else {
				LOG.warn("GeoIP database file does not exist: {}", config.dbPath());
				this.enabled = false;
			}
		} catch (IOException e) {
			LOG.error("Could not open GeoIP database {}", config.dbPath(), e);
			this.enabled = false;
		}
	}

	public boolean filter(Message message) {
		if (!enabled) {
			return false;
		}

		for (final Map.Entry<String, Object> field : message.getFields().entrySet()) {
			final CityResponse cityResponse = getCityResponse(field.getValue());
			if (cityResponse == null) {
				continue;
			}
			addCoordinatesField(message, field.getKey(), cityResponse);
			addCountryIsoCodeField(message, field.getKey(), cityResponse);
			addCityNameField(message, field.getKey(), cityResponse);
		}

		return false;
	}

	protected CityResponse getCityResponse(final Object fieldValue) {
		if (!(fieldValue instanceof String) || isNullOrEmpty((String) fieldValue)) {
			return null;
		}

		final String stringFieldValue = (String) fieldValue;
		final InetAddress ipAddress = this.getIpFromFieldValue(stringFieldValue);
		if (ipAddress == null) {
			return null;
		}

		try {
			try (Timer.Context ignored = resolveTime.time()) {
				return databaseReader.city(ipAddress);
			}
		} catch (Exception e) {
			LOG.debug("Could not get location from IP {}", ipAddress.getHostAddress(), e);
		}
		return null;
	}

	protected InetAddress getIpFromFieldValue(String fieldValue) {
		try {
			return InetAddresses.forString(fieldValue.trim());
		} catch (IllegalArgumentException e) {
			// Do nothing, field is not an IP
		}

		return null;
	}

	private void addCoordinatesField(Message message, final String fieldKey, CityResponse cityResponse) {
		final Location location = cityResponse.getLocation();
		final String key = fieldKey + "_geolocation";
		final String stringGeoPoint = location.getLatitude() + "," + location.getLongitude();
		message.addField(key, stringGeoPoint);
	}

	private void addCountryIsoCodeField(Message message, final String fieldKey, CityResponse cityResponse) {
		final String key = fieldKey + "_geocountryisocode";
		final String countryIsoCode = cityResponse.getRepresentedCountry().getIsoCode();
		message.addField(key, countryIsoCode);
	}

	private void addCityNameField(Message message, final String fieldKey, CityResponse cityResponse) {
		final String key = fieldKey + "_geocityname";
		final String cityName = cityResponse.getCity().getName();
		message.addField(key, cityName);
	}
}
