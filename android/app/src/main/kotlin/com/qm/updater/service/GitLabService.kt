package com.qm.updater.service

import com.qm.updater.data.gitlab.GitLabRelease
import retrofit2.http.GET
import retrofit2.http.Path


interface GitLabService {

    @GET("api/v4/projects/{user}%2F{repo}/releases")
    suspend fun getReleases(
        @Path("user") user: String,
        @Path("repo") repo: String
    ): List<GitLabRelease>

}
