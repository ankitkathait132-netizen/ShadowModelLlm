package com.example.demo.common.logging;

import com.example.demo.common.config.ShadowProxyProperties;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Resolves a stable machine identifier for structured logs, in this order:
 * 1. MACHINE_ID environment variable (name configurable via app.machine-id-env-var).
 * 2. DigitalOcean droplet hostname (via the droplet metadata service).
 * 3. OS hostname.
 * 4. Generated boot ID as a last resort.
 */
@Component
public class MachineIdResolver {

    private static final String DO_METADATA_HOSTNAME_URL = "http://169.254.169.254/metadata/v1/hostname";
    private static final Duration METADATA_TIMEOUT = Duration.ofMillis(300);

    private final ShadowProxyProperties properties;
    private final String generatedBootId = UUID.randomUUID().toString();
    private volatile String cachedMachineId;

    public MachineIdResolver(ShadowProxyProperties properties) {
        this.properties = properties;
    }

    public String resolve() {
        if (cachedMachineId == null) {
            cachedMachineId = resolveInternal();
        }
        return cachedMachineId;
    }

    private String resolveInternal() {
        String envMachineId = System.getenv(properties.getMachineIdEnvVar());
        if (envMachineId != null && !envMachineId.isBlank()) {
            return envMachineId;
        }

        String doHostname = fetchDigitalOceanHostname();
        if (doHostname != null && !doHostname.isBlank()) {
            return doHostname;
        }

        String osHostname = fetchOsHostname();
        if (osHostname != null && !osHostname.isBlank()) {
            return osHostname;
        }

        return "boot-" + generatedBootId;
    }

    private String fetchDigitalOceanHostname() {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(METADATA_TIMEOUT).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(DO_METADATA_HOSTNAME_URL))
                    .timeout(METADATA_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200 ? response.body() : null;
        } catch (Exception notOnDigitalOcean) {
            return null;
        }
    }

    private String fetchOsHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return null;
        }
    }
}
