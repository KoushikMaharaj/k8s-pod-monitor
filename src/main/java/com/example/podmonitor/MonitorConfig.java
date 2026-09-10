package com.example.podmonitor;

/**
 * Config populated from ConfigDialog.
 * contextName is the kubectl context name used to switch before polling.
 */
public class MonitorConfig {

    // Kube context (as named in ~/.kube/config)
    public String contextName;  // e.g. "gke_my-project_us-central1_my-cluster"
    public String cluster;      // display only — read from kube config

    // Kubernetes target
    public String namespace;

    // Local paths
    public String logDir;

    // Polling
    public int intervalMs;

    // Alert filters
    public boolean alertCrash;
    public boolean alertError;
    public boolean alertPending;
    public boolean alertOOM;
    public boolean alertTerminating;

    /** Returns true if the given kubectl output line represents a bad pod. */
    public boolean isBadLine(String line) {
        return (alertCrash        && line.contains("CrashLoopBackOff")) ||
               (alertError        && line.contains("Error"))            ||
               (alertPending      && line.contains("Pending"))          ||
               (alertOOM          && line.contains("OOMKilled"))        ||
               (alertTerminating  && line.contains("Terminating"));
    }
}
