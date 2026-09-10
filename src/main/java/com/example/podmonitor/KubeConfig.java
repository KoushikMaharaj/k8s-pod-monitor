package com.example.podmonitor;

import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Parses ~/.kube/config using SnakeYAML and extracts context details.
 */
public class KubeConfig {

    public static class Context {
        public final String name;
        public final String cluster;
        public final String user;
        public final String namespace;

        Context(String name, String cluster, String user, String namespace) {
            this.name      = name;
            this.cluster   = cluster;
            this.user      = user;
            this.namespace = namespace;
        }

        @Override public String toString() { return name; }
    }

    public static List<Context> loadContexts() {
        Path path = resolveKubeConfigPath();
        if (path == null || !Files.exists(path)) {
            Logger.warn("kube config not found at: {}", path);
            return Collections.emptyList();
        }

        Logger.debug("Loading kube contexts from: {}", path);
        try (InputStream is = Files.newInputStream(path)) {
            Map<String, Object> root = new Yaml().load(is);
            if (root == null) return Collections.emptyList();

            List<Context> result = new ArrayList<>();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contexts =
                    (List<Map<String, Object>>) root.get("contexts");

            if (contexts == null) return Collections.emptyList();

            for (Map<String, Object> entry : contexts) {
                String name = (String) entry.get("name");

                @SuppressWarnings("unchecked")
                Map<String, Object> ctx = (Map<String, Object>) entry.get("context");

                String cluster   = ctx != null ? str(ctx.get("cluster"))   : "";
                String user      = ctx != null ? str(ctx.get("user"))      : "";
                String namespace = ctx != null ? str(ctx.get("namespace")) : "";

                if (name != null) result.add(new Context(name, cluster, user, namespace));
            }

            Logger.debug("Loaded {} kube contexts", result.size());
            return result;

        } catch (Exception e) {
            Logger.error("Failed to parse kube config", e);
            return Collections.emptyList();
        }
    }

    public static String currentContext() {
        Path path = resolveKubeConfigPath();
        if (path == null || !Files.exists(path)) return null;
        try (InputStream is = Files.newInputStream(path)) {
            Map<String, Object> root = new Yaml().load(is);
            String ctx = root != null ? str(root.get("current-context")) : null;
            Logger.debug("Current kube context: {}", ctx);
            return ctx;
        } catch (Exception e) {
            Logger.error("Failed to read current-context", e);
            return null;
        }
    }

    private static String str(Object o) {
        return o != null ? o.toString() : "";
    }

    private static Path resolveKubeConfigPath() {
        String env = System.getenv("KUBECONFIG");
        if (env != null && !env.isBlank()) {
            String first = env.split(File.pathSeparator)[0].trim();
            if (!first.isEmpty()) {
                Logger.debug("Using KUBECONFIG env var: {}", first);
                return Paths.get(first);
            }
        }
        return Paths.get(System.getProperty("user.home"), ".kube", "config");
    }
}
