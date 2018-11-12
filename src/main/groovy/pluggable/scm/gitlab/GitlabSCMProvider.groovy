package pluggable.scm.gitlab

import groovy.json.JsonSlurper
import pluggable.configuration.EnvVarProperty
import pluggable.scm.SCMProvider
import pluggable.scm.helpers.ExecuteShellCommand
import pluggable.scm.helpers.Logger

/**
 Concrete implementation of SCMProvider interface for Gitlab
 */
class GitlabSCMProvider implements SCMProvider {

    private final GitlabSCMProtocol scmProtocol
    private final String scmHost
    private final int scmPort
    private String scmContext

    // Variables used to create repositories
    private GitlabSCMProtocol gitlabSCMProtocol
    private String gitlabEndpoint = ""
    private int gitlabPort
    private String internalEndpoint = "" // HTTP request endpoint to e.g create a new GitLab repo
    private String gitlabContext = ""

    /**
     * Constructor for class GitlabSCMProvider.
     * @param scmPort the scm port used when interacting with the scm provider to, for example clone e.g - HTTP, HTTPS or SSH
     * @param scmProtocol the clone protocol used when interacting with the scm provider to, for example clone e.g - HTTP, HTTPS or SSH
     * @param scmHost the clone url or host (for internal communication) when interacting with the scm provider e.g  10.0.0.1
     * @param scmContext GitLab url context when interacting with the scm provider e.g /gitlab in http://<ip_address>/gitlab/
     * @param gitlabSCMProtocol the cloning protocol used when performing cartridge loading operations - HTTP, HTTPS or SSH
     * @param gitlabEndpoint the url used when performing cartridge loading operations  e.g. 10.0.0.1
     * @param gitlabPort the port used when performing cartridge loading operations  e.g. 80
     * @param gitlabContext the url context used when performing cartridge loading operations  e.g /gitlab in http://<ip_address>/gitlab/
     * @throws IllegalArgumentException if a required field is null or empty
     */
    GitlabSCMProvider(int scmPort, GitlabSCMProtocol scmProtocol, String scmHost, String scmContext = "", GitlabSCMProtocol gitlabSCMProtocol, String gitlabEndpoint, int gitlabPort, String gitlabContext = "") {
        this.scmHost = scmHost
        this.scmPort = scmPort
        this.scmProtocol = scmProtocol
        this.scmContext = scmContext
        this.gitlabSCMProtocol = gitlabSCMProtocol
        this.gitlabEndpoint = gitlabEndpoint
        this.gitlabPort = gitlabPort
        this.gitlabContext = gitlabContext

        validate()
        internalEndpoint = "${this.gitlabSCMProtocol.toString()}://${this.gitlabEndpoint}${!this.gitlabContext.equals("") ? "/" + this.gitlabContext : ""}"
    }


    /**
     * Method which returns GitLab repository clone urls for http/https/ssh
     * @return SCM url for the provider.
     *     e.g. Gitlab-SSH  ssh://git@10.0.1.35:22/myproject/
     *          Gitlab-HTTP-with-context http://10.0.1.35:80/gitlab/myproject/
     *          Gitlab-HTTP-without-context http://10.0.1.35:80/myproject/
     *          Gitlab-HTTPS-with-context https://domain:443/gitlab/myproject/
     *          Gitlab-HTTPS-without-context https://domain:443/myproject/
     *
     * @throws IllegalArgumentException if the SCM protocol type is not supported.
     * */
    public String getScmUrl() {
        boolean isSSH = this.scmProtocol == GitlabSCMProtocol.SSH
        StringBuffer common = new StringBuffer("${this.scmProtocol.toString()}://") // ssh://, https:// or http://
        String lastCharacter = this.scmContext.equals("") ? "" : "/" // if there is no context, no need to have / at the end to avoid e.g http://10.0.0.0:80//

        return isSSH ? common.append("git@${this.scmHost}:${this.scmPort}/").toString()
                     : common.append("${this.scmHost}:${this.scmPort}/${this.scmContext}${lastCharacter}").toString()
    }


    /**
     * Return SCM section closuer for Jenkins DSL
     * @param projectName name of the project.
     * @param repoName name of the repository to clone.
     * @param branchName name of branch.
     * @param credentialId name of the credential in the Jenkins credential manager to use.
     * @param extras extra closures to add to the SCM section.
     * @return a closure representation of the SCM providers SCM section.
     */
    @Override
    Closure get(String projectName, String repoName, String branchName, String credentialId, Closure extras) {
        if (extras == null) extras = {}
        return {
            git extras >> {
                remote {
                    url(this.getScmUrl() + projectName + "/" + repoName + ".git")
                    credentials(credentialId)
                }

                branch(branchName)
            }
        }
    }


    /**
     *  Return a closure representation of the SCM section for multibranch pipelines (Jenkins DSL).
     * @param projectName  name of the project.
     * @param repoName  name of the repository to checkout.
     * @param credentialId  name of the credential in the Jenkins credential manager to use.
     * @param extras  extra closures to add to the multibranch SCM section.
     * @return a closure representation of the SCM providers multibranch SCM section.
     * */
    @Override
    public Closure getMultibranch(String projectName, String repoName, String credentialId, Closure extras) {
        if (extras == null) extras = {}
        return {
            git extras >> {
                remote(this.getScmUrl() + projectName + "/" + repoName + ".git")
                credentialsId(credentialId)
            }
        }
    }


    /**
     * Returns a closure representation of the SCM provider's trigger SCM section for Jenkins DSL.
     * This particular SCM provider only requires a branch name as manual configuration is needed on the repository's side to integrate it with the desired Jenkins job via a webhook
     * @param projectName  project name.
     * @param repoName  repository name.
     * @param branchName branch name to trigger.
     * @return a closure representation of the SCM providers trigger SCM section.
     */
    @Override
    Closure trigger(String projectName, String repoName, String branchName) {
        return {
            gitlabPush {
                buildOnMergeRequestEvents(true)
                buildOnPushEvents(true)
                enableCiSkip(false)
                setBuildDescription(false)
                rebuildOpenMergeRequest('never')
                includeBranches(branchName)
            }
        }
    }


    /**
     * Gets a list of repositories from urls.txt, validates them and then calls forkRepositoriesFromUrls to fork each repository in GitLab
     * If urls is empty, non readable or does not exist then exit without doing anything
     * @param workspace the current workspace
     * @param repoNamespace the current namespace (called group namespace in GitLab)
     * @param codeReviewEnabled not used in GitLab
     * @param overwriteRepos value of the overwriteRepos repos checkbox in jenkins. Can be "true" or "false"
     * @return nothing
     */
    @Override
    void createScmRepos(String workspace, String repoNamespace, String codeReviewEnabled, String overwriteRepos) {
        String cartHome = "/cartridge"
        String urlsFilePath = workspace + cartHome + "/src/urls.txt"
        List<String> repos = new ArrayList<>()
        File urlsFile = new File(urlsFilePath)

        // check that urls.txt exists, is readable and not empty
        if (!urlsTxtPermissionsAndPropertiesAreOk(urlsFile, urlsFilePath)){
            return
        }

        // get each line from urls.txt and add it to a list
        urlsFile.readLines().findAll({ it -> !it.isEmpty() }).each { it -> repos.add(it) }

        // make sure that the protocols are correct e.g SSH is not currently supported
        validateRepos(repos)

        // Create a group (if it does not exist) for the repositories and fork each repository.
        // If the group with that namespace already exists overwrite repositories with the same name there
        // only if overwriteRepos is set to true
        forkRepositoriesFromUrls(repos, workspace, overwriteRepos, repoNamespace)
    }


    /**
     * Given a list of public repositories from urls.txt, this method forks each of them into
     * a GitLab group (common GitLab repository namespace e.g /namespace/repo1, /namespace/repo2)
     * If the group does not already exist then it will be created.
     * If the group already exists and a repository with the same name as one of the repos in urls.txt
     * also exists then only overwrite the repo it if overwriteRepos is set to true
     * @param repos a list of public repositories to fork from urls.txt
     * @param workspace the current workspace
     * @param overwriteRepos value of the overwrite repos checkbox in jenkins.
     * @param namespace the Group namespace e.g myrepos for myrepos/reponame
     * @return nothing
     * @throws Exception if in search for a group more than one is found or if a group which was looked for and found
     * cannot be found later
     */
    private void forkRepositoriesFromUrls(List<String> repos, String workspace, String overwriteRepos, String namespace) {
        EnvVarProperty envVarProperty = EnvVarProperty.getInstance()
        int groupNamespaceId = -1
        boolean groupExists = GitlabRequestUtil.groupExists(internalEndpoint, namespace, envVarProperty.getProperty("GITLAB_TOKEN_VALUE"))

        // If a group with the given namespace exists, query the GitLab API to get its id. If it doesn't, create it and get its id
        if (groupExists) {
            Logger.info("Group ${namespace} already exists" )
            def response = GitlabRequestUtil.getGroupInfo(internalEndpoint, namespace, envVarProperty.getProperty("GITLAB_TOKEN_VALUE"))
            validateResponseToGetGroupRequest(response, namespace) // check that the response contains no more or less than a single element
            groupNamespaceId = response[0]."id" // get the group namespace id. There can only be a list with one element if the group exists
            Logger.info("Its group id is ${groupNamespaceId}")
        }
        else {
            String response = GitlabRequestUtil.createGroup(internalEndpoint, namespace, envVarProperty.getProperty("GITLAB_TOKEN_VALUE"))
            groupNamespaceId = new JsonSlurper().parseText(response)."id"
        }

        if (groupNamespaceId == -1) { // if it is still the default value then raise an exception
            throw new Exception("Error extracting group namespace id for group ${namespace}")
        }

        List existingProjectsInGroup = GitlabRequestUtil.getAllRepositoriesInGroup(internalEndpoint, namespace, envVarProperty.getProperty("GITLAB_TOKEN_VALUE"))

        // fork each repository to GitLab
        processRepositories(repos, existingProjectsInGroup, groupNamespaceId, workspace, namespace, overwriteRepos)
    }


    /**
     * Given a list of repositories from urls.txt and a list of repositories which exist under a group,
     * this method forks each repository in urls.txt to GitLab if it does not exist.
     * If it does exist, it overwrites it if overwriteRepos in Jenkins was set to true
     * @param repos a list of public repositories to fork
     * @param existingProjectsInGroup A list of all the repositories that already exist in a given GitLab namespace
     * @param groupNamespaceId the id of the group which is needed for API requests on that group
     * @param workspace the current workspace
     * @param namespace the GitLab group namespace
     * @param overwriteRepos true if the box overwriteRepos in Jenkins was ticked, false otherwise
     * @return nothing
     */
    private void processRepositories(List repos, List existingProjectsInGroup, int groupNamespaceId, String workspace, String namespace, String overwriteRepos) {
        EnvVarProperty envVarProperty = EnvVarProperty.getInstance()

        for (String repo : repos) {
            String repoName = extractRepoNameFromUrl(repo)
            if (!repositoryAlreadyExistsInGroup(existingProjectsInGroup, repoName)) {
                Logger.info("Creating repository  ${repoName}")
                GitlabRequestUtil.createRepositoryInGroup(internalEndpoint, repoName, groupNamespaceId, envVarProperty.getProperty("GITLAB_TOKEN_VALUE"))
                copyDataFromRepositoryInUrlsTxt(workspace, repoName, namespace, repo) // copy the data from the corresponding repository in urls.txt
            }
            else {
                if (overwriteRepos.equals("true")){
                    deleteAndRecreateRepository(repoName, existingProjectsInGroup, groupNamespaceId)
                    copyDataFromRepositoryInUrlsTxt(workspace, repoName, namespace, repo) // copy the data from the corresponding repository in urls.txt
                    continue
                }

                Logger.info("Repository ${repoName} already exists - but since overwrite repositories was not checked it was not overwritten")
            }
        }
    }


    /**
     * Checks that urls.txt exists, is readable and non empty
     * @param urlsFile the file as an object
     * @param urlsFilePath the path to the file
     * @return true if urls.txt exists, is readable and non empty, false otherwise
     */
    private boolean urlsTxtPermissionsAndPropertiesAreOk(File urlsFile, String urlsFilePath){

        if (!urlsFile.exists()) {
            Logger.info("urls.txt cannot be found at: " + urlsFilePath)
            return false
        }

        if (!urlsFile.canRead()) {
            Logger.info("urls.txt is present at " + urlsFilePath + " but the program is unable to read it")
            return false
        }
 
        boolean allLinesAreEmpty = urlsFile.readLines().findAll({ it -> !it.isEmpty() }).isEmpty()
        
        if (allLinesAreEmpty) {
            Logger.info("urls.txt is present but empty")
            return false
        }

        return true
    }

    /**
     * Deletes a GitLab repository and recreates it
     * @param repoName the repository name
     * @param existingProjectsInGroup list of existing repositories in that group - used to get the ID of
     * the repository to be deleted
     * @param groupNamespaceId the id of the group which is used when recreating the repository in that group
     * @return nothing
     */
    private void deleteAndRecreateRepository(String repoName, List existingProjectsInGroup, int groupNamespaceId) {
        Logger.info("Repository ${repoName} already exists - deleting")
        def repoId = getRepoId(existingProjectsInGroup, repoName)
        Logger.info("The repository ID of the existing repository is ${repoId}")
        EnvVarProperty envVarProperty = EnvVarProperty.getInstance()

        GitlabRequestUtil.deleteRepoById(internalEndpoint, repoId, envVarProperty.getProperty("GITLAB_TOKEN_VALUE"))
        sleep(1000) // the api requires a slight delay before we recreate the same repository
        Logger.info("Creating repository ${repoName}")
        GitlabRequestUtil.createRepositoryInGroup(internalEndpoint, repoName, groupNamespaceId, envVarProperty.getProperty("GITLAB_TOKEN_VALUE"))
    }


    /**
     * Clone a repository from GitLab and populate it by pulling the data from the corresponding repository in urls.txt
     * FIXME clone using SSH or in a way in which the access token is not stored in .git/config - fine for now as this is for evaluation purposes
     * @param workspace the current workspace
     * @param repoName the repository name
     * @param namespace the GitLab group namespace
     * @param repoFromUrlsTxt the clone url of the repository from urls.txt
     * @return nothing
     */
    private void copyDataFromRepositoryInUrlsTxt(String workspace, String repoName, String namespace, String repoFromUrlsTxt) {
        EnvVarProperty envVarProperty = EnvVarProperty.getInstance()
        ExecuteShellCommand com = new ExecuteShellCommand()
        String tempDir = workspace + "/tmp"
        Logger.info("Copying over the data to ${repoName} from ${repoFromUrlsTxt}")
        def tempScript = new File(tempDir + '/shell_script.sh')

        // can clone using username and access token instead of username and password e.g http://<user_name>:<access_token>@10.161.85.37:80/gitlab/ADOP-C/test.git
        tempScript << "git clone " + "${gitlabSCMProtocol}://${envVarProperty.getProperty("GITLAB_TOKEN_USER")}:${envVarProperty.getProperty("GITLAB_TOKEN_VALUE")}@${this.gitlabEndpoint}:${this.gitlabPort}/${this.gitlabContext == "" ? "" : this.gitlabContext + "/"}${namespace}/${repoName}.git " + tempDir + "/" + repoName + "\n"
        def gitDir = "--git-dir=" + tempDir + "/" + repoName + "/.git"
        tempScript << "git " + gitDir + " remote add source " + repoFromUrlsTxt + "\n"
        tempScript << "git " + gitDir + " fetch source" + "\n"
        tempScript << "git " + gitDir + " push origin +refs/remotes/source/*:refs/heads/*\n"
        com.executeCommand('chmod +x ' + tempDir + '/shell_script.sh')
        com.executeCommand(tempDir + '/shell_script.sh')
        new File(tempDir).delete()
    }


    /**
     * Validates that all the fields required to create an object out of this class are not null or empty
     * @return nothing
     * @throws IllegalArgumentException if a field is null or empty
     */
    private void validate() {

        if (scmContext == null) {
            scmContext = ""
        }

        if (this.gitlabContext == null) {
            Logger.info("There is no context set for gitlab in the properties file.  If GitLab is behind a context such as /gitlab there may be connection issues")
            gitlabContext = ""
        }

        def possiblyNullRequiredFields = [scmProtocol, scmHost, gitlabSCMProtocol, gitlabEndpoint].findAll({ it -> it == null }).collect()
        if (!possiblyNullRequiredFields.isEmpty()) {
            throw new IllegalArgumentException(GitlabSCMConstants.NULL_FIELD_EXCEPTION_MESSAGE)
        }

        def possiblyEmptyRequiredFields = [scmHost, gitlabEndpoint].findAll({ it -> it == "" }).collect()
        if (!possiblyEmptyRequiredFields.isEmpty()) {
            throw new IllegalArgumentException(GitlabSCMConstants.EMPTY_FIELD_EXCEPTION_MESSAGE)
        }

        def possiblyInvalidPorts = [scmPort, gitlabPort].findAll({ it -> (it <= 0 || it > 65535) }).collect()
        if (!possiblyInvalidPorts.isEmpty()) {
            throw new IllegalArgumentException(GitlabSCMConstants.INVALID_PORT_EXCEPTION_MESSAGE + ": " + possiblyInvalidPorts)
        }
    }


    /**
     * Extracts the repository name from an HTTP git repository url
     * e.g for https://github.com/Accenture/adop-cartridge-java-environment-template.git return adop-cartridge-java-environment-template
     * Gerrit repository names will not be extracted from Gerrit urls unless  a substring /gerrit/, http(s)/gerrit or .gerrit. exists
     * @param url git clone repository url
     * @return repository name
     */
    private String extractRepoNameFromUrl(String url) {
        String result = null
        boolean gerritUrl = isGerritUrl(url)

        if (gerritUrl) {
            println "Gerrit url found in urls.txt"
            return url.substring(url.lastIndexOf("/") + 1, url.length())
        }

        if (!url.contains(".git")) {
           throw new IllegalArgumentException("The url provided ${url} does not end with .git")
        }

        result = url.split("/").findAll({ it.contains(".git") })
                               .collect({ it.replace(".git", "") })
                               .get(0)

        return result
    }


    /**
     * Given a list of repositories, this method validates each of them by calling the validate method
     * @param repos a list of repositories to validate
     * @return Nothing
     * @throws IllegalArgumentException if the repositories list is empty
     */
    private void validateRepos(List<String> repos) {

        if (repos.isEmpty()) {
            throw new IllegalArgumentException("Something went wrong: urls.txt was present and not empty but the contents could not be parsed")
        }

        repos.each { it -> validateRepo(it) }
    }

    /**
     * Validates a repository clone URL by checking  the protocol against the GitLabSCM protocol enum (SSH is not allowed at the moment)
     * @param repo repository name
     * @throws IllegalArgumentException if a a repository is invalid
     */
    private void validateRepo(String repo) {
        GitlabSCMProtocol[] protocols = GitlabSCMProtocol.values()

        if (repo.contains("ssh://")) {
            throw new IllegalArgumentException("The only protocols supported are: http and https. Found: SSH")
        }

        for (GitlabSCMProtocol protocol : protocols) {
            if (repo.contains(protocol.toString() + "://")) {
                return
            }
        }

        throw new IllegalArgumentException(GitlabSCMConstants.INVALID_PROTOCOL_IN_URLS_FILE_EXCEPTION_MESSAGE)
    }


    /**
     * Given a list of repository names and a target repository name, it checks that the list contains the target repository name
     * @param listOfRepos list of Repositories
     * @param targetRepoName the repository name to be searched for in the list
     * @return true if the repository exists, false otherwise
     */
    private boolean repositoryAlreadyExistsInGroup(List listOfRepos, String targetRepoName) {
        return listOfRepos.find({ it -> it.name.equals(targetRepoName) }) == null ? false : true
    }


    /**
     * Given a list of repository objects parsed from JSON it looks for a repository with a particular name and if it finds it, it extracts its id
     * @param listOfExistingRepos list of Repository objects
     * @param targetRepoName the repository name to be searched for in the list
     * @return id of the target repository
     * @throws IllegalArgumentException if the target repository is not in the list
     * @throws Exception if the target repository is found but an id cannot be extracted
     */
    private int getRepoId(List listOfExistingRepos, String targetRepoName) {
        def targetRepo = listOfExistingRepos.find({ it -> it.name.equals(targetRepoName) })

        if (targetRepo == null) {
            throw new IllegalArgumentException("Tried to get repo info from a list of repositories but the repo was not in the list")
        }

        int repoId = targetRepo.id

        if (repoId.equals("") || repoId == null) {
            throw new Exception("The program was not able to extract a project id from repo ${targetRepoName}. Tried to extract a project id from \n${targetRepo}")
        }

        return repoId
    }


    /**
     * Checks whether or not a clone repository url is for Gerrit.
     * It only captures the simplest cases where the string does not contain .git and one or  of the substrings
     * /gerrit/, http(s)/gerrit .gerrit. are present
     * @param url the repository url to be checked
     * @return true if a substring /gerrit/, http(s)/gerrit or .gerrit. exists and false otherwise
     */
    public boolean isGerritUrl(String url) {
        return !url.contains(".git") && (url.contains("/gerrit/") || url.contains("https://gerrit.") || url.contains("http://gerrit.") || url.contains(".gerrit."))
    }


    /**
     * Validates the  response of an HTTP request to search for a group.
     * Makes sure that only one group was returned
     * @param response a list of JSON objects after parsing the response to the REST request
     * @param namespace the group namespace (common namespace for a collection of repositories e.g /namespace/repo1, /namespace/repo2
     * @throws Exception if more than one group or no groups were found
     */
    private String validateResponseToGetGroupRequest(List response, String namespace){
        if (response.size() == 0) {
            throw new Exception("A query for the repositories inside the group ${namespace} showed that the group exists but when tried to query  it the request was not processed")
        }
        else if (response.size() > 1) {
            throw new Exception("A query for the group ${namespace} showed that the group exists but when tried to get it more than one was found. There is probably an error in the code. Please raise an issue")
        }
    }
}
