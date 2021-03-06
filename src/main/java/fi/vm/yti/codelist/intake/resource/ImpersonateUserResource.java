package fi.vm.yti.codelist.intake.resource;

import java.util.List;

import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.springframework.stereotype.Component;

import fi.vm.yti.codelist.intake.groupmanagement.GroupManagementUser;
import fi.vm.yti.codelist.intake.groupmanagement.ImpersonateUserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import static fi.vm.yti.codelist.common.constants.ApiConstants.API_PATH_IMPERSONABLE_USERS;

@Component
@Path(API_PATH_IMPERSONABLE_USERS)
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "System")
public class ImpersonateUserResource implements AbstractBaseResource {

    private final ImpersonateUserService impersonateUserService;

    @Inject
    public ImpersonateUserResource(ImpersonateUserService impersonateUserService) {
        super();
        this.impersonateUserService = impersonateUserService;
    }

    @GET
    public List<GroupManagementUser> isLoginFakeable() {
        return impersonateUserService.getUsers();
    }
}
