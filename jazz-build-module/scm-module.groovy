#!groovy?
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import groovy.transform.Field

/*
* Logic for configuring projects in the user's preferred scm
*/

@Field def scm
@Field def repo_base

def initialize(scm, repo_base){
  setScm(scm)
  setRepoBase(repo_base)
}

def setSCMBase(){
	def scm_base = repo_base + "/"
	if (scm == 'bitbucket'){
		def scm_base = scm_base + "scm/"
	}
  return scm_base
}

def setRepoUrl(repo_protocol, scm_repo_name){
	def repo_url = repo_protocol
	if (scm == "gitlab"){
		repo_url = repo_url + scm_base + "/cas/" + scm_repo_name
	}else{
		repo_url = repo_url + repo_base + "/projects/CAS/repos/" + scm_repo_name + "/browse";
	}
	return repo_url
}

def createProjectInSCM(gitlab_private_token, repo_owner, scm_repo_name, cas_rest_repo, repo_loc, username, password){
  try{
  	if(scm == "gitlab"){
  		def git_username
      def cas_proj_id
      def cas_repo_id
      //assuming usernames for now are a concatination of the user's email, check platform_usermanagement index.js
  		if(repo_owner.contains("@")){
  			git_username = repo_owner.substring(0, owner.indexOf("@"))
  		}
  		def user_id = getGitlabUserId(git_username, gitlab_private_token)
  		def gitlab_repo_output = sh (
  			script: "curl --header \"Private-Token: $gitlab_private_token\" -X POST \"http://$repo_base/api/v3/projects/user/$user_id?name=$scm_repo_name&path=$scm_repo_name&visibility=private&request_access_enabled=true\"",
  			returnStdout: true
  		).trim()

  		def jsonSlurper = new JsonSlurper()
  		def object = jsonSlurper.parseText(gitlab_repo_output)
      if(object == null || object.equals("") || object.id == null || object.id.equals("")){
				error "new project creation failed"
			}
			cas_proj_id = object.id

      cas_repo_id = getCasRepoId(gitlab_private_token, repo_loc)

  		transferProject(cas_repo_id, cas_proj_id)
  	}
  	else if(scm == "bitbucket"){
  		sh "curl -X POST -k -v -u \"$username:$password\" -H \"Content-Type: application/json\" " + cas_rest_repo + " -d \'{\"name\":\""+ scm_repo_name +"\", \"scmId\": \"git\", \"forkable\": \"true\"}\'"
  	}
  }catch (ex) {
		if(!((ex.getMessage()).indexOf("groovy.json.internal.LazyMap") > -1)) {
			echo "createUserInSCM Failed"
			error "createProjectInSCM Failed. "+ex.getMessage()
		}
	}
}

def getGitlabUserId(gitlab_username, gitlab_private_token){
  try{
    def output = sh (
  		script: "curl --header \"Private-Token: $gitlab_private_token\" -X GET \"http://$repo_base/api/v3/users?username=$gitlab_username\"",
  		returnStdout: true
  	).trim()
  	def jsonSlurper = new JsonSlurper()
  	def userObject = jsonSlurper.parseText(output)
    if(userObject == null || userObject.equals("") || userObject[0] == null || userObject[0].equals("") ||
				userObject[0].id == null || userObject[0].id.equals("")){
			error "get user data in gitlab failed"
		}
  	return userObject[0].id
  }
  catch (ex) {
		if(!((ex.getMessage()).indexOf("groovy.json.internal.LazyMap") > -1)) {
			echo "getGitlabUserId Failed"
			error "getGitlabUserId Failed. "+ex.getMessage()
		}
	}
}

def getCasRepoId(gitlab_private_token, repo_loc){
  try{
    def output = sh (
  		script: "curl --header \"Private-Token: $gitlab_private_token\" -X GET \"http://$repo_base/api/v3/groups?search=$repo_loc\"",
  		returnStdout: true
  	).trim()
  	def jsonSlurper = new JsonSlurper()
  	def groupObject = jsonSlurper.parseText(output)
    if(groupObject == null || groupObject.equals("") || groupObject[0] == null || groupObject[0].equals("") ||
				groupObject[0].id == null || groupObject[0].id.equals("")){
					error "get cas group data failed"
		}
  	return groupObject[0].id
  }catch (ex) {
		if(!((ex.getMessage()).indexOf("groovy.json.internal.LazyMap") > -1)) {
			echo "getCasRepoId Failed"
			error "getCasRepoId Failed. "+ex.getMessage()
		}
	}
}

def transferProject(gitlab_private_token, cas_id, project_id){
  	def output = sh (
  		script: "curl --header \"Private-Token: $gitlab_private_token\" -X POST \"http://$repo_base/api/v3/groups/$cas_id/projects/$project_id\"",
  		returnStdout: true
  	).trim()
}

/**
 * Delete the project repository from scm
 * @param  scm_repo_name
 * @return
 */
def deleteProject(gitlab_private_token, repo_loc, scm_repo_name, cas_rest_repo, username, password) {
  if(scm == "gitlab"){
    def encodedProjectPath = repo_loc + "%2F" + scm_repo_name
    def gitlab_repo_output = sh (
      script: "curl --header \"Private-Token: $gitlab_private_token\" -X POST \"http://$repo_base/api/v3/projects/$encodedProjectPath\"",
      returnStdout: true
    ).trim()
  }
  else if(scm == "bitbucket"){
    def repourl = cas_rest_repo + scm_repo_name;
		def outputStr = sh (
			script: "curl -X DELETE -k -u \"$username:$password\" '" + cas_rest_repo + scm_repo_name +"'" ,
			returnStdout: true
		).trim()
  }
}


//setters start
def setScm(paramScm){
  scm = paramScm
}

def setRepoBase(paramRepoBase){
  repo_base = paramRepoBase
}

return this
