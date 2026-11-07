import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Container healthcheck: exits 0 when the given URL answers 200 (the JRE image has no curl). */
public final class HealthCheck {

  private HealthCheck() {}

  public static void main(String[] args) throws Exception {
    HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(args[0])).timeout(Duration.ofSeconds(2)).GET().build();
    HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
    System.exit(response.statusCode() == 200 ? 0 : 1);
  }
}
