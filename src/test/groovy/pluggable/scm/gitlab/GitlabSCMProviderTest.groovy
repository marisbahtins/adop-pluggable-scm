package pluggable.scm.gitlab

public class GitlabSCMProviderTest extends GroovyTestCase {

    public void testGetScmUrlSSHWithoutContext() {
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")

        assertEquals "ssh://git@10.161.85.37:22/", gitlabSCMProvider.getScmUrl()
    }

    public void testGetScmUrlSSHWithContext() {
        //no context used for SSH cloning so it should be the same as testGetScmUrlSSHWithoutContext
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "gitLabServer", "customContext", GitlabSCMProtocol.HTTPS, "none", 80, "")

        assertEquals "ssh://git@gitLabServer:22/", gitlabSCMProvider.getScmUrl()
    }

    public void testGetScmUrlHTTPWithoutContext() {
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(80, GitlabSCMProtocol.HTTP, "server", "", GitlabSCMProtocol.HTTPS, "none", 80, "")

        assertEquals "http://server:80/", gitlabSCMProvider.getScmUrl()
    }

    public void testGetScmUrlHTTPWithContext() {
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(80, GitlabSCMProtocol.HTTP, "10.161.85.37", "gitlab", GitlabSCMProtocol.HTTPS, "none", 80, "")
        assertEquals "http://10.161.85.37:80/gitlab/", gitlabSCMProvider.getScmUrl()
    }

    public void testGetScmUrlHTTPSWithoutContext() {
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(443, GitlabSCMProtocol.HTTPS, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")

        assertEquals "https://10.161.85.37:443/", gitlabSCMProvider.getScmUrl()
    }

    public void testGetScmUrlHTTPSWithContext() {
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(443, GitlabSCMProtocol.HTTPS, "10.161.85.37", "gitlab", GitlabSCMProtocol.HTTPS, "none", 80, "")

        assertEquals "https://10.161.85.37:443/gitlab/", gitlabSCMProvider.getScmUrl()
    }

    public void testCreateGitlabScmObjectWithNullRequiredFieldProtocol() {
        String message = shouldFail(IllegalArgumentException) {
            GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(443, null, "10.161.85.37", "gitlab", GitlabSCMProtocol.HTTPS, "none", 80, "")
        }

        assertEquals message, GitlabSCMConstants.NULL_FIELD_EXCEPTION_MESSAGE
    }

    public void testCreateGitlabScmObjectWithEmptyRequiredFieldHost() {
        String message = shouldFail(IllegalArgumentException) {
            GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(443, GitlabSCMProtocol.HTTPS, "", "gitlab", GitlabSCMProtocol.HTTPS, "none", 80, "")
        }

        assertEquals message, GitlabSCMConstants.EMPTY_FIELD_EXCEPTION_MESSAGE
    }

    public void testCreateGitlabScmObjectWithNullRequiredFieldScmUser() {
        String message = shouldFail(IllegalArgumentException) {
            GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        }

        assertEquals message, GitlabSCMConstants.EMPTY_FIELD_EXCEPTION_MESSAGE
    }

    public void testCreateGitlabScmObjectWithInvalidRequiredFieldPort() {
        String message = shouldFail(IllegalArgumentException) {
            GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(33, GitlabSCMProtocol.HTTPS, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 0, "")
        }

        assertTrue((message.contains(GitlabSCMConstants.INVALID_PORT_EXCEPTION_MESSAGE)))
    }

    public void testExtractRepoNameFromGithubUrl(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://github.com/Accenture/adop-cartridge-java-environment-template.git"
        String result = gitlabSCMProvider.extractRepoNameFromUrl(input)

        assertEquals(result, "adop-cartridge-java-environment-template")
    }

    public void testExtractRepoNameFromGitlabUrl(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://gitlab.domain.com/testUser/sample-repo.git"
        String result = gitlabSCMProvider.extractRepoNameFromUrl(input)

        assertEquals(result, "sample-repo")
    }

    public void testExtractRepoNameFromGerritUrl(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "http://adopCuser@10.161.85.37/gerrit/gerrit-repo"
        String result = gitlabSCMProvider.extractRepoNameFromUrl(input)

        assertEquals(result, "gerrit-repo")
    }

    public  void testValidateValidHTTPRepo(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "http://adopCuser@10.161.85.37/gerrit/gerrit-repo"

        gitlabSCMProvider.validateRepos(Arrays.asList([input]))
    }

    public  void testValidateValidHTTPSRepo(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://gitlab.domain.com/testUser/sample-repo.git"

        gitlabSCMProvider.validateRepos(Arrays.asList([input]))
    }

    public  void testValidateInValidRepoProtocol() {
        String input = "ssth://gitlab.domain.com/testUser/sample-repo.git"
        String message = shouldFail(IllegalArgumentException) {
            GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
            gitlabSCMProvider.validateRepos(Arrays.asList([input]))
        }

        assertEquals message, GitlabSCMConstants.INVALID_PROTOCOL_IN_URLS_FILE_EXCEPTION_MESSAGE
    }

    public void testIsGerritUrlValidGerritUrlHttp(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://gerrit.googlesource.com/testRepo"
        
        assertTrue(gitlabSCMProvider.isGerritUrl(input))
    }

    public void testIsGerritUrlValidGerritUrlHttps(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://gerrit.googlesource.com/testRepo"
        
        assertTrue(gitlabSCMProvider.isGerritUrl(input))
    }

    public void testIsGerritUrlInvalidGerritUrlHttp(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://gerrit.googlesource.com/testRepo.git"
        
        assertFalse gitlabSCMProvider.isGerritUrl(input)
    }

    public void testIsGerritUrlInvalidGerritUrlHttps(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://gerrit.googlesource.com/testRepo.git"
        
        assertFalse gitlabSCMProvider.isGerritUrl(input)
    }

    public void testIsGerritUrlValidUrlContainsGerritContext(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://10.0.0.1/gerrit/"
        
        assertTrue gitlabSCMProvider.isGerritUrl(input)
    }

    public void testIsGerritUrlValidUrlDoesNotContainGerritContext(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://10.0.0.1/ggerrit/"
        
        assertFalse gitlabSCMProvider.isGerritUrl(input)
    }

    public void testIsGerritUrlEmptyString(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = ""
        
        assertFalse gitlabSCMProvider.isGerritUrl(input)
    }

    public void testIsGerritUrlValidUrl(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "http://mydomain.gerrit.com"
        
        assertTrue gitlabSCMProvider.isGerritUrl(input)
    }

    public void testIsGerritUrlValidUrlBeginsWithGerrit(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://gerrit.mydomain.com"
        
        assertTrue gitlabSCMProvider.isGerritUrl(input)
    }

    public void testValidGithubUrlWhichContainsGerritName(){
        GitlabSCMProvider gitlabSCMProvider = new GitlabSCMProvider(22, GitlabSCMProtocol.SSH, "10.161.85.37", "", GitlabSCMProtocol.HTTPS, "none", 80, "")
        String input = "https://github.com/someUser/gerrit.git"  //github repository called Gerrit should not be identified as Gerrit
        
        assertFalse gitlabSCMProvider.isGerritUrl(input)
    }

}