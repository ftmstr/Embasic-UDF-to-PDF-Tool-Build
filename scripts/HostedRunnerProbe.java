/** Checks basic Java availability on a GitHub-hosted Windows runner. */
public final class HostedRunnerProbe {
    private HostedRunnerProbe() { }
    public static void main(String[] args) {
        if (Runtime.version().feature() < 17) {
            throw new IllegalStateException("JDK 17+ required");
        }
        System.out.println("WINDOWS HOSTED JAVA PROBE PASS (not an application test)");
    }
}
