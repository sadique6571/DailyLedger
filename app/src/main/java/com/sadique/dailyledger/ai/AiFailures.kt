package com.sadique.dailyledger.ai

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.ai.type.*
import com.google.firebase.auth.FirebaseAuthException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeoutException

internal enum class AiStage { SIGN_IN, APP_CHECK, GENERATION }

/** Fixed messages only: provider errors may contain submitted text or credentials. */
internal object AiFailures {
    fun describe(error: Exception, stage: AiStage = AiStage.GENERATION): AiException {
        if (error is AiException) return error
        val causes = generateSequence<Throwable>(error) { it.cause }.take(8).toList()
        fun has(test: (Throwable) -> Boolean) = causes.any(test)
        val message = when {
            has { it is RequestTimeoutException || it is SocketTimeoutException || it is TimeoutException } ->
                "The AI request timed out. Try again later. [AI_TIMEOUT]"
            has { it is FirebaseNetworkException || it is IOException } ->
                "Could not connect to the AI service. Check Wi-Fi/mobile data and try again. [AI_NETWORK]"
            has { it is FirebaseAuthException } || stage == AiStage.SIGN_IN ->
                "Firebase could not verify your sign-in. Please sign in again. [AI_SIGN_IN]"
            stage == AiStage.APP_CHECK ->
                "AI verification failed. Please try again later. [AI_APP_CHECK]"
            has { it is APINotConfiguredException || it is ServiceDisabledException } ->
                "AI is not enabled for this app yet. The app owner must finish the AI setup. [AI_SETUP]"
            has { it is InvalidAPIKeyException } ->
                "AI configuration is not accepted. The app owner must check the AI setup. [AI_CONFIG]"
            has { it is PermissionMissingException } ->
                "The AI service denied this request. Please try again later. [AI_ACCESS]"
            has { it is QuotaExceededException } ->
                "Gemini's usage limit is reached. Try later; manual entries and local insights still work. [AI_QUOTA]"
            has { it is UnsupportedUserLocationException } ->
                "Gemini is not available for this account or region. Manual entries and local insights still work. [AI_REGION]"
            has { it is PromptBlockedException || it is ContentBlockedException || it is ResponseStoppedException } ->
                "Gemini could not complete these entries. Try a short item-and-amount description. Nothing was saved. [AI_RESPONSE]"
            has { it is SerializationException } ->
                "Gemini returned an unreadable response. Nothing was saved. [AI_RESPONSE]"
            has { it is ServerException } ->
                "The AI service could not complete the request. Please try again later. [AI_SERVICE]"
            else -> "Gemini AI is unavailable right now. Nothing was saved. [AI_UNAVAILABLE]"
        }
        return AiException(message)
    }
}
