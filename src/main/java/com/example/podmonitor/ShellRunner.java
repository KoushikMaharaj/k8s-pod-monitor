package com.example.podmonitor;

import java.io.*;

/**
 * Runs a shell command via cmd.exe and returns stdout + stderr combined.
 */
public class ShellRunner {

    public static String run(String cmd) throws Exception {
        Logger.debug("ShellRunner executing: {}", cmd);

        ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", cmd);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        Logger.debug("ShellRunner exit code: {} for cmd: {}", exitCode, cmd);

        return output.toString();
    }
}
