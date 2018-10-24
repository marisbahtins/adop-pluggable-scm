package pluggable.scm.gitlab

class GitlabSCMConstants {
    static final String NULL_FIELD_EXCEPTION_MESSAGE = "One or more of the fields provided [scmProtocol, scmHost, gitlabSCMProtocol, gitlabEndpoint] is null"
    static final String EMPTY_FIELD_EXCEPTION_MESSAGE = "One or more of the fields provided [scmHost, gitlabEndpoints] is empty"
    static final String INVALID_PORT_EXCEPTION_MESSAGE = "An invalid port was provided"
    static final String INVALID_PROTOCOL_IN_URLS_FILE_EXCEPTION_MESSAGE = "Unsupported protocol in one or more entries in  urls.txt. The only protocols supported are: http and https"
}
