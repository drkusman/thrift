package ng.asuu.thrift.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serves the statically-exported Next.js frontend directly from this jar's own classpath (populated
 * under src/main/resources/static by the frontend's own build - see DEPLOYMENT_MANUAL.md) instead of
 * running a separate Node process behind nginx. next.config.ts sets trailingSlash: true precisely so
 * every route gets its own pre-rendered index.html (e.g. admin/members/index.html), not just a single
 * SPA shell - a clean URL like /admin/members just needs resolving to that route's own file.
 * <p>
 * Registering our own handler for "/**" replaces Spring Boot's default static-resource autoconfiguration
 * for that same pattern (WebMvcAutoConfiguration checks registry.hasMappingForPattern("/**") and backs
 * off if one already exists) - annotated @RestController endpoints under /api/** are unaffected, since
 * Spring MVC always matches those ahead of any resource handler regardless of this registration.
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = super.getResource(resourcePath, location);
                        if (requested != null) return requested;
                        // A path with a dot (foo.js, foo.png) is a genuinely missing static asset, not a
                        // route - let that 404 normally instead of masking it with a route's index.html.
                        if (resourcePath.contains(".")) return null;
                        String base = resourcePath.endsWith("/")
                                ? resourcePath.substring(0, resourcePath.length() - 1)
                                : resourcePath;
                        Resource indexHtml = location.createRelative(base + "/index.html");
                        return indexHtml.exists() && indexHtml.isReadable() ? indexHtml : null;
                    }
                });
    }
}
