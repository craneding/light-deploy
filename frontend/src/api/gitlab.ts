import request from '../utils/request'

export interface GitlabProject {
  id: number
  name: string
  name_with_namespace: string
  path: string
  path_with_namespace: string
  web_url: string
  description: string | null
}

/**
 * Fetch Gitlab projects using the saved token
 */
export function fetchGitlabProjects(search?: string, page: number = 1, perPage: number = 20) {
  return request.get('/gitlab/projects', {
    params: { 
      search,
      page,
      per_page: perPage
    }
  })
}

export function fetchGitlabBranches(projectId: number) {
  return request.get(`/gitlab/projects/${projectId}/branches`)
}

export function fetchGitlabTags(projectId: number) {
  return request.get(`/gitlab/projects/${projectId}/tags`)
}

export function fetchGitlabCommits(projectId: number) {
  return request.get(`/gitlab/projects/${projectId}/commits`)
}

