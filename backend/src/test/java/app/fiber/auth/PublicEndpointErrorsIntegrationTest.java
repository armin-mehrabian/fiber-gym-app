package app.fiber.auth;

import static org.assertj.core.api.Assertions.assertThat;

import app.fiber.TestcontainersConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Errors on public endpoints keep their real status and body for callers who aren't logged in.
 *
 * Runs against a real server (random port) with the full security filter chain, because
 * MockMvc skips parts of real error handling (it never forwards to Spring's /error page).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class PublicEndpointErrorsIntegrationTest {

	@LocalServerPort
	private int port;

	@Test
	void badParameterOnAPublicEndpointIs400NotA401() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/exercises?all=xyz"))
			.GET()
			.build();

		HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).isEqualTo("{\"error\":\"Invalid value 'xyz' for parameter 'all'\"}");
	}

}
