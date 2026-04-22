package com.smartcampus.exception;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.logging.Logger;

// Logs every incoming request and every outgoing response automatically.
@Provider
public class LoggingFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger logger = Logger.getLogger(LoggingFilter.class.getName());

// Runs before the request reaches any resource method.
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        logger.info("Incoming request: " + requestContext.getMethod() 
                + " " + requestContext.getUriInfo().getRequestUri());
    }
// Runs after the resource method has finished and the response is ready.
    @Override
    public void filter(ContainerRequestContext requestContext, 
                       ContainerResponseContext responseContext) throws IOException {
        logger.info("Outgoing response: " + responseContext.getStatus());
    }
}