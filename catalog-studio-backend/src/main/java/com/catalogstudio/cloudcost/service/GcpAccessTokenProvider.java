package com.catalogstudio.cloudcost.service;

import com.google.auth.oauth2.GoogleCredentials;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GcpAccessTokenProvider {

    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/cloud-platform",
            "https://www.googleapis.com/auth/cloud-billing.readonly",
            "https://www.googleapis.com/auth/bigquery"
    );

    public String accessToken() throws IOException {
        try {
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault().createScoped(SCOPES);
            credentials.refreshIfExpired();
            if (credentials.getAccessToken() == null || credentials.getAccessToken().getTokenValue() == null) {
                throw new IOException("Google ADC returned an empty access token");
            }
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException ex) {
            throw new IOException(
                    "GCP Application Default Credentials missing. "
                            + "Local: run `gcloud auth application-default login`. "
                            + "Prod: attach a service account with Billing Viewer + BigQuery Job User/Data Viewer. "
                            + ex.getMessage(),
                    ex);
        }
    }
}
