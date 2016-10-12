package io.jenkins.blueocean.rest.pageable;

import com.google.common.collect.Iterators;
import hudson.model.Api;
import org.kohsuke.stapler.CancelRequestHandlingException;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.Interceptor;
import org.kohsuke.stapler.interceptor.InterceptorAnnotation;

import javax.servlet.ServletException;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Renders {@link Pageable} to HTTP by honoring the current page, links to next page, etc.
 *
 * @author Kohsuke Kawaguchi
 */
@Retention(RUNTIME)
@Target(METHOD)
@InterceptorAnnotation(PagedResponse.Processor.class)
public @interface PagedResponse {
    class Processor extends Interceptor {
        public static final int DEFAULT_LIMIT=100;
        @Override
        public Object invoke(StaplerRequest request, StaplerResponse response, Object instance, Object[] arguments)
            throws IllegalAccessException, InvocationTargetException, ServletException {

            String method = request.getMethod();
            if(!method.equalsIgnoreCase("GET")){
                throw new CancelRequestHandlingException();
            }
            final Pageable<?> resp = (Pageable<?>) target.invoke(request, response, instance, arguments);

            return new HttpResponse() {
                @Override
                public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
                    int start = (req.getParameter("start") != null) ? Integer.parseInt(req.getParameter("start")) : -1;
                    int limit = (req.getParameter("limit") != null) ? Integer.parseInt(req.getParameter("limit")) : -1;

                    if(start == -1){
                        start = 0;
                    }

                    if(limit == -1){
                        limit = DEFAULT_LIMIT;
                    }
                    Object[] page;
                    if (start >= 0 && limit >= 0) {
                        page = Iterators.toArray(resp.iterator(start, limit), Object.class);
                        String separator = (req.getQueryString() != null) ? "&" : "?";
                        rsp.setHeader("Link", "<" + req.getRequestURIWithQueryString() + separator + "start=" + (start + limit) + "&limit="+limit + ">; rel=\"next\"");
                    } else {
                        page = Iterators.toArray(resp.iterator(), Object.class);
                    }
                    new Api(page).doJson(req, rsp);
                }
            };
        }


    }
}
