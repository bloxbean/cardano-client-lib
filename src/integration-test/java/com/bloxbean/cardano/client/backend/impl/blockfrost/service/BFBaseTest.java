package com.bloxbean.cardano.client.backend.impl.blockfrost.service;

public class BFBaseTest {
    public String projectId = System.getenv("BF_PROJECT_ID");

    public BFBaseTest() {
        projectId = System.getProperty("BF_PROJECT_ID");
        if(projectId == null || projectId.isEmpty()) {
            projectId = System.getenv("BF_PROJECT_ID");
        }
    }
}
