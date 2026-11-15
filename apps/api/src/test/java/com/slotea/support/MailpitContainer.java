package com.slotea.support;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/** Mailpit (la misma imagen que en {@code compose.yaml}): un SMTP real que guarda los emails y los enseña por API. */
public class MailpitContainer extends GenericContainer<MailpitContainer> {

    private static final int SMTP = 1025;
    private static final int HTTP = 8025;

    public MailpitContainer() {
        super(DockerImageName.parse("axllent/mailpit:v1.33.1"));
        withExposedPorts(SMTP, HTTP);
        // Sin esto saluda tras ~10 s (DNS inverso del cliente): ver compose.yaml.
        withEnv("MP_SMTP_DISABLE_RDNS", "true");
        waitingFor(Wait.forHttp("/readyz").forPort(HTTP));
    }

    public int smtpPort() {
        return getMappedPort(SMTP);
    }

    public String apiUrl() {
        return "http://" + getHost() + ":" + getMappedPort(HTTP);
    }
}
