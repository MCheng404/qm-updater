package com.qm.updater.service

import com.qm.updater.data.github.GitHubRelease
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface GitHubService {

    @GET("/repos/{user}/{repo}/releases")
    suspend fun getReleases(
        @Path("user") user: String = "rumboalla",
        @Path("repo") repo: String = "apkupdater",
        @Query("per_page") perPage: Int = 50
    ): List<GitHubRelease>

}
