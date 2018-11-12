package pluggable.scm.gitlab

import groovy.json.JsonSlurper
import pluggable.scm.helpers.Logger

class GitlabRequestUtil {

    /**
     * Calls the makeRequest method with parameters which adjust the HTTP request as to create a repository within a GitLab Group (namespace)
     * @param baseUrl GitLab base url e.g http://gitlab/gitlab or http://<ip address>/gitlab/ or http://<ip address>
     * @param repositoryName the name of the repository to be created
     * @param groupNamespaceId the id of the group namespace e.g 60
     * @param token the GitLab access token which allows API access
     * @throws Exception on non 2.x.x responses
     * @return the HTTP response or response code if the request was not successful
     */
    public static String createRepositoryInGroup(String baseUrl, String repositoryName, int groupNamespaceId, String token) {
        String endpoint = "${baseUrl}/api/v4/projects?name=${repositoryName}&namespace_id=${groupNamespaceId}"

        return makeRequest(endpoint, "POST", token)
    }

    /**
     * Calls the makeRequest method with parameters which adjust the HTTP request as to create a GitLab group (namespace for a collection of repositories)
     * @param baseUrl Gitlab base url e.g http://gitlab/gitlab or http://<ip address>/gitlab/ or http://<ip address>
     * @param group the group (namespace)
     * @param token the GitLab access token which allows API access
     * @throws Exception on non 2.x.x responses
     * @return the HTTP response or response code if the request was not successful
     */
    public static String createGroup(String baseUrl, String group, String token) {
        String endpoint = "${baseUrl}/api/v4/groups?name=${group}&path=${group}"

        return makeRequest(endpoint, "POST", token)
    }

    /**
     * Calls the makeRequest method with parameters which adjust the HTTP request as to check if a group already exists
     * @param baseUrl Gitlab base url e.g http://gitlab/gitlab or http://<ip address>/gitlab/ or http://<ip address>
     * @param group the group (namespace) to search for
     * @param token the GitLab access token which allows API access
     * @return true if the group exists, false otherwise
     */
    public static boolean groupExists(String baseUrl, String groupName, String token) {
        String endpoint = "${baseUrl}/api/v4/groups?search=${groupName}"

        return !makeRequest(endpoint, "GET", token).equals("[]")
    }

    /**
     * Calls the makeRequest method with parameters which adjust the HTTP request as to get all the information about a given group.
     * @param baseUrl Gitlab base url e.g http://gitlab/gitlab or http://<ip address>/gitlab/ or http://<ip address>
     * @param group the group (namespace) to search for
     * @param token the GitLab access token which allows API access
     * @throws Exception on non 2.x.x responses
     * @return a list of objects parsed from the JSON response
     */
    public static List getGroupInfo(String baseUrl, String groupName, String token) {
        String endpoint = "${baseUrl}/api/v4/groups?search=${groupName}"
        def response = makeRequest(endpoint, "GET", token)

        return response.equals("") ? Collections.EMPTY_LIST 
               : new JsonSlurper().parseText(response)
    }

    /**
     * Calls the makeRequest method with parameters which adjust the HTTP request as to return all the repositories that exist within a given group (namespace)
     * @param baseUrl Gitlab base url e.g http://gitlab/gitlab or http://<ip address>/gitlab/ or http://<ip address>
     * @param group the group (namespace)
     * @param token the GitLab access token which allows API access
     * @return a ist of Objects created by parsing the JSON response, an empty list if no repositories exist in the group
     */
    public static List getAllRepositoriesInGroup(String baseUrl, String groupName, String token) {
        String endpoint = "${baseUrl}/api/v4/groups/${java.net.URLEncoder.encode(groupName, "UTF-8")}/projects"
        def response = makeRequest(endpoint, "GET", token)

        return response.equals("") || response.equals("[]") ? Collections.EMPTY_LIST 
               : new JsonSlurper().parseText(response)
    }

    /**
     * Calls the makeRequest method with parameters which adjust the HTTP request as to delete a repository with a given id
     * @param baseUrl Gitlab base url e.g http://gitlab/gitlab or http://<ip address>/gitlab/ or http://<ip address>
     * @param repoId the id of the repository to be deleted
     * @param token the GitLab access token which allows API access
     * @throws Exception on non 2.x.x responses
     * @return the HTTP response or response code if the request was not successful
     */
    public static String deleteRepoById(String baseUrl, int repoId, String token) {
        String endpoint = "${baseUrl}/api/v4/projects/${repoId}"

        return makeRequest(endpoint, "DELETE", token)
    }

    /**
     * Performs an HTTP request with a Private-Token header and returns the response if the request returned a 2.x.x
     * response code and throws an exception otherwise
     * @param endpoint e.g http://10.161.85.37/gitlab/api/v4/projects/
     * @param method HTTP method e.g GET,POST, PUT
     * @param token the GitLab access token which allows API access
     * @return the response if the request returned a 2.x.x response, the response code otherwise
     */
    public static String makeRequest(String endpoint, String method, String token) {
        def url = new URL(endpoint)
        String response = ""
        int responseCode
        HttpURLConnection connection = (HttpURLConnection) url.openConnection()

        connection.with {
            requestMethod = "${method}"
            setRequestProperty("Private-Token", token)
            responseCode = connection.responseCode
            switch (responseCode) {
                case HTTP_OK:
                    response = content.text
                    break
                case HTTP_ACCEPTED:
                    response = content.text
                    break
                case HTTP_CREATED:
                    response = content.text
                    break
                case HTTP_NOT_AUTHORITATIVE:
                    response = content.text
                    break
                case HTTP_NO_CONTENT:
                    response = content.text
                    break
                case HTTP_RESET:
                    response = content.text
                    break
                case HTTP_PARTIAL:
                    response = content.text
                    break
                default:
                    Logger.info("Request returned ${responseCode} for request ${method}: ${endpoint}")
                    throw new Exception("HTTP request returned a non 2.x.x response code " + responseCode)
            }
        }
        
        return response
    }
}
