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
// History (app/history/page.tsx): the 20sp/600 "Watch history" heading, the
// `view-sub` helper line and a plain cover grid of GET /api/history.
// ---------------------------------------------------------------------------

@Composable
fun HistoryScreen(app: AppServices, onOpen: (CardItem) -> Unit) {
    var items by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        try {
            items = app.repo.history()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = historyErrorMessage(e)
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        Text("Watch history", color = RenzoColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        // `view-sub mt-0.5 text-[13px] text-muted-foreground`
        Text(
            "Recently watched — most recent first.",
            color = RenzoColors.MutedForeground,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
        when {
            error != null -> ErrorBox(message = error!!, onRetry = { retryKey++ })
            else -> {
                val level = app.contentLevel.value
                MediaGrid(
                    items = items.filter { !isHidden(it, level) },
                    onOpen = onOpen,
                    loading = loading,
                    empty = "Nothing watched yet — mark episodes watched or finish one in the player.",
                )
            }
        }
    }
}

private fun historyErrorMessage(e: Exception): String = when {
    e is ApiError && e.status == 0 -> "Can't reach your Renzo server — check the connection."
    e is ApiError && e.status == 401 -> "Session expired — sign in again from the account menu."
    else -> e.message ?: "Something went wrong."
}
