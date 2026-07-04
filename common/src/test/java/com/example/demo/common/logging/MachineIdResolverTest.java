package com.example.demo.common.logging;

import com.example.demo.common.config.ShadowProxyProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MachineIdResolverTest {

    @Test
    void resolveReturnsNonBlankValue() {
        ShadowProxyProperties properties = new ShadowProxyProperties();
        properties.setMachineIdEnvVar("SHADOWMODE_LLM_TEST_NONEXISTENT_ENV_VAR");

        MachineIdResolver resolver = new MachineIdResolver(properties);

        String machineId = resolver.resolve();

        assertThat(machineId).isNotNull();
        assertThat(machineId).isNotBlank();
    }

    @Test
    void resolveCachesValueAcrossCalls() {
        ShadowProxyProperties properties = new ShadowProxyProperties();
        properties.setMachineIdEnvVar("SHADOWMODE_LLM_TEST_NONEXISTENT_ENV_VAR");

        MachineIdResolver resolver = new MachineIdResolver(properties);

        String first = resolver.resolve();
        String second = resolver.resolve();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void usesEnvironmentVariableWhenConfiguredVariableIsSetAndNonBlank() {
        String pathValue = System.getenv("PATH");
        org.junit.jupiter.api.Assumptions.assumeTrue(pathValue != null && !pathValue.isBlank(),
                "PATH environment variable must be set for this test to be meaningful");

        ShadowProxyProperties properties = new ShadowProxyProperties();
        properties.setMachineIdEnvVar("PATH");

        MachineIdResolver resolver = new MachineIdResolver(properties);

        assertThat(resolver.resolve()).isEqualTo(pathValue);
    }
}
