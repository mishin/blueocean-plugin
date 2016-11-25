/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.jenkins.blueocean.i18n;

import hudson.Extension;
import hudson.PluginWrapper;
import hudson.model.RootAction;
import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Internationalization REST (ish) API for Blue Ocean.
 * @author <a href="mailto:tom.fennelly@gmail.com">tom.fennelly@gmail.com</a>
 */
@Extension
@Restricted(NoExternalUse.class)
public class BlueI18n implements RootAction {

    /**
     * Failed lookup cache entry.
     */
    private static final JSONObject BUNDLE_404 = new JSONObject();

    /**
     * Bundle cache.
     */
    private Map<BundleParams, BundleCacheEntry> bundleCache = new ConcurrentHashMap<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public String getIconFileName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDisplayName() {
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getUrlName() {
        return "blueocean-i18n";
    }

    /**
     * Get a localised resource bundle.
     * <p>
     * URL: {@code blueocean-i18n/$PLUGIN_NAME/$PLUGIN_VERSION/$BUNDLE_NAME/$LOCALE} (where {@code $LOCALE} is optional).
     *
     * @param request The request.
     * @return The JSON response.
     */
    public HttpResponse doDynamic(StaplerRequest request) {
        BundleParams bundleParams = getBundleParameters(request.getRestOfPath());

        if (bundleParams == null) {
            return HttpResponses.errorJSON("All mandatory bundle identification parameters not specified: '$PLUGIN_NAME/$PLUGIN_VERSION/$BUNDLE_NAME' (and optional $LOCALE).");
        }

        try {
            Locale locale = bundleParams.getLocale();

            if (locale == null) {
                locale = request.getLocale();
            }

            BundleCacheEntry bundleCacheEntry = bundleCache.get(bundleParams);

            JSONObject bundle;
            if (bundleCacheEntry == null) {
                bundle = getBundle(bundleParams, locale);
                if (bundle == null) {
                    bundle = BUNDLE_404;
                }
                bundleCacheEntry = new BundleCacheEntry(bundle);
                bundleCache.put(bundleParams, bundleCacheEntry);
            }

            if (bundleCacheEntry.bundleData == BUNDLE_404) {
                return JSONObjectResponse.errorJson("Unknown plugin or resource bundle: " + bundleParams.toString(), HttpServletResponse.SC_NOT_FOUND);
            } else {
                return JSONObjectResponse.okJson(bundleCacheEntry);
            }
        } catch (Exception e) {
            return HttpResponses.errorJSON(e.getMessage());
        }
    }

    @CheckForNull
    private JSONObject getBundle(BundleParams bundleParams, Locale locale) {
        PluginWrapper plugin = bundleParams.getPlugin();

        if (plugin == null) {
            return null;
        }

        try {
            ResourceBundle resourceBundle = ResourceBundle.getBundle(bundleParams.bundleName, locale, plugin.classLoader);
            JSONObject bundleJSON = new JSONObject();
            for (String key : resourceBundle.keySet()) {
                bundleJSON.put(key, resourceBundle.getString(key));
            }
            return bundleJSON;
        } catch (MissingResourceException e) {
            // fall through and return null.
        }

        return null;
    }

    @CheckForNull
    static BundleParams getBundleParameters(String restOfPath) {
        if (restOfPath == null || restOfPath.length() == 0) {
            return null;
        }

        String[] pathTokens = restOfPath.split("/");
        List<String> bundleParameters = new ArrayList<>();

        for (String pathToken : pathTokens) {
            if (pathToken.length() > 0) {
                bundleParameters.add(pathToken);
            }
        }

        if (bundleParameters.size() != 3 && bundleParameters.size() != 4) {
            return null;
        }

        BundleParams bundleParams = new BundleParams(
            bundleParameters.get(0),
            bundleParameters.get(1),
            bundleParameters.get(2)
        );
        if (bundleParameters.size() == 4) {
            // https://www.w3.org/International/questions/qa-lang-priorities
            // in case we have regions/countries in the language query parameter
            String locale = bundleParameters.get(3);
            String[] localeTokens = locale.split("-|_");
            bundleParams.language = localeTokens[0];
            if (localeTokens.length > 1) {
                bundleParams.country = localeTokens[1];
                if (localeTokens.length > 2) {
                    bundleParams.variant = localeTokens[2];
                }
            }
        }

        return bundleParams;
    }

    @CheckForNull
    static PluginWrapper getPlugin(String pluginName) {
        Jenkins jenkins = Jenkins.getInstance();
        return jenkins.getPluginManager().getPlugin(pluginName);
    }

    static class BundleParams {
        final String pluginName;
        final String pluginVersion;
        final String bundleName;
        String language;
        String country;
        String variant;
        private PluginWrapper plugin;

        BundleParams(String pluginName, String pluginVersion, String bundleName) {
            this.pluginName = pluginName;
            this.pluginVersion = pluginVersion;
            this.bundleName = bundleName;
        }

        @CheckForNull
        Locale getLocale() {
            if (language != null && country != null && variant != null) {
                return new Locale(language, country, variant);
            } else if (language != null && country != null) {
                return new Locale(language, country);
            } else if (language != null) {
                return new Locale(language);
            } else {
                return null;
            }
        }

        // We declare the plugin to be a release version iff
        // the version string contains only numerics.
        private static Pattern RELEASE_VERSION_PATTERN = Pattern.compile("[\\d/.]{3,}");
        boolean isReleaseVersion(String version) {
            return RELEASE_VERSION_PATTERN.matcher(version).matches();
        }
        boolean isReleaseVersion() {
            return isReleaseVersion(pluginVersion);
        }

        @CheckForNull
        PluginWrapper getPlugin() {
            if (plugin != null) {
                return plugin;
            }
            plugin = BlueI18n.getPlugin(pluginName);
            return plugin;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            BundleParams that = (BundleParams) o;

            if (!pluginName.equals(that.pluginName)) {
                return false;
            }
            if (!pluginVersion.equals(that.pluginVersion)) {
                return false;
            }
            if (!bundleName.equals(that.bundleName)) {
                return false;
            }
            if (language != null ? !language.equals(that.language) : that.language != null) {
                return false;
            }
            if (country != null ? !country.equals(that.country) : that.country != null) {
                return false;
            }
            return variant != null ? variant.equals(that.variant) : that.variant == null;
        }

        @Override
        public int hashCode() {
            int result = pluginName.hashCode();
            result = 31 * result + pluginVersion.hashCode();
            result = 31 * result + bundleName.hashCode();
            result = 31 * result + (language != null ? language.hashCode() : 0);
            result = 31 * result + (country != null ? country.hashCode() : 0);
            result = 31 * result + (variant != null ? variant.hashCode() : 0);
            return result;
        }

        @Override
        public String toString() {
            String string = pluginName + "/" + pluginVersion + "/" + bundleName;
            Locale locale = getLocale();
            if (locale != null) {
                string += "/" + locale.toString();
            }
            return string;
        }
    }

    private static class BundleCacheEntry {
        private long timestamp = System.currentTimeMillis();
        private JSONObject bundleData;

        public BundleCacheEntry(JSONObject bundleData) {
            this.bundleData = bundleData;
        }
    }

    static class JSONObjectResponse implements HttpResponse {

        private static final Charset UTF8 = Charset.forName("UTF-8");

        private final JSONObject jsonObject = new JSONObject();
        private int statusCode = HttpServletResponse.SC_OK;

        private static JSONObjectResponse okJson(BundleCacheEntry bundleCacheEntry) {
            JSONObjectResponse response = new JSONObjectResponse();
            response.jsonObject.put("data", bundleCacheEntry.bundleData);
            response.jsonObject.put("status", "ok");
            response.jsonObject.put("cache-timestamp", bundleCacheEntry.timestamp);
            return response;
        }

        private static JSONObjectResponse errorJson(String message, int errorCode) {
            JSONObjectResponse response = new JSONObjectResponse();
            response.jsonObject.put("status", "error");
            response.jsonObject.put("message", message);
            response.statusCode = errorCode;
            return response;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void generateResponse(StaplerRequest req, StaplerResponse rsp, Object node) throws IOException, ServletException {
            byte[] bytes = jsonObject.toString().getBytes(UTF8);
            rsp.setStatus(statusCode);
            rsp.setContentType("application/json; charset=UTF-8");
            rsp.setContentLength(bytes.length);
            rsp.getOutputStream().write(bytes);
        }
    }
}
