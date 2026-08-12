package com.sandy.expense;

import java.net.URI;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ExpenseApprovalApplication {

    public static void main(String[] args) {
        applyDatabaseUrl();
        SpringApplication.run(ExpenseApprovalApplication.class, args);
    }

    /**
     * Fly.io / Heroku inject a single {@code DATABASE_URL} of the form
     * {@code postgres://user:pass@host:5432/db}, which JDBC/Hikari can't use directly. If present,
     * split it into {@code spring.datasource.*} system properties (which override application.yml).
     * When absent (local dev), the {@code DB_URL}/{@code DB_USER}/{@code DB_PASSWORD} defaults apply.
     */
    static void applyDatabaseUrl() {
        String raw = System.getenv("DATABASE_URL");
        if (raw == null || !(raw.startsWith("postgres://") || raw.startsWith("postgresql://"))) {
            return;
        }
        try {
            URI uri = new URI(raw);
            String[] creds = uri.getUserInfo() != null ? uri.getUserInfo().split(":", 2) : new String[] {"", ""};
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getPath();
            if (uri.getQuery() != null && !uri.getQuery().isBlank()) {
                jdbc += "?" + uri.getQuery();
            }
            System.setProperty("spring.datasource.url", jdbc);
            System.setProperty("spring.datasource.username", creds[0]);
            System.setProperty("spring.datasource.password", creds.length > 1 ? creds[1] : "");
        } catch (Exception e) {
            System.err.println("Could not parse DATABASE_URL; falling back to DB_URL config: " + e.getMessage());
        }
    }
}
