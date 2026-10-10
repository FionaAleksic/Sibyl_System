package org.sibyl.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Real embedded Tomcat tests: catch authentication redirects that
 * MockMvc tests sometimes interpret differently than live HTTP.
 */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class SibylHttpLiveSecurityTests {
    @LocalServerPort int port;

    @Test
    void anonymousRequestsAreRedirectedOnlyForHtmlNeverForApi() throws Exception {
        try (HttpClient client=HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).build()) {
            assertEquals(200, get(client,"/login.html"));
            assertEquals(200, get(client,"/login.css"));
            assertEquals(200, get(client,"/api/v1/auth/csrf"));
            assertEquals(302, get(client,"/"));
            assertEquals(302, get(client,"/admin.html"));
            assertEquals(302, get(client,"/addons.html"));
            assertEquals(401, get(client,"/api/v1/auth/me"));
            assertEquals(401, get(client,"/api/v1/settings/public"));
            assertEquals(401, get(client,"/api/v1/addons/catalog?channel=prerelease"));
        }
    }

    int get(HttpClient client,String path) throws Exception {
        HttpRequest req=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+path))
            .header("Accept","*/*").GET().build();
        return client.send(req,HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
