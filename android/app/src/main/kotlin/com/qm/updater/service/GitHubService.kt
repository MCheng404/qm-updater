package com.qm.updater.service

import com.qm.updater.data.github.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Path

interface GitHubService {

    @GET("/repos/{user}/{repo}/releases")
    suspend fun getReleases(
        @Path("user") user: String = "rumboalla",
        @Path("repo") repo: String = "apkupdater"
    ): List<GitHubRelease>

}
