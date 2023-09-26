package com.smattme.config;

import org.testcontainers.containers.InternetProtocol;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

public class FixedPortMySQLContainer<SELF extends FixedPortMySQLContainer<SELF>> extends MySQLContainer<SELF> {

    public FixedPortMySQLContainer(String dockerImageName) {
        super(dockerImageName);
    }

    public FixedPortMySQLContainer(DockerImageName dockerImageName) {
        super(dockerImageName);
    }

    public void addFixedExposedPort(int hostPort, int containerPort) {
        addFixedExposedPort(hostPort, containerPort, InternetProtocol.TCP);
    }
}
