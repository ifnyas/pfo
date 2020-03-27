package co.wangun.pfo

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.*

interface FaceApiService {

    @Multipart
    @POST("api/result")
    fun postResult(
        @Header("authorization") auth: String,
        @Part("username") username: String,
        @Part("lat") lat: String,
        @Part("lng") lng: String,
        @Part("confidence") confidence: String,
        @Part("status") status: Int,
        @Part img: MultipartBody.Part
    ): Call<ResponseBody>

    @Multipart
    @POST("api/registered-user")
    fun postUser(
        @Header("authorization") auth: String,
        @Part("username") username: String,
        @Part img: MultipartBody.Part
    ): Call<ResponseBody>

    @GET("api/registered-user/{username}")
    fun getUser(
        @Header("authorization") auth: String,
        @Path(value = "username", encoded = true) username: String
    ): Call<ResponseBody>
}