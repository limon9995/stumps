package com.mdlimonhossain.stumps.ui.news

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mdlimonhossain.stumps.StumpsApplication
import com.mdlimonhossain.stumps.data.remote.news.NewsArticle
import com.mdlimonhossain.stumps.ui.designsystem.AppCard
import com.mdlimonhossain.stumps.ui.designsystem.EmptyState
import com.mdlimonhossain.stumps.ui.designsystem.staggeredEntrance

private sealed interface NewsLoadState {
    data object Loading : NewsLoadState
    data class Error(val message: String) : NewsLoadState
    data class Loaded(val articles: List<NewsArticle>) : NewsLoadState
}

/**
 * A real cricket news feed — fetched live from ESPN Cricinfo's public RSS feed (see
 * NewsRepository), not a mock/placeholder. Tapping an article opens it in the phone's browser,
 * since there's no in-app article reader here — RSS only gives us a title/summary/link, not the
 * full article body.
 */
@Composable
fun NewsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as StumpsApplication
    var loadState by remember { mutableStateOf<NewsLoadState>(NewsLoadState.Loading) }

    LaunchedEffect(Unit) {
        loadState = runCatching { app.newsRepository.fetchLatest() }
            .fold(
                onSuccess = { NewsLoadState.Loaded(it) },
                onFailure = { e -> NewsLoadState.Error(e.message ?: "নিউজ লোড করা যায়নি") }
            )
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TextButton(onClick = onBack) { Text("← ফিরে যাও") }
        Spacer(Modifier.height(8.dp))
        Text(text = "Cricket News", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        when (val state = loadState) {
            is NewsLoadState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is NewsLoadState.Error -> EmptyState(
                icon = Icons.AutoMirrored.Filled.List,
                title = "নিউজ লোড করা যায়নি",
                subtitle = state.message,
                actionLabel = "আবার চেষ্টা করো",
                onAction = { loadState = NewsLoadState.Loading }
            )
            is NewsLoadState.Loaded -> if (state.articles.isEmpty()) {
                EmptyState(icon = Icons.AutoMirrored.Filled.List, title = "কোনো খবর পাওয়া যায়নি")
            } else {
                LazyColumn {
                    itemsIndexed(state.articles, key = { _, a -> a.link }) { index, article ->
                        ArticleCard(article, modifier = Modifier.staggeredEntrance(index)) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleCard(article: NewsArticle, modifier: Modifier = Modifier, onClick: () -> Unit) {
    AppCard(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp), onClick = onClick) {
        Text(text = article.title, fontWeight = FontWeight.Bold)
        if (article.description.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(text = article.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3)
        }
        article.pubDate?.let {
            Spacer(Modifier.height(6.dp))
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
