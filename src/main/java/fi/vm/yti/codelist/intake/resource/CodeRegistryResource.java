package fi.vm.yti.codelist.intake.resource;

import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.transaction.Transactional;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.cfg.ObjectWriterInjector;

import fi.vm.yti.codelist.common.model.Code;
import fi.vm.yti.codelist.common.model.CodeRegistry;
import fi.vm.yti.codelist.common.model.CodeScheme;
import fi.vm.yti.codelist.common.model.ExternalReference;
import fi.vm.yti.codelist.common.model.Meta;
import fi.vm.yti.codelist.common.model.Status;
import fi.vm.yti.codelist.intake.api.ApiUtils;
import fi.vm.yti.codelist.intake.api.MetaResponseWrapper;
import fi.vm.yti.codelist.intake.api.ResponseWrapper;
import fi.vm.yti.codelist.intake.domain.Domain;
import fi.vm.yti.codelist.intake.indexing.Indexing;
import fi.vm.yti.codelist.intake.jpa.CodeRegistryRepository;
import fi.vm.yti.codelist.intake.jpa.CodeRepository;
import fi.vm.yti.codelist.intake.jpa.CodeSchemeRepository;
import fi.vm.yti.codelist.intake.jpa.ExternalReferenceRepository;
import fi.vm.yti.codelist.intake.parser.CodeParser;
import fi.vm.yti.codelist.intake.parser.CodeRegistryParser;
import fi.vm.yti.codelist.intake.parser.CodeSchemeParser;
import fi.vm.yti.codelist.intake.security.AuthorizationManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import static fi.vm.yti.codelist.common.constants.ApiConstants.API_PATH_CODEREGISTRIES;
import static fi.vm.yti.codelist.common.constants.ApiConstants.API_PATH_CODES;
import static fi.vm.yti.codelist.common.constants.ApiConstants.API_PATH_CODESCHEMES;
import static fi.vm.yti.codelist.common.constants.ApiConstants.API_PATH_EXTERNALREFERENCES;
import static fi.vm.yti.codelist.common.constants.ApiConstants.API_PATH_VERSION_V1;
import static fi.vm.yti.codelist.common.constants.ApiConstants.EXCEL_SHEET_CODESCHEMES;
import static fi.vm.yti.codelist.common.constants.ApiConstants.FILTER_NAME_CODE;
import static fi.vm.yti.codelist.common.constants.ApiConstants.FILTER_NAME_CODEREGISTRY;
import static fi.vm.yti.codelist.common.constants.ApiConstants.FILTER_NAME_CODESCHEME;
import static fi.vm.yti.codelist.common.constants.ApiConstants.FORMAT_CSV;
import static fi.vm.yti.codelist.common.constants.ApiConstants.FORMAT_EXCEL;
import static fi.vm.yti.codelist.common.constants.ApiConstants.FORMAT_JSON;
import static fi.vm.yti.codelist.common.constants.ApiConstants.METHOD_DELETE;
import static fi.vm.yti.codelist.common.constants.ApiConstants.METHOD_POST;

@Component
@Path("/v1/coderegistries")
@Api(value = "coderegistries", description = "Operations for creating, deleting and updating coderegistries, codeschemes and codes.")
@Produces("text/plain")
public class CodeRegistryResource extends AbstractBaseResource {

    private static final Logger LOG = LoggerFactory.getLogger(CodeRegistryResource.class);
    private final Domain domain;
    private final Indexing indexing;
    private final ApiUtils apiUtils;
    private final CodeRegistryParser codeRegistryParser;
    private final CodeRegistryRepository codeRegistryRepository;
    private final CodeSchemeParser codeSchemeParser;
    private final CodeSchemeRepository codeSchemeRepository;
    private final CodeParser codeParser;
    private final CodeRepository codeRepository;
    private final ExternalReferenceRepository externalReferenceRepository;
    private final AuthorizationManager authorizationManager;

    @Inject
    public CodeRegistryResource(final Domain domain,
                                final Indexing indexing,
                                final ApiUtils apiUtils,
                                final CodeRegistryParser codeRegistryParser,
                                final CodeRegistryRepository codeRegistryRepository,
                                final CodeSchemeParser codeSchemeParser,
                                final CodeSchemeRepository codeSchemeRepository,
                                final CodeParser codeParser,
                                final CodeRepository codeRepository,
                                final ExternalReferenceRepository externalReferenceRepository,
                                final AuthorizationManager authorizationManager) {
        this.domain = domain;
        this.indexing = indexing;
        this.apiUtils = apiUtils;
        this.codeRegistryParser = codeRegistryParser;
        this.codeRegistryRepository = codeRegistryRepository;
        this.codeSchemeParser = codeSchemeParser;
        this.codeSchemeRepository = codeSchemeRepository;
        this.codeParser = codeParser;
        this.codeRepository = codeRepository;
        this.externalReferenceRepository = externalReferenceRepository;
        this.authorizationManager = authorizationManager;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Parses CodeRegistries from JSON input.")
    @ApiResponse(code = 200, message = "Returns success.")
    @Transactional
    public Response addOrUpdateCodeRegistriesFromJson(@ApiParam(value = "JSON playload for CodeRegistry data.", required = true) final String jsonPayload) {
        logApiRequest(LOG, METHOD_POST, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES);
        ObjectWriterInjector.set(new AbstractBaseResource.FilterModifier(createSimpleFilterProvider(FILTER_NAME_CODEREGISTRY, null)));
        final Meta meta = new Meta();
        final ResponseWrapper<CodeRegistry> wrapper = new ResponseWrapper<>(meta);
        if (!authorizationManager.isSuperUser()) {
            return handleUnauthorizedAccess(meta, wrapper,
                    "Superuser rights are needed to addOrUpdateCodeRegistriesFromJson.");
        }
        final ObjectMapper mapper = createObjectMapper();
        try {
            Set<CodeRegistry> codeRegistries = new HashSet<>();
            if (jsonPayload != null && !jsonPayload.isEmpty()) {
                codeRegistries = mapper.readValue(jsonPayload, new TypeReference<Set<CodeRegistry>>() {
                });
                for (final CodeRegistry codeRegistry : codeRegistries) {
                    if (codeRegistry.getId() == null) {
                        codeRegistry.setId(UUID.randomUUID());
                        codeRegistry.setUri(apiUtils.createCodeRegistryUri(codeRegistry));
                        codeRegistry.setModified(new Date(System.currentTimeMillis()));
                    }
                }
            }
            for (final CodeRegistry register : codeRegistries) {
                LOG.debug("CodeRegistry parsed from input: " + register.getCodeValue());
            }
            if (!codeRegistries.isEmpty()) {
                domain.persistCodeRegistries(codeRegistries);
                // TODO only reindex relevant data.
                indexing.reIndexEverything();
            }
            meta.setMessage("CodeRegistries added or modified: " + codeRegistries.size());
            meta.setCode(200);
            wrapper.setResults(codeRegistries);
            return Response.ok(wrapper).build();
        } catch (Exception e) {
            return handleInternalServerError(meta, wrapper, "Error parsing CodeRegistries.", e);
        }
    }



    @POST
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Parses CodeRegistries from input data.")
    @ApiResponse(code = 200, message = "Returns success.")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "file", value = "Input-file", required = false, dataType = "file", paramType = "formData")
    })
    @Transactional
    public Response addOrUpdateCodeRegistriesFromFile(@ApiParam(value = "Format for input.", required = true) @QueryParam("format") @DefaultValue("json") final String format,
                                                      @ApiParam(value = "Input-file for CSV or Excel import.", required = false, hidden = true, type = "file") @FormDataParam("file") final InputStream inputStream) {
        logApiRequest(LOG, METHOD_POST, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES);
        ObjectWriterInjector.set(new AbstractBaseResource.FilterModifier(createSimpleFilterProvider(FILTER_NAME_CODEREGISTRY, null)));
        final Meta meta = new Meta();
        final ResponseWrapper<CodeRegistry> wrapper = new ResponseWrapper<>(meta);
        if (!authorizationManager.isSuperUser()) {
            return handleUnauthorizedAccess(meta, wrapper,
                    "Superuser rights are needed to addOrUpdateCodeRegistriesFromFile.");
        }
        try {
            Set<CodeRegistry> codeRegistries = new HashSet<>();
            if (FORMAT_CSV.equalsIgnoreCase(format)) {
                codeRegistries = codeRegistryParser.parseCodeRegistriesFromCsvInputStream(inputStream);
            } else if (FORMAT_EXCEL.equalsIgnoreCase(format)) {
                codeRegistries = codeRegistryParser.parseCodeRegistriesFromExcelInputStream(inputStream);
            }
            for (final CodeRegistry register : codeRegistries) {
                LOG.debug("CodeRegistry parsed from input: " + register.getCodeValue());
            }
            if (!codeRegistries.isEmpty()) {
                domain.persistCodeRegistries(codeRegistries);
                // TODO only reindex relevant data.
                indexing.reIndexEverything();
            }
            meta.setMessage("CodeRegistries added or modified: " + codeRegistries.size());
            meta.setCode(200);
            wrapper.setResults(codeRegistries);
            return Response.ok(wrapper).build();
        } catch (Exception e) {
            return handleInternalServerError(meta, wrapper, "Error parsing CodeRegistries.", e);
        }
    }

    @POST
    @Path("{codeRegistryCodeValue}/codeschemes/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Parses CodeSchemes from JSON input.")
    @ApiResponse(code = 200, message = "Returns success.")
    @Transactional
    public Response addOrUpdateCodeSchemesFromJson(@ApiParam(value = "Format for input.", required = false) @QueryParam("format") @DefaultValue("json") final String format,
                                                   @ApiParam(value = "CodeRegistry codeValue", required = true) @PathParam("codeRegistryCodeValue") final String codeRegistryCodeValue,
                                                   @ApiParam(value = "JSON playload for CodeScheme data.", required = true) final String jsonPayload) {
        logApiRequest(LOG, METHOD_POST, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES + "/" + codeRegistryCodeValue + API_PATH_CODESCHEMES + "/");
        final Meta meta = new Meta();
        final ResponseWrapper<CodeScheme> responseWrapper = new ResponseWrapper<>(meta);
        ObjectWriterInjector.set(new FilterModifier(createSimpleFilterProvider(FILTER_NAME_CODESCHEME, "codeRegistry,code")));
        final CodeRegistry codeRegistry = codeRegistryRepository.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                return handleUnauthorizedAccess(meta, responseWrapper,
                        "Unauthorized call to addOrUpdateCodeSchemesFromJson.");
            }
            Set<CodeScheme> codeSchemes = new HashSet<>();
            try {
                if (FORMAT_JSON.equalsIgnoreCase(format) && jsonPayload != null && !jsonPayload.isEmpty()) {
                    final ObjectMapper mapper = createObjectMapper();
                    codeSchemes = mapper.readValue(jsonPayload, new TypeReference<Set<CodeScheme>>() {
                    });
                    for (final CodeScheme codeScheme : codeSchemes) {
                        if (!startDateIsBeforeEndDateSanityCheck(codeScheme.getStartDate(), codeScheme.getEndDate())) {
                           return handleStartDateLaterThanEndDate(responseWrapper);
                        }
                        if (codeScheme.getId() == null) {
                            codeScheme.setId(UUID.randomUUID());
                            codeScheme.setUri(apiUtils.createCodeSchemeUri(codeRegistry, codeScheme));
                        }
                        codeScheme.setCodeRegistry(codeRegistry);
                        final Set<ExternalReference> externalReferences = initializeExternalReferences(codeScheme.getExternalReferences(), codeScheme);
                        if (!externalReferences.isEmpty()) {
                            externalReferenceRepository.save(externalReferences);
                            codeScheme.setExternalReferences(null);
                            // This intermediate saving is necessary to avoid hibernate insertion issues with primary key constraint
                            codeSchemeRepository.save(codeScheme);
                            codeScheme.setExternalReferences(externalReferences);
                        } else {
                            codeScheme.setExternalReferences(null);
                        }
                        codeScheme.setModified(new Date(System.currentTimeMillis()));
                    }
                }
            } catch (final Exception e) {
                return handleInternalServerError(meta, responseWrapper, "Internal server error during call to addOrUpdateCodeSchemesFromJson.", e);
            }
            for (final CodeScheme codeScheme : codeSchemes) {
                LOG.debug("CodeScheme parsed from input: " + codeScheme.getCodeValue());
            }
            if (!codeSchemes.isEmpty()) {
                domain.persistCodeSchemes(codeSchemes);
                indexing.updateCodeSchemes(codeSchemes);
                for (final CodeScheme codeScheme : codeSchemes) {
                    indexing.updateCodes(codeRepository.findByCodeScheme(codeScheme));
                    indexing.updateExternalReferences(externalReferenceRepository.findByParentCodeScheme(codeScheme));
                }
            }
            meta.setMessage("CodeSchemes added or modified: " + codeSchemes.size());
            meta.setCode(200);
            responseWrapper.setResults(codeSchemes);
            return Response.ok(responseWrapper).build();
        }
        meta.setMessage("CodeScheme with code: " + codeRegistryCodeValue + " does not exist yet, please creater register first.");
        meta.setCode(404);
        return Response.status(Response.Status.NOT_ACCEPTABLE).entity(responseWrapper).build();
    }

    @POST
    @Path("{codeRegistryCodeValue}/codeschemes/")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Parses CodeSchemes from input data.")
    @ApiResponse(code = 200, message = "Returns success.")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "file", value = "Input-file", required = false, dataType = "file", paramType = "formData")
    })
    @Transactional
    public Response addOrUpdateCodeSchemesFromFile(@ApiParam(value = "Format for input.", required = false) @QueryParam("format") @DefaultValue("csv") final String format,
                                                   @ApiParam(value = "CodeRegistry codeValue", required = true) @PathParam("codeRegistryCodeValue") final String codeRegistryCodeValue,
                                                   @ApiParam(value = "Input-file for CSV or Excel import.", required = false, hidden = true, type = "file") @FormDataParam("file") final InputStream inputStream) {
        logApiRequest(LOG, METHOD_POST, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES + "/" + codeRegistryCodeValue + API_PATH_CODESCHEMES + "/");
        final Meta meta = new Meta();
        final ResponseWrapper<CodeScheme> responseWrapper = new ResponseWrapper<>(meta);
        ObjectWriterInjector.set(new AbstractBaseResource.FilterModifier(createSimpleFilterProvider(FILTER_NAME_CODESCHEME, "codeRegistry")));
        final CodeRegistry codeRegistry = codeRegistryRepository.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                return handleUnauthorizedAccess(meta, responseWrapper,
                        "Unauthorized call to addOrUpdateCodeSchemesFromFile.");
            }
            Set<CodeScheme> codeSchemes = new HashSet<>();
            Set<Code> codes = new HashSet<>();
            try {
                if (FORMAT_CSV.equalsIgnoreCase(format)) {
                    codeSchemes = codeSchemeParser.parseCodeSchemesFromCsvInputStream(codeRegistry, inputStream);
                } else if (FORMAT_EXCEL.equalsIgnoreCase(format)) {
                    try (final Workbook workbook = WorkbookFactory.create(inputStream)) {
                        codeSchemes = codeSchemeParser.parseCodeSchemesFromExcel(codeRegistry, workbook);
                        if (!codeSchemes.isEmpty() && codeSchemes.size() == 1 && workbook.getSheet(EXCEL_SHEET_CODESCHEMES) != null) {
                            codes = codeParser.parseCodesFromExcel(codeSchemes.iterator().next(), workbook);
                        }
                    }
                }
            } catch (final Exception e) {
                return handleInternalServerError(meta, responseWrapper, "Internal server error during call to addOrUpdateCodeSchemesFromFile.", e);
            }
            for (final CodeScheme codeScheme : codeSchemes) {
                if (!startDateIsBeforeEndDateSanityCheck(codeScheme.getStartDate(), codeScheme.getEndDate())) {
                    return handleStartDateLaterThanEndDate(responseWrapper);
                }
                LOG.debug("CodeScheme parsed from input: " + codeScheme.getCodeValue());
            }
            if (!codeSchemes.isEmpty()) {
                domain.persistCodeSchemes(codeSchemes);
                indexing.updateCodeSchemes(codeSchemes);
                for (final CodeScheme codeScheme : codeSchemes) {
                    indexing.updateCodes(codeRepository.findByCodeScheme(codeScheme));
                    indexing.updateExternalReferences(externalReferenceRepository.findByParentCodeScheme(codeScheme));
                }
            }
            for (final Code code : codes) {
                if (!startDateIsBeforeEndDateSanityCheck(code.getStartDate(), code.getEndDate())) {
                    return handleStartDateLaterThanEndDate(responseWrapper);
                }
                LOG.debug("Code parsed from input: " + code.getCodeValue());
            }
            if (!codes.isEmpty()) {
                domain.persistCodes(codes);
                indexing.updateCodes(codes);
            }
            meta.setMessage("CodeSchemes added or modified: " + codeSchemes.size());
            meta.setCode(200);
            responseWrapper.setResults(codeSchemes);
            return Response.ok(responseWrapper).build();
        }
        meta.setMessage("CodeScheme with code: " + codeRegistryCodeValue + " does not exist yet, please creater register first.");
        meta.setCode(404);
        return Response.status(Response.Status.NOT_ACCEPTABLE).entity(responseWrapper).build();
    }

    @POST
    @Path("{codeRegistryCodeValue}/codeschemes/{codeSchemeId}/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Modifies single existing CodeScheme.")
    @ApiResponse(code = 200, message = "Returns success.")
    @Transactional
    public Response updateCodeScheme(@ApiParam(value = "CodeRegistry codeValue", required = true) @PathParam("codeRegistryCodeValue") final String codeRegistryCodeValue,
                                     @ApiParam(value = "CodeScheme codeValue", required = true) @PathParam("codeSchemeId") final String codeSchemeId,
                                     @ApiParam(value = "JSON playload for Code data.", required = false) final String jsonPayload) throws WebApplicationException {

        logApiRequest(LOG, METHOD_POST, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES + "/" + codeRegistryCodeValue + API_PATH_CODESCHEMES + "/" + codeSchemeId + "/");
        final Meta meta = new Meta();
        final MetaResponseWrapper responseWrapper = new MetaResponseWrapper(meta);
        final CodeRegistry codeRegistry = codeRegistryRepository.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                return handleUnauthorizedAccess(meta, responseWrapper,
                        "Unauthorized call to updateCodeScheme.");
            }
            final CodeScheme existingCodeScheme = codeSchemeRepository.findByCodeRegistryAndId(codeRegistry, UUID.fromString(codeSchemeId));
            if (existingCodeScheme != null) {
                try {
                    if (jsonPayload == null || jsonPayload.isEmpty()) {
                        return handleBadRequest(
                                meta,
                                responseWrapper,
                                "No JSON payload found.");
                    }

                    final ObjectMapper mapper = createObjectMapper();
                    final CodeScheme payload = mapper.readValue(jsonPayload, CodeScheme.class);

                    if (!existingCodeScheme.getCodeValue().equalsIgnoreCase(payload.getCodeValue())) {
                        return handleBadRequest(
                                meta,
                                responseWrapper,
                                "CodeScheme cannot be updated because CodeValue changed: " + payload.getCodeValue());
                    }

                    updateCodeSchemeFromPayload(existingCodeScheme, payload);

                    if (!startDateIsBeforeEndDateSanityCheck(
                            existingCodeScheme.getStartDate(),
                            existingCodeScheme.getEndDate())) {
                        return handleStartDateLaterThanEndDate(responseWrapper);
                    }

                    codeSchemeRepository.save(existingCodeScheme);

                    if (indexing.updateCodeScheme(existingCodeScheme) &&
                        indexing.updateCodes(codeRepository.findByCodeScheme(existingCodeScheme)) &&
                        indexing.updateExternalReferences(externalReferenceRepository.findByParentCodeScheme(existingCodeScheme))) {
                        meta.setMessage("CodeScheme " + codeSchemeId + " modified.");
                        meta.setCode(200);
                        return Response.ok(responseWrapper).build();
                    } else {
                        return handleInternalServerError(meta, responseWrapper,
                                "CodeScheme " + codeSchemeId + " modification failed.", new WebApplicationException());
                    }

                } catch (Exception e) {
                    return handleInternalServerError(meta, responseWrapper, "Internal server error during call to updateCodeScheme.", e);
                }
            } else {
                meta.setMessage("CodeScheme: " + codeSchemeId + " does not exist yet, please create codeScheme first.");
                meta.setCode(406);
            }
        } else {
            meta.setMessage("CodeRegistry with code: " + codeRegistryCodeValue + " does not exist yet, please create registry first.");
            meta.setCode(406);
        }
        return Response.status(Response.Status.NOT_ACCEPTABLE).entity(responseWrapper).build();
    }

    @POST
    @Path("{codeRegistryCodeValue}/codeschemes/{codeSchemeId}/codes/")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Parses Codes from JSON input.")
    @ApiResponse(code = 200, message = "Returns success.")
    @Transactional
    public Response addOrUpdateCodesFromJson(@ApiParam(value = "Format for input.", required = true) @QueryParam("format") @DefaultValue("json") final String format,
                                             @ApiParam(value = "CodeRegistry codeValue", required = true) @PathParam("codeRegistryCodeValue") final String codeRegistryCodeValue,
                                             @ApiParam(value = "CodeScheme codeValue", required = true) @PathParam("codeSchemeId") final String codeSchemeId,
                                             @ApiParam(value = "JSON playload for Code data.", required = true) final String jsonPayload) {

        logApiRequest(LOG, METHOD_POST, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES + "/" + codeRegistryCodeValue + API_PATH_CODESCHEMES + "/" + codeSchemeId + API_PATH_CODES + "/");
        final Meta meta = new Meta();
        final ResponseWrapper<Code> responseWrapper = new ResponseWrapper<>(meta);
        ObjectWriterInjector.set(new AbstractBaseResource.FilterModifier(createSimpleFilterProvider(FILTER_NAME_CODE, "codeRegistry,codeScheme")));
        final CodeRegistry codeRegistry = codeRegistryRepository.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                return handleUnauthorizedAccess(meta, responseWrapper,
                        "Unauthorized call to addOrUpdateCodesFromJson.");
            }
            final CodeScheme codeScheme = codeSchemeRepository.findByCodeRegistryAndId(codeRegistry, UUID.fromString(codeSchemeId));
            if (codeScheme != null) {
                Set<Code> codes = new HashSet<>();
                try {
                    if (FORMAT_JSON.equalsIgnoreCase(format) && jsonPayload != null && !jsonPayload.isEmpty()) {
                        final ObjectMapper mapper = createObjectMapper();
                        codes = mapper.readValue(jsonPayload, new TypeReference<Set<Code>>() {
                        });
                        for (final Code code : codes) {
                            if (!startDateIsBeforeEndDateSanityCheck(code.getStartDate(), code.getEndDate())) {
                                return handleStartDateLaterThanEndDate(responseWrapper);
                            }
                            if (code.getId() == null) {
                                code.setId(UUID.randomUUID());
                                code.setUri(apiUtils.createCodeUri(codeRegistry, codeScheme, code));
                                code.setCodeScheme(codeScheme);
                                code.setModified(new Date(System.currentTimeMillis()));
                            }
                            final Set<ExternalReference> externalReferences = initializeExternalReferences(code.getExternalReferences(), codeScheme);
                            if (!externalReferences.isEmpty()) {
                                externalReferenceRepository.save(externalReferences);
                                code.setExternalReferences(externalReferences);
                            } else {
                                code.setExternalReferences(null);
                            }
                        }
                    }
                } catch (Exception e) {
                    return handleInternalServerError(meta, responseWrapper, "Internal server error during call to addOrUpdateCodesFromJson.",e);
                }
                if (!codes.isEmpty()) {
                    domain.persistCodes(codes);
                    indexing.updateCodes(codes);
                }
                meta.setMessage("Codes added or modified: " + codes.size());
                meta.setCode(200);
                // Hack for removing cyclic dependency issue in response.
                for (final Code code : codes) {
                    code.setCodeScheme(null);
                }
                responseWrapper.setResults(codes);
                return Response.ok(responseWrapper).build();
            } else {
                meta.setMessage("CodeScheme with id: " + codeSchemeId + " does not exist yet, please create codeScheme first.");
                meta.setCode(406);
            }
        } else {
            meta.setMessage("CodeRegistry with id: " + codeRegistryCodeValue + " does not exist yet, please create registry first.");
            meta.setCode(406);
        }
        return Response.status(Response.Status.NOT_ACCEPTABLE).entity(responseWrapper).build();
    }

    @POST
    @Path("{codeRegistryCodeValue}/codeschemes/{codeSchemeId}/codes/")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Parses Codes from input data.")
    @ApiResponse(code = 200, message = "Returns success.")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "file", value = "Input-file", required = false, dataType = "file", paramType = "formData")
    })
    @Transactional
    public Response addOrUpdateCodesFromFile(@ApiParam(value = "Format for input.", required = true) @QueryParam("format") @DefaultValue("csv") final String format,
                                             @ApiParam(value = "CodeRegistry codeValue", required = true) @PathParam("codeRegistryCodeValue") final String codeRegistryCodeValue,
                                             @ApiParam(value = "CodeScheme codeValue", required = true) @PathParam("codeSchemeId") final String codeSchemeId,
                                             @ApiParam(value = "Input-file for CSV or Excel import.", required = false, hidden = true, type = "file") @FormDataParam("file") final InputStream inputStream) {

        logApiRequest(LOG, METHOD_POST, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES + "/" + codeRegistryCodeValue + API_PATH_CODESCHEMES + "/" + codeSchemeId + API_PATH_CODES + "/");
        final Meta meta = new Meta();
        final ResponseWrapper<Code> responseWrapper = new ResponseWrapper<>(meta);
        ObjectWriterInjector.set(new AbstractBaseResource.FilterModifier(createSimpleFilterProvider(FILTER_NAME_CODE, "codeRegistry,codeScheme")));
        final CodeRegistry codeRegistry = codeRegistryRepository.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                return handleUnauthorizedAccess(meta, responseWrapper,
                        "Unauthorized call to addOrUpdateCodesFromFile.");
            }
            final CodeScheme codeScheme = codeSchemeRepository.findByCodeRegistryAndId(codeRegistry, UUID.fromString(codeSchemeId));
            if (codeScheme != null) {
                Set<Code> codes = new HashSet<>();
                try {
                    if (FORMAT_CSV.equalsIgnoreCase(format)) {
                        codes = codeParser.parseCodesFromCsvInputStream(codeScheme, inputStream);
                    } else if (FORMAT_EXCEL.equalsIgnoreCase(format)) {
                        codes = codeParser.parseCodesFromExcelInputStream(codeScheme, inputStream);
                    }
                } catch (Exception e) {
                    return handleInternalServerError(meta, responseWrapper, "Internal server error during call to addOrUpdateCodesFromFile." ,e);
                }
                for (final Code code : codes) {
                    if (!startDateIsBeforeEndDateSanityCheck(code.getStartDate(), code.getEndDate())) {
                        return handleStartDateLaterThanEndDate(responseWrapper);
                    }
                    LOG.debug("Code parsed from input: " + code.getCodeValue());
                }
                if (!codes.isEmpty()) {
                    domain.persistCodes(codes);
                    indexing.updateCodes(codes);
                }
                meta.setMessage("Codes added or modified: " + codes.size());
                meta.setCode(200);
                // Hack for removing cyclic dependency issue in response.
                for (final Code code : codes) {
                    code.setCodeScheme(null);
                }
                responseWrapper.setResults(codes);
                return Response.ok(responseWrapper).build();
            } else {
                meta.setMessage("CodeScheme with id: " + codeSchemeId + " does not exist yet, please create CodeScheme first.");
                meta.setCode(406);
            }
        } else {
            meta.setMessage("CodeRegistry with id: " + codeRegistryCodeValue + " does not exist yet, please create CodeRegistry first.");
            meta.setCode(406);
        }
        return Response.status(Response.Status.NOT_ACCEPTABLE).entity(responseWrapper).build();
    }

    @POST
    @Path("{codeRegistryCodeValue}/codeschemes/{codeSchemeId}/codes/{codeId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Modifies single existing Code.")
    @ApiResponse(code = 200, message = "Returns success.")
    @Transactional
    public Response updateCode(@ApiParam(value = "CodeRegistry codeValue", required = true) @PathParam("codeRegistryCodeValue") final String codeRegistryCodeValue,
                               @ApiParam(value = "CodeScheme codeValue", required = true) @PathParam("codeSchemeId") final String codeSchemeId,
                               @ApiParam(value = "Code codeValue.", required = true) @PathParam("codeId") final String codeId,
                               @ApiParam(value = "JSON playload for Code data.", required = false) final String jsonPayload) {

        logApiRequest(LOG, METHOD_POST, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES + "/" + codeRegistryCodeValue + API_PATH_CODESCHEMES + "/" + codeSchemeId + API_PATH_CODES + "/" + codeId + "/");
        final Meta meta = new Meta();
        final MetaResponseWrapper responseWrapper = new MetaResponseWrapper(meta);
        final CodeRegistry codeRegistry = codeRegistryRepository.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                return handleUnauthorizedAccess(meta, responseWrapper,
                        "Unauthorized call to updateCode.");
            }
            final CodeScheme codeScheme = codeSchemeRepository.findByCodeRegistryAndId(codeRegistry, UUID.fromString(codeSchemeId));
            if (codeScheme != null) {
                final Code existingCode = codeRepository.findByCodeSchemeAndId(codeScheme, UUID.fromString(codeId));
                try {
                    if (jsonPayload != null && !jsonPayload.isEmpty()) {
                        final ObjectMapper mapper = createObjectMapper();
                        final Code code = mapper.readValue(jsonPayload, Code.class);
                        if (!startDateIsBeforeEndDateSanityCheck(code.getStartDate(), code.getEndDate())) {
                            return handleStartDateLaterThanEndDate(responseWrapper);
                        }
                        if (!code.getCodeValue().equalsIgnoreCase(existingCode.getCodeValue())) {
                            LOG.error("Code cannot be updated because CodeValue changed: " + code.getCodeValue());
                            meta.setMessage("Code cannot be updated because CodeValue changed. " + code.getCodeValue());
                            meta.setCode(406);
                        } else {
                            final Set<ExternalReference> externalReferences = initializeExternalReferences(code.getExternalReferences(), codeScheme);
                            if (!externalReferences.isEmpty()) {
                                externalReferenceRepository.save(externalReferences);
                                code.setExternalReferences(externalReferences);
                            } else {
                                code.setExternalReferences(null);
                            }
                            code.setModified(new Date(System.currentTimeMillis()));
                            codeRepository.save(code);
                            codeSchemeRepository.save(codeScheme);
                            if (indexing.updateCode(code) &&
                                indexing.updateCodeScheme(codeScheme) &&
                                indexing.updateExternalReferences(externalReferenceRepository.findByParentCodeScheme(codeScheme))) {
                                meta.setMessage("Code " + codeId + " modified.");
                                meta.setCode(200);
                                return Response.ok(responseWrapper).build();
                            } else {
                                return handleInternalServerError(meta, responseWrapper,
                                        "Code " + codeId + " modifification failed.", new WebApplicationException());
                            }
                        }
                    } else {
                        meta.setMessage("No JSON payload found.");
                        meta.setCode(406);
                    }
                } catch (Exception e) {
                    return handleInternalServerError(meta, responseWrapper, "Internal server error during call to updateCode.", e);
                }
            } else {
                meta.setMessage("CodeScheme with id: " + codeSchemeId + " does not exist yet, please create codeScheme first.");
                meta.setCode(406);
            }
        } else {
            meta.setMessage("CodeRegistry with CodeValue: " + codeRegistryCodeValue + " does not exist yet, please create registry first.");
            meta.setCode(406);
        }
        return Response.status(Response.Status.NOT_ACCEPTABLE).entity(responseWrapper).build();
    }

    @DELETE
    @Path("{codeRegistryCodeValue}/codeschemes/{codeSchemeId}/codes/{codeId}")
    @Produces(MediaType.APPLICATION_JSON + ";charset=UTF-8")
    @ApiOperation(value = "Deletes a single code. This means that the item status is set to Status.RETIRED.")
    @ApiResponse(code = 200, message = "Returns success.")
    @Transactional
    public Response retireCode(@ApiParam(value = "CodeRegistry codeValue.", required = true) @PathParam("codeRegistryCodeValue") final String codeRegistryCodeValue,
                               @ApiParam(value = "CodeScheme codeValue.", required = true) @PathParam("codeSchemeId") final String codeSchemeId,
                               @ApiParam(value = "Code codeValue.", required = true) @PathParam("codeId") final String codeId) {
        logApiRequest(LOG, METHOD_DELETE, API_PATH_VERSION_V1, API_PATH_CODEREGISTRIES + "/" + codeRegistryCodeValue + API_PATH_CODESCHEMES + "/" + codeSchemeId + API_PATH_CODES + "/" + codeId + "/");
        final Meta meta = new Meta();
        final MetaResponseWrapper responseWrapper = new MetaResponseWrapper(meta);
        final CodeRegistry codeRegistry = codeRegistryRepository.findByCodeValue(codeRegistryCodeValue);
        if (codeRegistry != null) {
            if (!authorizationManager.canBeModifiedByUserInOrganization(codeRegistry.getOrganizations())) {
                return handleUnauthorizedAccess(meta, responseWrapper,
                        "Unauthorized call to retireCode.");
            }
            final CodeScheme codeScheme = codeSchemeRepository.findByCodeRegistryAndCodeValue(codeRegistry, codeSchemeId);
            if (codeScheme != null) {
                final Code code = codeRepository.findByCodeSchemeAndCodeValue(codeScheme, codeId);
                if (code != null) {
                    code.setStatus(Status.RETIRED.toString());
                    codeRepository.save(code);
                    indexing.reIndexEverything();
                    meta.setMessage("Code marked as RETIRED!");
                    meta.setCode(200);
                    return Response.ok(responseWrapper).build();
                } else {
                    meta.setMessage("Code " + codeId + " not found!");
                    meta.setCode(404);
                }
            } else {
                meta.setMessage("CodeScheme with id: " + codeSchemeId + " not found!");
                meta.setCode(404);
            }
        } else {
            meta.setMessage("CodeRegistry with codeValue: " + codeRegistryCodeValue + " not found!");
            meta.setCode(404);
        }
        return Response.status(Response.Status.NOT_FOUND).entity(responseWrapper).build();
    }

    private Set<ExternalReference> initializeExternalReferences(final Set<ExternalReference> externalReferences,
                                                                final CodeScheme codeScheme) {
        if (externalReferences != null) {
            externalReferences.forEach(externalReference -> {
                boolean hasChanges = false;
                if (externalReference.getId() == null) {
                    final UUID referenceUuid = UUID.randomUUID();
                    externalReference.setId(referenceUuid);
                    externalReference.setUri(apiUtils.createResourceUri(API_PATH_EXTERNALREFERENCES, referenceUuid.toString()));
                    hasChanges = true;
                } else {
                    final ExternalReference existingExternalReference = externalReferenceRepository.findById(externalReference.getId());
                    if (existingExternalReference != null) {
                        externalReference.setModified(existingExternalReference.getModified());
                    }
                }
                if (externalReference.getParentCodeScheme() == null) {
                    externalReference.setParentCodeScheme(codeScheme);
                    hasChanges = true;
                }
                if (hasChanges) {
                    externalReference.setModified(new Date(System.currentTimeMillis()));
                }
            });
            return externalReferences;
        } else {
            return new HashSet<ExternalReference>();
        }
    }


    private void updateCodeSchemeFromPayload(final CodeScheme existing,
                                             final CodeScheme payload) {
        //From AbstractIdentifyableCode
        //id

        //From AbstractBaseCode
        existing.setModified(new Date(System.currentTimeMillis()));
        //uri

        //From AbstractCommonCode
        //codeValue

        //From AbstractHistoricalCode
        existing.setStartDate(payload.getStartDate());
        existing.setEndDate(payload.getEndDate());
        existing.setStatus(payload.getStatus());

        //From CodeScheme
        //codes
        //codeRegistry
        //version TODO - should version be updated also?
        existing.setSource(payload.getSource());
        existing.setLegalBase(existing.getLegalBase());
        existing.setGovernancePolicy(payload.getGovernancePolicy());
        existing.setLicense(payload.getLicense());
        existing.setPrefLabel(payload.getPrefLabel());
        existing.setDefinition(payload.getDefinition());
        existing.setDescription(payload.getDescription());
        existing.setChangeNote(payload.getChangeNote());
        existing.setDataClassifications(
                payload.getDataClassifications().stream()
                    .map( it -> it.getId())
                    .map( it -> codeRepository.findById(it))
                    .collect(Collectors.toSet()));


        Map<UUID, ExternalReference> existingExtRefs = existing.getExternalReferences().stream()
                .collect(Collectors.toMap(ExternalReference::getId, Function.identity()));

        Map<UUID, ExternalReference> payloadExtRefs = payload.getExternalReferences().stream()
                .collect(Collectors.toMap(ExternalReference::getId, Function.identity()));

        //Deleted & modified existing extRefs
        Iterator<ExternalReference> existingExtRefsIterator = existing.getExternalReferences().iterator();
        while (existingExtRefsIterator.hasNext()) {
            ExternalReference existingExtRef = existingExtRefsIterator.next();
            ExternalReference payloadExtRef = payloadExtRefs.get(existingExtRef.getId());

            if (payloadExtRef == null) {
                existingExtRefsIterator.remove();
                if (!existingExtRef.getGlobal()) {
                    externalReferenceRepository.delete(existingExtRef);
                }
            } else {
                updateExternalReferenceFromPayload(existingExtRef, payloadExtRef);
            }
        }


        //New created extRefs
        existing.getExternalReferences().addAll(
                payload.getExternalReferences().stream()
                    .filter(it -> it.getId() == null)
                    .peek(it -> {
                        UUID uuid = UUID.randomUUID();
                        it.setId(uuid);
                        it.setUri(apiUtils.createResourceUri(API_PATH_EXTERNALREFERENCES, uuid.toString()));
                        it.setParentCodeScheme(existing);
                    })
                    .collect(Collectors.toList()));


        //Assigned global extRefs
        existing.getExternalReferences().addAll(
                payload.getExternalReferences().stream()
                    .filter(it -> existingExtRefs.get(it.getId()) == null)
                    .map(it -> it.getId())
                    .map(it -> externalReferenceRepository.findById(it))
                    .collect(Collectors.toList()));
    }


    private void updateExternalReferenceFromPayload(final ExternalReference existing,
                                                    final ExternalReference payload) {
        if (existing.getGlobal()){
            return;
        }

        //From AbstractIdentifyableCode
        //id

        //From AbstractBaseCode
        //uri
        existing.setModified(new Date(System.currentTimeMillis()));


        //From ExternalReference
        //codeSchemes
        //codes
        //parentCodeScheme
        //global
        existing.setUrl(payload.getUrl());
        existing.setTitle(payload.getTitle());
        existing.setDescription(payload.getDescription());
        existing.setPropertyType(payload.getPropertyType());
    }
}

