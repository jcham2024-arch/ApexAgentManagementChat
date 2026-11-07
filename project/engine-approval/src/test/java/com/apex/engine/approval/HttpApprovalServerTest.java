package com.apex.engine.approval;

import com.apex.engine.core.port.ApprovalPort;
import com.apex.engine.core.port.model.ApprovalDecision;
import com.apex.engine.core.port.model.ApprovalRequest;
import com.apex.engine.planner.RiskLevel;

import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpApprovalServerTest {

    @Test
    void approveDecisionReturnedAsJson() throws Exception {
        ApprovalPort port = request -> new ApprovalDecision.Approve(request.id(), "user-approved");
        HttpApprovalServer server = new HttpApprovalServer(0, port);
        try {
            server.start();
            int portNum = server.getPort();

            URL url = new URL("http://localhost:" + portNum + "/approve");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            String json = "{\"id\":\"req-1\",\"summary\":\"tool_call:run_command\",\"riskLevel\":\"L4\"}";
            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            assertThat(con.getResponseCode()).isEqualTo(200);
            String response = new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(response).contains("\"type\":\"approve\"");
            assertThat(response).contains("\"requestId\":\"req-1\"");
            assertThat(response).contains("\"comment\":\"user-approved\"");
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectDecisionReturnedAsJson() throws Exception {
        ApprovalPort port = request -> new ApprovalDecision.Reject(request.id(), "denied by policy");
        HttpApprovalServer server = new HttpApprovalServer(0, port);
        try {
            server.start();
            int portNum = server.getPort();

            URL url = new URL("http://localhost:" + portNum + "/approve");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            String json = "{\"id\":\"req-2\",\"summary\":\"tool_call:run_command\",\"riskLevel\":\"L4\"}";
            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            assertThat(con.getResponseCode()).isEqualTo(200);
            String response = new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(response).contains("\"type\":\"reject\"");
            assertThat(response).contains("\"requestId\":\"req-2\"");
            assertThat(response).contains("\"reason\":\"denied by policy\"");
        } finally {
            server.stop();
        }
    }

    @Test
    void modifyDecisionReturnedAsJson() throws Exception {
        ApprovalPort port = request -> new ApprovalDecision.Modify(request.id(), "{\"path\":\"/tmp\"}", "use tmp path");
        HttpApprovalServer server = new HttpApprovalServer(0, port);
        try {
            server.start();
            int portNum = server.getPort();

            URL url = new URL("http://localhost:" + portNum + "/approve");
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/json");
            con.setDoOutput(true);

            String json = "{\"id\":\"req-3\",\"summary\":\"tool_call:write_file\",\"riskLevel\":\"L2\"}";
            try (OutputStream os = con.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            assertThat(con.getResponseCode()).isEqualTo(200);
            String response = new String(con.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(response).contains("\"type\":\"modify\"");
            assertThat(response).contains("\"requestId\":\"req-3\"");
            assertThat(response).contains("\"modifiedArguments\":");
        } finally {
            server.stop();
        }
    }
}
