package fi.vm.yti.codelist.intake.groupmanagement;

import static fi.vm.yti.codelist.common.constants.ApiConstants.GROUPMANAGEMENT_PUBLIC_API;
import static fi.vm.yti.codelist.common.constants.ApiConstants.GROUPMANAGEMENT_USERS;
import static org.springframework.http.HttpMethod.GET;

import java.util.Collections;
import java.util.List;

import javax.inject.Inject;
import javax.validation.constraints.NotNull;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import fi.vm.yti.codelist.intake.configuration.GroupManagementProperties;
import fi.vm.yti.codelist.intake.configuration.ImpersonateProperties;

@Component
public class ImpersonateUserService {
    
    private final GroupManagementProperties groupManagementProperties;
    private final ImpersonateProperties fakeLoginProperties;
    private final RestTemplate restTemplate;

    @Inject
    public ImpersonateUserService(final GroupManagementProperties groupManagementProperties, final ImpersonateProperties fakeLoginProperties) {
        this.groupManagementProperties = groupManagementProperties;
        this.fakeLoginProperties = fakeLoginProperties;
        this.restTemplate = new RestTemplate();
    }    
    
    @NotNull
    public List<GroupManagementUser> getUsers() {
        if (fakeLoginProperties.isAllowed()) {
            String url = groupManagementProperties.getUrl() + GROUPMANAGEMENT_PUBLIC_API + GROUPMANAGEMENT_USERS;
            System.out.println("URL " + url);
            return restTemplate.exchange(url, GET, null, new ParameterizedTypeReference<List<GroupManagementUser>>() {
            }).getBody();
        } else {
            return Collections.emptyList();
        }
    }
}