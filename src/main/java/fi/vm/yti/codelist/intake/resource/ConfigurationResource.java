package fi.vm.yti.codelist.intake.resource;

import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import fi.vm.yti.codelist.intake.api.ApiUtils;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import static fi.vm.yti.codelist.common.constants.ApiConstants.*;

@Component
@Path("/v1/configuration")
@Api(value = "configuration", description = "Operations for fetching configuration values to frontend.")
@Produces("text/plain")
public class ConfigurationResource extends AbstractBaseResource {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationResource.class);
    private final ApiUtils apiUtils;

    @Inject
    public ConfigurationResource(final ApiUtils apiUtils) {
        this.apiUtils = apiUtils;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Get configuration values as JSON")
    @ApiResponse(code = 200, message = "Returns the configuration JSON element to the frontend related to this service.")
    public Response getConfig() throws IOException {
        logApiRequest(LOG, METHOD_GET, API_PATH_VERSION_V1, API_PATH_CONFIGURATION);
        final String groupManagementPublicUrl = apiUtils.getGroupmanagementPublicUrl();
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode configJson = mapper.createObjectNode();
        final ObjectNode groupManagementConfig = mapper.createObjectNode();
        groupManagementConfig.put("url", groupManagementPublicUrl);
        configJson.set("groupManagementConfig", groupManagementConfig);
        return Response.ok(configJson).build();
    }
}