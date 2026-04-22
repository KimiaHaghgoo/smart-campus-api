package com.smartcampus;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

// This is the entry point for the entire JAX-RS application.
@ApplicationPath("/api/v1")
public class SmartCampusApp extends Application {
// This will scan and register all @Path and @Provider annotated classes it finds.
}