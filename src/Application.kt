package com.example

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.client.request.header
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import io.ktor.auth.*
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.jackson.*
import io.ktor.features.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.get
import io.ktor.locations.*

fun main(args: Array<String>): Unit = io.ktor.server.netty.EngineMain.main(args)

private fun ApplicationCall.redirectUrl(path: String): String {
    val defaultPort = if (request.origin.scheme == "http") 80 else 443
    val hostPort = request.host() + request.port().let { port -> if (port == defaultPort) "" else ":$port" }
    val protocol = request.origin.scheme
    return "$protocol://$hostPort$path"
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    val loginProviders = OAuthServerSettings.OAuth2ServerSettings(
            name = "github",
            authorizeUrl = "https://github.com/login/oauth/authorize",
            accessTokenUrl = "https://github.com/login/oauth/access_token",
            clientId = "78553e2d7623c689434a",
            clientSecret = "3d97ec175bf7486014a61fd32c38d89aca4c908f"
        )

    install(Authentication) {
        oauth("gitHubOAuth") {
            client = HttpClient(Apache)
            providerLookup = { loginProviders }
            urlProvider = { redirectUrl("/login") }
        }
    }

    routing {
        get("/") {
            call.respondHtml {
                body {
                    form (action = "http://localhost:8080/login") {
                        button (type = ButtonType.submit) {
                            + "Sign in with GitHub"
                        }
                    }
                }
            }
        }

        authenticate("gitHubOAuth") {
            route("/login") {
                handle {
                    val principal = call.authentication.principal<OAuthAccessTokenResponse>()
                    if (principal == null) call.respondText ("Bad Request 400")
                    else {
                        val userToken = when (principal) {
                            is OAuthAccessTokenResponse.OAuth1a -> principal.token
                            is OAuthAccessTokenResponse.OAuth2 -> principal.accessToken
                        }
                        val userInfo = HttpClient(Apache).get<String>("https://api.github.com/user") {
                            header ("Authorization", "token $userToken")
                        }
                        val data = ObjectMapper().readValue<Map<String, Any?>>(userInfo)
                        val userLogin = data["login"] as String
                        call.respondText("$userLogin\nOK 200")
                    }
                }
            }
        }
    }
}

fun FlowOrMetaDataContent.styleCss(builder: CSSBuilder.() -> Unit) {
    style(type = ContentType.Text.CSS.toString()) {
        +CSSBuilder().apply(builder).toString()
    }
}

fun CommonAttributeGroupFacade.style(builder: CSSBuilder.() -> Unit) {
    this.style = CSSBuilder().apply(builder).toString().trim()
}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
