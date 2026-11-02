package com.visualtrace.ai.data.remote

import com.visualtrace.ai.data.remote.dto.FavoriteCreateDto
import com.visualtrace.ai.data.remote.dto.FavoriteDto
import com.visualtrace.ai.data.remote.dto.HealthDto
import com.visualtrace.ai.data.remote.dto.ProvidersDto
import com.visualtrace.ai.data.remote.dto.ReportDto
import com.visualtrace.ai.data.remote.dto.ResultsPageDto
import com.visualtrace.ai.data.remote.dto.SearchAcceptedDto
import com.visualtrace.ai.data.remote.dto.SearchDetailDto
import com.visualtrace.ai.data.remote.dto.TextSearchDto
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface VisualTraceApi {
    @Multipart
    @POST("api/v1/search/image")
    suspend fun searchImage(
        @Part file: MultipartBody.Part?,
        @Part("image_url") imageUrl: RequestBody?,
        @Part("providers") providers: RequestBody?,
        @Part("third_party_consent") thirdPartyConsent: RequestBody,
        @Part("query_text") queryText: RequestBody?,
        @Part("save_preview") savePreview: RequestBody,
    ): SearchAcceptedDto

    @Multipart
    @POST("api/v1/search/video")
    suspend fun searchVideo(
        @Part frames: List<MultipartBody.Part>,
        @Part video: MultipartBody.Part?,
        @Part("frame_timestamps_ms") frameTimestamps: RequestBody?,
        @Part("upload_consent") uploadConsent: RequestBody,
        @Part("frame_interval_seconds") frameInterval: RequestBody,
        @Part("max_duration_seconds") maxDuration: RequestBody,
        @Part("duration_ms") durationMs: RequestBody?,
        @Part("original_filename") originalFilename: RequestBody?,
        @Part("query_text") queryText: RequestBody?,
        @Part("providers") providers: RequestBody?,
        @Part("third_party_consent") thirdPartyConsent: RequestBody,
        @Part("save_preview") savePreview: RequestBody,
    ): SearchAcceptedDto

    @POST("api/v1/search/text")
    suspend fun searchText(@Body body: TextSearchDto): SearchAcceptedDto

    @GET("api/v1/search/{id}")
    suspend fun getSearch(@Path("id") id: String): SearchDetailDto

    @GET("api/v1/search/{id}/results")
    suspend fun getResults(
        @Path("id") id: String,
        @Query("filter") filter: String,
        @Query("sort") sort: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
    ): ResultsPageDto

    @POST("api/v1/results/{id}/report")
    suspend fun report(@Path("id") id: String, @Body body: ReportDto): Response<Unit>

    @DELETE("api/v1/history/{id}")
    suspend fun deleteHistory(@Path("id") id: String): Response<Unit>

    @DELETE("api/v1/history")
    suspend fun clearHistory(): Response<Unit>

    @GET("api/v1/providers")
    suspend fun providers(): ProvidersDto

    @GET("api/v1/health")
    suspend fun health(): HealthDto

    @GET("api/v1/favorites")
    suspend fun favorites(): List<FavoriteDto>

    @POST("api/v1/favorites")
    suspend fun createFavorite(@Body body: FavoriteCreateDto): FavoriteDto

    @DELETE("api/v1/favorites/{id}")
    suspend fun deleteFavorite(@Path("id") id: String): Response<Unit>

    @DELETE("api/v1/me")
    suspend fun deleteMe(): Response<Unit>
}
