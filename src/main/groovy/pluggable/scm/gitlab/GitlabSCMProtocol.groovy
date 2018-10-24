package pluggable.scm.gitlab;

/**
 Enumeration of the protocols supported by GitLab
 */
enum GitlabSCMProtocol {
    SSH("ssh"),
    HTTP("http"),
    HTTPS("https")

    private final String protocol = "";

    /**
     * Constructor for class GitlabSCMProtocol
     *
     * @param protocol a string representation of the protocol e.g. ssh, https
     */
    public GitlabSCMProtocol(String protocol) {
        this.protocol = protocol
    }

    /**
     * Return a string representation of the SCM protocol.
     * @return a string representation of the SCM protocol.
     */
    @Override
    public String toString() {
        return this.protocol;
    }
}