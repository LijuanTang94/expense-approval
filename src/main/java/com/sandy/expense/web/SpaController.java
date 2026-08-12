package com.sandy.expense.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the React SPA shell. The built frontend lives in classpath:/static (copied in at image
 * build time); Spring serves its assets automatically. Client-side routes (deep links like
 * /requests/1) have no static file, so we forward them to index.html and let React Router take over.
 * API routes (/api/**) and static assets are matched first and never reach here.
 */
@Controller
public class SpaController {

    @GetMapping({"/", "/login", "/requests/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
