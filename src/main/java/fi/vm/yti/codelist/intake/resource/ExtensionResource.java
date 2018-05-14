package fi.vm.yti.codelist.intake.resource;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.jaxrs.cfg.ObjectWriterInjector;

import fi.vm.yti.codelist.common.dto.ExtensionDTO;
import fi.vm.yti.codelist.intake.api.ResponseWrapper;
import fi.vm.yti.codelist.intake.indexing.Indexing;
import fi.vm.yti.codelist.intake.model.Meta;
import fi.vm.yti.codelist.intake.service.ExtensionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import static fi.vm.yti.codelist.common.constants.ApiConstants.FILTER_NAME_EXTENSION;
import static fi.vm.yti.codelist.common.constants.ApiConstants.FILTER_NAME_EXTENSIONSCHEME;

@Component
@Path("/v1/extensions")
@Api(value = "extensions")
@Produces(MediaType.APPLICATION_JSON)
public class ExtensionResource extends AbstractBaseResource {

    private final Indexing indexing;
    private final ExtensionService extensionService;

    @Inject
    public ExtensionResource(final Indexing indexing,
                             final ExtensionService extensionService) {
        this.indexing = indexing;
        this.extensionService = extensionService;
    }

    @POST
    @Path("{extensionId}/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Parses and creates or updates Extensions from JSON input.")
    @ApiResponses(value = {
        @ApiResponse(code = 200, message = "Returns success.")
    })
    public Response addOrUpdateExtensionsFromJson(@ApiParam(value = "Extension UUID", required = true) @PathParam("extensionId") final UUID extensionId,
                                                  @ApiParam(value = "JSON playload for Extension data.", required = true) final String jsonPayload) {
        return parseAndPersistExtensionFromSource(jsonPayload);
    }

    private Response parseAndPersistExtensionFromSource(final String jsonPayload) {
        final ExtensionDTO extension = extensionService.parseAndPersistExtensionFromJson(jsonPayload);
        final Set<ExtensionDTO> extensions = new HashSet<>();
        extensions.add(extension);
        indexing.updateExtensions(extensions);
        final Meta meta = new Meta();
        ObjectWriterInjector.set(new AbstractBaseResource.FilterModifier(createSimpleFilterProvider(FILTER_NAME_EXTENSION, "extension")));
        final ResponseWrapper<ExtensionDTO> responseWrapper = new ResponseWrapper<>(meta);
        meta.setMessage("Extension added or modified.");
        meta.setCode(200);
        responseWrapper.setResults(extensions);
        return Response.ok(responseWrapper).build();
    }
}
