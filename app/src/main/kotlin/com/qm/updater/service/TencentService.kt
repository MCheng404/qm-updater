package com.qm.updater.service

import com.qm.updater.data.tencent.TencentRequest
import com.qm.updater.data.tencent.TencentResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface TencentService {

    /** 应用宝只提供逐包查询接口（实测不支持批量），调用方必须自行限流限量。 */
    @POST("wechat-apkinfo")
    suspend fun info(@Body body: TencentRequest): TencentResponse
}
