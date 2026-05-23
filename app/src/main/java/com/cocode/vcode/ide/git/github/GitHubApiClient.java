package com.cocode.vcode.ide.git.github;

import androidx.annotation.NonNull;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class GitHubApiClient {

    public static final String BASE_URL = "https://api.github.com";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 15000;
    private final String token;

    public GitHubApiClient(String token) {
        this.token = token;
    }

    // --- Core HTTP Engine ---

    private String request() throws IOException {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(BASE_URL + "/user");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "token " + token);
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "VCode-IDE");
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);

            StringBuilder sb = getStringBuilder(conn);

            return sb.toString();
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    @NonNull
    private StringBuilder getStringBuilder(HttpURLConnection conn) throws IOException {
        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300)
                ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        if (is != null) {
            try (BufferedReader reader = new BufferedReader(
                    new java.io.InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
            }
        }

        if (code == 401) throw new IOException("Authentication failed. Check your token.");
        if (code == 403) throw new IOException("Access forbidden. Token may lack required scopes.");
        if (code == 404) throw new IOException("User profile destination endpoint not found.");
        if (code >= 400) throw new IOException("GitHub API error " + code + ": " + sb);
        return sb;
    }

    // --- Public API ---

    public GitHubUser validateToken() throws IOException {
        String json = request();
        try {
            JSONObject obj = new JSONObject(json);
            return new GitHubUser(
                    obj.optString("login", ""),
                    obj.optString("name", ""),
                    obj.optString("email", ""),
                    obj.optString("avatar_url", ""),
                    obj.optInt("public_repos", 0),
                    obj.optInt("total_private_repos", 0)
            );
        } catch (Exception e) {
            throw new IOException("Failed to parse user response: " + e.getMessage());
        }
    }

    // --- Unified Data Model ---

    public static class GitHubUser {
        private final String login;
        private final String name;
        private final String email;
        private final String avatarUrl;
        private final int publicRepos;
        private final int totalPrivateRepos;

        public GitHubUser(String login, String name, String email, String avatarUrl, int publicRepos, int totalPrivateRepos) {
            this.login = login;
            this.name = name;
            this.email = email;
            this.avatarUrl = avatarUrl;
            this.publicRepos = publicRepos;
            this.totalPrivateRepos = totalPrivateRepos;
        }

        public String getLogin() { return login; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getAvatarUrl() { return avatarUrl; }
        public int getPublicRepos() { return publicRepos; }
        public int getTotalPrivateRepos() { return totalPrivateRepos; }
    }
}