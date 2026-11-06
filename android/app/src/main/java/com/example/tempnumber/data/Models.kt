package com.example.tempnumber.data

import kotlinx.serialization.Serializable

// --- Auth ---
@Serializable data class AuthUser(val id: String, val email: String)
@Serializable data class AuthResponse(val token: String, val user: AuthUser)
@Serializable data class RegisterRequest(val email: String, val password: String)
@Serializable data class LoginRequest(val email: String, val password: String)

// --- Numbers & messages ---
@Serializable
data class RentedNumber(
    val phoneNumber: String,
    val sid: String? = null,
    val ownerId: String? = null,
    val provisionedAt: String? = null,
)

@Serializable
data class AvailableNumber(
    val phoneNumber: String,
    val friendlyName: String? = null,
    val locality: String? = null,
    val region: String? = null,
)

@Serializable
data class SmsMessage(
    val id: String,
    val to: String,
    val from: String,
    val body: String,
    val receivedAt: String,
)

// --- Response envelopes ---
@Serializable data class NumbersResponse(val numbers: List<RentedNumber> = emptyList())
@Serializable data class AvailableNumbersResponse(val numbers: List<AvailableNumber> = emptyList())
@Serializable data class MessagesResponse(val messages: List<SmsMessage> = emptyList())

@Serializable data class RentRequest(val phoneNumber: String)
