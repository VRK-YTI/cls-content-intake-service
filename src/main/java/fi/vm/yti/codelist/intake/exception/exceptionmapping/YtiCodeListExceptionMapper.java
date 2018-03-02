package fi.vm.yti.codelist.intake.exception.exceptionmapping;

import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

import fi.vm.yti.codelist.intake.exception.YtiCodeListException;

@Provider
public class YtiCodeListExceptionMapper extends BaseExceptionMapper implements ExceptionMapper<YtiCodeListException> {

    @Override
    public Response toResponse(final YtiCodeListException ex) {
        return getResponse(ex);
    }
}
