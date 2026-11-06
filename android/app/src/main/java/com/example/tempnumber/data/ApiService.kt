package com.example.tempnumber.data

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @GET("api/available-numbers")
    suspend fun availableNumbers(
        @Query("country") country: String = "US",
        @Query("areaCode") areaCode: String? = null,
    ): AvailableNumbersResponse

    @GET("api/numbers")
    suspend fun numbers(): NumbersResponse

    @POST("api/numbers")
    suspend fun rent(@Body body: RentRequest): RentedNumber

    @DELETE("api/numbers/{phoneNumber}")
    suspend fun release(@Path("phoneNumber") phoneNumber: String)

    @GET("api/messages")
    suspend fun messages(@Query("number") number: String? = null): MessagesResponse
}
