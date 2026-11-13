package com.example.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.domain.model.PdfToolId
import com.example.domain.model.ToolCategory
import com.example.ui.components.CenteredResponsiveContainer
import com.example.ui.components.PdfToolTopBar
import com.example.ui.localization.LocalAppStrings

@Composable
fun ToolsHubScreen(
    favoriteToolIds: Set<String>,
    onToggleFavoriteTool: (PdfToolId) -> Unit,
    onToolClick: (PdfToolId) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalAppStrings.current
    val allTools = rememberAllToolDescriptors(strings)
    var query by remember { mutableStateOf("") }

    val filtered = remember(allTools, query) {
        if (query.isBlank()) allTools
        else allTools.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.subtitle.contains(query, ignoreCase = true)
        }
    }

    val grouped = remember(filtered) {
        filtered.groupBy { it.toolId.category }
    }

    Column(modifier = modifier.fillMaxSize()) {
        PdfToolTopBar(title = strings.allPdfTools, subtitle = "20 specialized offline tools")

        CenteredResponsiveContainer(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tools_hub_search"),
                        placeholder = { Text(strings.searchToolsHint) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp)
                    )
                }

                ToolCategory.entries.forEach { category ->
                    val toolsInCategory = grouped[category].orEmpty()
                    if (toolsInCategory.isNotEmpty()) {
                        item(key = "cat_${category.name}") {
                            Text(
                                text = categoryTitle(category),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                        items(toolsInCategory, key = { it.toolId.id }) { tool ->
                            val isFav = tool.toolId.id in favoriteToolIds
                            ToolListTile(
                                tool = tool,
                                onClick = { onToolClick(tool.toolId) },
                                trailing = {
                                    IconButton(
                                        onClick = { onToggleFavoriteTool(tool.toolId) },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                            contentDescription = "Pin to favorites",
                                            tint = if (isFav) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun categoryTitle(category: ToolCategory): String = when (category) {
    ToolCategory.CREATE -> "Create & Generate"
    ToolCategory.SCAN_OCR -> "Scan & OCR"
    ToolCategory.EDIT -> "View & Annotate"
    ToolCategory.ORGANIZE -> "Organize Pages"
    ToolCategory.CONVERT -> "Convert & Compress"
    ToolCategory.SECURITY -> "Security & Signatures"
    ToolCategory.UTILITIES -> "Utilities & Inspection"
}
