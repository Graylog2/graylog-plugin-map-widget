package org.graylog.plugins.map.rest;

import com.codahale.metrics.annotation.Timed;
import com.google.common.io.Resources;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.graylog.plugins.map.search.MapDataSearch;
import org.graylog.plugins.map.search.MapDataSearchRequest;
import org.graylog.plugins.map.search.MapDataSearchResult;
import org.graylog2.indexer.searches.Searches;
import org.graylog2.plugin.cluster.ClusterConfigService;
import org.graylog2.plugin.indexer.searches.timeranges.AbsoluteRange;
import org.graylog2.plugin.indexer.searches.timeranges.KeywordRange;
import org.graylog2.plugin.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.rest.PluginRestResource;
import org.graylog2.rest.resources.search.SearchResource;
import org.graylog2.shared.security.RestPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.io.IOException;

@Path("/")
@RequiresAuthentication
@Api(value = "MapWidget", description = "Get map data")
public class MapDataResource extends SearchResource implements PluginRestResource {
    private static final Logger LOG = LoggerFactory.getLogger(MapDataResource.class);

    private final MapDataSearch search;

    @Inject
    public MapDataResource(MapDataSearch search, Searches searches, ClusterConfigService clusterConfigService) {
        super(searches, clusterConfigService);
        this.search = search;
    }

    @POST
    @Path("/mapdata")
    @Timed
    @ApiOperation(value = "Get map data")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public MapDataSearchResult mapData(@ApiParam(name = "JSON body", required = true) MapDataSearchRequest request) {
        final String filter = "streams:" + request.streamId();

        switch (request.timerange().type()) {
            case AbsoluteRange.ABSOLUTE:
                checkSearchPermission(filter, RestPermissions.SEARCHES_ABSOLUTE);
                break;
            case RelativeRange.RELATIVE:
                checkSearchPermission(filter, RestPermissions.SEARCHES_RELATIVE);
                break;
            case KeywordRange.KEYWORD:
                checkSearchPermission(filter, RestPermissions.SEARCHES_KEYWORD);
                break;
        }

        try {
            return search.searchMapData(request);
        } catch (MapDataSearch.ValueTypeException e) {
            LOG.error("Map data query failed: {}", e.getMessage());
            throw new BadRequestException(e.getMessage());
        }
    }

    @GET
    @Path("/geojson")
    @Timed
    @ApiOperation(value = "Get GEOJSON map")
    @Produces(MediaType.APPLICATION_JSON)
    public byte[] geoJson() throws IOException {
        return Resources.asByteSource(Resources.getResource("geojson-map.json")).read();
    }
}
