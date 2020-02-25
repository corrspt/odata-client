package com.github.davidmoten.msgraph.beta;

import com.github.davidmoten.msgraph.MsGraphClientBuilder;
import com.github.davidmoten.msgraph.MsGraphClientBuilder.Builder;

import odata.msgraph.client.beta.container.GraphService;

public final class MsGraph {

    private static final String MSGRAPH_BETA_BASE_URL = "https://graph.microsoft.com/beta";

    private MsGraph() {
        // prevent instantiation
    }

    public static Builder<GraphService> tenantName(String tenantName) {
        return new MsGraphClientBuilder<GraphService>(MSGRAPH_BETA_BASE_URL, context -> new GraphService(context)).tenantName(tenantName);
    }
}
