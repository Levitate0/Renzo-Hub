package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.MediaGrid
import top.levitatemedia.renzo.tv.ui.components.isHidden
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

// ---------------------------------------------------------------------------
// Updates (app/updates/page.tsx): the 20sp/600 heading, the `view-sub`
// helper line, and the /api/updates feed as ribbon cards ("New · S#E#" →
// straight into playback, "New season · S#" / "Soon", "Available").
// ---------------------------------------------------------------------------

@Composable
fun UpdatesScreen(app: AppServices, onOpen: (CardItem) -> Unit) {
    var items by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        try {
            // The feed sends `kind`; normalize onto updKind so the ribbon and
            // the open-card handler (episode/movie → playback) both see it —
            // exactly the web's loadUpdates mapping (updKind: u.kind).
            items = app.repo.updates().map {
                if (it.updKind == null && it.kind != null) it.copy(updKind = it.kind) else it
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = updatesErrorMessage(e)
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text("Updates", color = RenzoColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        // `view-sub mt-0.5 text-[13px] text-muted-foreground`
        Text(
            "New episodes & seasons for the anime and movies in your library.",
            color = RenzoColors.MutedForeground,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        when {
            error != null -> ErrorBox(message = error!!, onRetry = { retryKey++ }, autoFocus = app.isTv)
            else -> {
                val level = app.contentLevel.value
                MediaGrid(
                    items = items.filter { !isHidden(it, level) },
                    onOpen = onOpen,
                    loading = loading,
                    empty = "You're all caught up — no new episodes or seasons.",
                )
            }
        }
    }
}

private fun updatesErrorMessage(e: Exception): String = when {
    e is ApiError && e.status == 0 -> "Can't reach your Renzo server — check the connection."
    e is ApiError && e.status == 401 -> "Session expired — sign in again from the account menu."
    else -> e.message ?: "Something went wrong."
}
