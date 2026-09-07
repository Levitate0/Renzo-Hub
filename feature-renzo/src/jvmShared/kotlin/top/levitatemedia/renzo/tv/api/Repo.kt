package top.levitatemedia.renzo.tv.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Typed endpoint surface. One suspend function per server route the TV client
 * uses (catalog: /api prefix, all cookie-authed except version/login/setup).
 */
class Repo(val client: ApiClient) {

    // --- auth -------------------------------------------------------------
    suspend fun me(): MeResponse = client.get("/api/auth/me")

    suspend fun login(username: String, password: String, remember: Boolean = true): PublicUser {
        val body = buildJsonObject {
            put("username", username); put("password", password); put("remember", remember)
        }
        return client.post<LoginResponse>("/api/auth/login", body.toString()).user
    }

    /** Password-reset email. The server response is ALWAYS generic (no enumeration). */
    suspend fun forgot(username: String) {
        val body = buildJsonObject { put("username", username) }
        try { client.raw("/api/auth/forgot", "POST", body.toString()) } catch (_: Exception) { /* generic regardless */ }
    }

    suspend fun logout() { try { client.raw("/api/auth/logout", "POST") } catch (_: Exception) {} }

    // --- discover ---------------------------------------------------------
    suspend fun trending(): List<CardItem> = client.get("/api/discover/trending")
    suspend fun recommended(): List<CardItem> = client.get("/api/discover/recommended")
    suspend fun newSeason(): List<CardItem> = client.get("/api/discover/new-season")
    suspend fun search(q: String, type: String? = null): List<CardItem> {
        val t = if (type != null) "&type=$type" else ""
        return client.get("/api/discover/search?q=" + java.net.URLEncoder.encode(q, "UTF-8") + t)
    }
    suspend fun resolveMal(malId: Int): Int = client.get<ResolveResponse>("/api/titles/resolve?mal=$malId").id

    // --- library / feeds --------------------------------------------------
    suspend fun library(folder: String? = null, list: String? = null): List<CardItem> {
        val q = mutableListOf<String>()
        folder?.let { q.add("folder=" + java.net.URLEncoder.encode(it, "UTF-8")) }
        list?.let { q.add("list=" + java.net.URLEncoder.encode(it, "UTF-8")) }
        val qs = if (q.isEmpty()) "" else "?" + q.joinToString("&")
        return client.get("/api/library$qs")
    }
    suspend fun folders(): List<FolderInfo> = client.get("/api/folders")
    suspend fun createFolder(name: String) {
        val body = buildJsonObject { put("name", name) }
        client.raw("/api/folders", "POST", body.toString())
    }
    /** GET /api/lists — list name -> title count (web ListCounts). */
    suspend fun lists(): Map<String, Int> = client.get("/api/lists")
    suspend fun updates(): List<CardItem> = client.get("/api/updates")
    suspend fun jobs(): List<JobItem> = client.get("/api/jobs")
    suspend fun history(): List<CardItem> = client.get("/api/history")
    /** Pull AniList/MAL lists into the library (web library Import button). */
    suspend fun importTrackers(): ImportResult = client.post("/api/trackers/import")

    // --- downloads page (web /downloads/) ----------------------------------
    suspend fun autodlStatus(): AutodlStatus = client.get("/api/autodl/status")
    suspend fun autodlRun(): AutodlRunResult = client.post("/api/autodl/run")
    /** Re-search a failed episode download from scratch. */
    suspend fun retryEpisode(titleId: Int, ep: Int) { client.raw("/api/titles/$titleId/retry/$ep", "POST") }
    /** Move a queued download to the front of the line ("Download now"). */
    suspend fun prioritizeJob(id: String) { client.raw("/api/jobs/$id/prioritize", "POST") }
    suspend fun addToLibrary(id: Int): CardItem {
        val body = buildJsonObject { put("anilistId", id) }
        return client.post("/api/library", body.toString())
    }
    suspend fun removeFromLibrary(id: Int) { client.raw("/api/library/$id", "DELETE") }

    // --- title + playback -------------------------------------------------
    suspend fun title(id: Int): TitleDetail = client.get("/api/titles/$id")

    /** Toggle watchlist/favorites/custom list membership (hero icon pills). */
    suspend fun toggleList(id: Int, list: String, on: Boolean): ListToggleResult {
        val body = buildJsonObject { put("list", list); put("on", on) }
        return client.post("/api/titles/$id/lists", body.toString())
    }

    /** Queue every missing aired episode (hero ⬇ pill). Returns queued count. */
    suspend fun downloadSeason(id: Int): SeasonDownloadResult =
        client.post("/api/titles/$id/download-season")

    /** Background-download ONE episode; priority = jump the queue ("Download now"). */
    suspend fun downloadEpisode(id: Int, ep: Int, priority: Boolean = false) {
        val q = if (priority) "?priority=1" else ""
        client.raw("/api/titles/$id/download/$ep$q", "POST")
    }

    /** Hero "Auto: on/off" — applies to every season of the series. */
    suspend fun setAuto(id: Int, enabled: Boolean): AutoResult {
        val body = buildJsonObject { put("enabled", enabled) }
        return client.post("/api/titles/$id/auto", body.toString())
    }

    /** Hero folder picker — moves the whole season chain (and its files). */
    suspend fun setFolder(id: Int, folder: String): FolderResult {
        val body = buildJsonObject { put("folder", folder) }
        return client.post("/api/titles/$id/folder", body.toString())
    }

    suspend fun providers(id: Int): List<ProviderOption> = client.get("/api/titles/$id/providers")

    /** Pin a release group ("" = auto). */
    suspend fun setProvider(id: Int, group: String): ProviderResult {
        val body = buildJsonObject { put("group", group) }
        return client.post("/api/titles/$id/provider", body.toString())
    }

    /**
     * Parsed by hand rather than via [ApiClient.get] because the connected test
     * is key PRESENCE, and kotlinx.serialization cannot distinguish an absent
     * key from a present-null one — a distinction the server makes load-bearing
     * (`tracker.ts:272`: "key present+null = connected but failed; absent = not
     * connected").
     */
    private fun parseTracking(raw: String): Tracking {
        val obj = client.json.parseToJsonElement(raw).jsonObject
        fun entry(key: String): TrackEntry? =
            obj[key]?.takeIf { it !is JsonNull }?.let { client.json.decodeFromJsonElement(it) }
        return Tracking(
            anilist = entry("anilist"),
            mal = entry("mal"),
            anilistConnected = obj.containsKey("anilist"),
            malConnected = obj.containsKey("mal"),
        )
    }

    suspend fun tracking(id: Int): Tracking = parseTracking(client.raw("/api/titles/$id/tracking"))

    /**
     * Status must be the server's lowercase vocabulary (`tracker.ts:254`
     * TRACK_STATUSES) — the uppercase AniList enum is server-internal and is
     * silently rejected by the `isTrackStatus` guard at `api.ts:1131`, which
     * still answers 200.
     *
     * Note the server has no untrack path: `api.ts:1131` rejects the empty
     * string, so selecting "— Not tracked —" cannot clear a remote list entry.
     * That limitation is shared with the web, not Hub drift.
     */
    suspend fun setTracking(
        id: Int,
        status: String? = null,
        progress: Int? = null,
        score: Double? = null,
    ): Tracking {
        val body = buildJsonObject {
            if (status != null) put("status", status)
            if (progress != null) put("progress", progress)
            if (score != null) put("score", score)
        }
        return parseTracking(client.raw("/api/titles/$id/tracking", "POST", body.toString()))
    }

    /** BLOCKS up to ~45s server-side while the debrid pipeline resolves. */
    suspend fun play(id: Int, ep: Int): ResolvedStream = client.get("/api/titles/$id/play/$ep")

    // --- playback resume (server-owned, so web and native share it) ---------

    suspend fun resumePoints(id: Int): Map<String, ResumePoint> =
        client.get("/api/titles/$id/resume")

    suspend fun saveResume(id: Int, ep: Int, positionMs: Long, durationMs: Long) {
        client.raw(
            "/api/titles/$id/resume/$ep", "POST",
            buildJsonObject {
                put("positionMs", positionMs)
                put("durationMs", durationMs)
            }.toString(),
        )
    }

    suspend fun clearResume(id: Int, ep: Int) {
        client.raw("/api/titles/$id/resume/$ep", "DELETE")
    }

    /**
     * Resolve several episodes at once, so a season download does not pay
     * ~45s per episode serially. Answers within ~55s; episodes that miss the
     * deadline come back retriable and keep resolving server-side into the
     * 8-minute stream cache. 404s on servers predating the endpoint.
     */
    suspend fun playBatch(id: Int, episodes: List<Int>): BatchPlayResponse =
        client.post("/api/titles/$id/play", buildJsonObject {
            put("episodes", kotlinx.serialization.json.JsonArray(episodes.map { JsonPrimitive(it) }))
        }.toString())

    /**
     * Local file + caption URLs signed with per-path download tokens, for the
     * offline downloader. Throws ApiError(409) when the episode is debrid-only.
     */
    suspend fun offlineLinks(id: Int, ep: Int): OfflineLinks =
        client.get("/api/titles/$id/offline/$ep")

    suspend fun markWatched(id: Int, ep: Int): WatchedResponse =
        client.post("/api/titles/$id/watched/$ep")

    suspend fun setProgress(id: Int, ep: Int): ProgressResponse {
        val body = buildJsonObject { put("ep", ep) }
        return client.post("/api/titles/$id/progress", body.toString())
    }

    /** Absolute, cookie-authed URL for a subtitle track. */
    fun captionUrl(subId: String): String = client.absolute("/api/captions/$subId.vtt")

    // --- account settings (web-parity surface) ------------------------------
    /** Email for password-reset links; "" clears it. */
    suspend fun setEmail(email: String): PublicUser {
        val body = buildJsonObject { put("email", email) }
        return client.post("/api/account/email", body.toString())
    }

    suspend fun changePassword(current: String, next: String) {
        val body = buildJsonObject { put("currentPassword", current); put("newPassword", next) }
        client.raw("/api/account/password", "POST", body.toString())
    }

    /** base64 == null clears back to the initials avatar.
     *  Returns the normalized stored image (or nulls after a clear). */
    suspend fun setAvatar(base64: String?, contentType: String?): AvatarSetResult {
        val body = buildJsonObject {
            put("avatarBase64", base64 ?: "")
            if (contentType != null) put("contentType", contentType)
        }
        return client.post("/api/account/avatar", body.toString())
    }

    suspend fun gravatar(email: String): GravatarResult {
        val body = buildJsonObject { put("email", email) }
        return client.post("/api/account/avatar/gravatar", body.toString())
    }

    suspend fun apiKey(): ApiKeyInfo = client.get("/api/account/apikey")
    suspend fun rotateApiKey(): ApiKeyInfo = client.post("/api/account/apikey/rotate")

    suspend fun setRealDebrid(token: String): PublicUser {
        val body = buildJsonObject { put("token", token) }
        return client.post<PublicUser>("/api/account/realdebrid", body.toString())
    }
    suspend fun setAllDebrid(key: String): PublicUser {
        val body = buildJsonObject { put("key", key) }
        return client.post<PublicUser>("/api/account/alldebrid", body.toString())
    }
    suspend fun setJimaku(key: String): PublicUser {
        val body = buildJsonObject { put("key", key) }
        return client.post<PublicUser>("/api/account/jimaku", body.toString())
    }
    suspend fun setPreferredDebrid(provider: String?): PublicUser {
        val body = buildJsonObject { put("provider", provider ?: "") }
        return client.post<PublicUser>("/api/account/debrid", body.toString())
    }
    suspend fun setTrackerToken(anilist: String? = null, mal: String? = null): PublicUser {
        val body = buildJsonObject {
            if (anilist != null) put("anilistToken", anilist)
            if (mal != null) put("malToken", mal)
        }
        return client.post<PublicUser>("/api/account/trackers", body.toString())
    }

    suspend fun oauthStart(provider: String): OAuthStart =
        client.post("/api/account/oauth/$provider/start")

    /**
     * null while the auth-site flow is still pending.
     *
     * Pending is a 200 carrying `{pending:true}`, NOT a 404 — the previous
     * comment here claimed otherwise and the code decoded into a type with a
     * required `user`, so the ordinary "not yet" answer threw a deserialization
     * error on the very first poll and the link could never complete. The 404
     * arm is kept as belt-and-braces for an older server.
     */
    suspend fun oauthPoll(provider: String, state: String): PublicUser? {
        val body = buildJsonObject { put("state", state) }
        return try {
            client.post<OAuthPollResponse>("/api/account/oauth/$provider/poll", body.toString()).user
        } catch (e: ApiError) {
            if (e.status == 404) null else throw e
        }
    }

    /**
     * The server REBUILDS addDefaults from each POST body — always send the
     * complete state (a ccLang-only body would wipe track/folder/autoDownload).
     */
    suspend fun saveAddDefaults(
        track: String?,
        autoDownload: Boolean,
        folder: String?,
        ccLang: String? = null,
        autoStatus: Boolean? = null,
    ): PublicUser {
        val body = buildJsonObject {
            if (!track.isNullOrBlank()) put("track", track)
            if (autoDownload) put("autoDownload", true)
            if (!folder.isNullOrBlank()) put("folder", folder)
            if (ccLang != null) put("ccLang", ccLang)
            if (autoStatus != null) put("autoStatus", autoStatus)
        }
        return client.post<PublicUser>("/api/account/add-defaults", body.toString())
    }

    // --- staff: users & invites (web /users/) --------------------------------
    suspend fun users(): List<PublicUser> = client.get("/api/users")

    suspend fun createUser(username: String, password: String, role: String, email: String?): PublicUser {
        val body = buildJsonObject {
            put("username", username); put("password", password); put("role", role)
            if (!email.isNullOrBlank()) put("email", email)
        }
        return client.post("/api/users", body.toString())
    }

    /** Owner only. */
    suspend fun setUserRole(id: String, role: String): PublicUser {
        val body = buildJsonObject { put("role", role) }
        return client.post("/api/users/$id/role", body.toString())
    }

    /** Staff: block/allow a user's downloads (streaming stays allowed). */
    suspend fun setUserDownloads(id: String, denied: Boolean): PublicUser {
        val body = buildJsonObject { put("denied", denied) }
        return client.post("/api/users/$id/downloads", body.toString())
    }

    suspend fun deleteUser(id: String) { client.raw("/api/users/$id", "DELETE") }

    suspend fun invites(): List<InviteItem> = client.get("/api/invites")

    suspend fun createInvite(role: String, email: String?, username: String?): InviteItem {
        val body = buildJsonObject {
            put("role", role)
            if (!email.isNullOrBlank()) put("email", email)
            if (!username.isNullOrBlank()) put("username", username)
        }
        return client.post("/api/invites", body.toString())
    }

    suspend fun revokeInvite(token: String) { client.raw("/api/invites/$token", "DELETE") }

    // --- owner: SMTP (web /settings/) ---------------------------------------
    /** null when no SMTP is configured (the server returns a bare `null`). */
    suspend fun smtp(): SmtpSettings? = try {
        val raw = client.raw("/api/smtp")
        if (raw.trim() == "null" || raw.isBlank()) null else client.json.decodeFromString(raw)
    } catch (_: Exception) { null }

    suspend fun saveSmtp(
        host: String, port: Int, secure: Boolean, user: String, pass: String?, from: String,
    ) {
        val body = buildJsonObject {
            put("host", host); put("port", port); put("secure", secure)
            put("user", user); put("from", from)
            if (!pass.isNullOrBlank()) put("pass", pass) // blank = keep the stored one
        }
        client.raw("/api/smtp", "POST", body.toString())
    }

    suspend fun testSmtp(to: String) {
        val body = buildJsonObject { put("to", to) }
        client.raw("/api/smtp/test", "POST", body.toString())
    }

    // --- misc -------------------------------------------------------------
    suspend fun health(): HealthResponse = client.get("/api/health")

    companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}
