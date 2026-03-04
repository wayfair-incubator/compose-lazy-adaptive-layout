package com.lazyadaptivelayout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.lazyadaptivelayout.model.ContentPadding
import com.lazyadaptivelayout.model.EdgeSpacing

/**
 * Simple deterministic previews for each grid type to visually inspect in IDE.
 * These are also used by snapshot tests to render consistent output.
 */

@Composable
private fun ColoredItem(label: String, bg: Color) {
    Box(
        modifier = Modifier
            .background(bg)
            .fillMaxSize(),
        contentAlignment = Alignment.Center
    ) { Text(text = label, color = Color.Black) }
}

@Preview(name = "Staggered Grid", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewStaggeredGrid() {
    val state = rememberAdaptiveGridState()
    LazyAdaptiveGrid(state = state) {
        staggeredItems(items = (0..9).toList(), columns = 2, height = { _, index -> if (index % 2 == 0) 120.dp else 80.dp },
            edgeSpacing = EdgeSpacing(8.dp, 8.dp, 8.dp, 8.dp),
            contentPadding = ContentPadding(4.dp, 4.dp)
        ) { item, index, columnIndex, _ ->
            ColoredItem(label = "S#$index C$columnIndex", bg = Color(0xFFE3F2FD))
        }
    }
}

@Preview(name = "Uniform Grid", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewUniformGrid() {
    val state = rememberAdaptiveGridState()
    LazyAdaptiveGrid(state = state) {
        items(items = (0..11).toList(), columns = 3, height = { _, _ -> 90.dp },
            edgeSpacing = EdgeSpacing(8.dp, 8.dp, 8.dp, 8.dp),
            contentPadding = ContentPadding(4.dp, 4.dp)
        ) { _, index, columnIndex, _ ->
            ColoredItem(label = "U#$index C$columnIndex", bg = Color(0xFFE8F5E8))
        }
    }
}

@Preview(name = "Full Width Items", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewFullWidthGrid() {
    val state = rememberAdaptiveGridState()
    LazyAdaptiveGrid(state = state) {
        for (i in 0..5) {
            item(item = i, height = { _, _ -> 64.dp },
                edgeSpacing = EdgeSpacing(8.dp, 8.dp, 8.dp, 8.dp),
                contentPadding = ContentPadding(4.dp, 4.dp)
            ) { _, index, _, _ ->
                ColoredItem(label = "F#$index", bg = Color(0xFFFFF3E0))
            }
        }
    }
}

@Preview(name = "Custom Span Grid", showBackground = true, backgroundColor = 0xFFFFFFFF)
@Composable
fun PreviewCustomSpanGrid() {
    val state = rememberAdaptiveGridState()
    LazyAdaptiveGrid(state = state) {
        custom(
            columns = 3, // Custom layout with 3 columns total
            edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            contentPadding = ContentPadding(horizontal = 2.dp, vertical = 2.dp),
        ) {
            item(
                item = 60,
                span = 2,
                height = 300.dp,
            ) { item, index, columnIndex, columnCounts ->
                ColoredItem(label = "C#$index C$columnIndex", bg = Color(0xFFF3E5F5))
            }

            item(
                item = 61,
                span = 1,
                height = 150.dp,
            ) { item, index, columnIndex, columnCounts ->
                ColoredItem(label = "C#$index C$columnIndex", bg = Color(0xFFF3E5F5))
            }

            item(
                item = 65,
                span = 1,
                height = 150.dp,
            ) { item, index, columnIndex, columnCounts ->
                ColoredItem(label = "C#$index C$columnIndex", bg = Color(0xFFF3E5F5))
            }

            item(
                item = 62,
                span = 3, // This item will span all 3 columns
                height = 100.dp, // Full-width item gets more height
            ) { item, index, columnIndex, columnCounts ->
                ColoredItem(label = "C#$index C$columnIndex", bg = Color(0xFFF3E5F5))
            }

            item(
                item = 63,
                span = 1,
            ) { item, index, columnIndex, columnCounts ->
                ColoredItem(label = "C#$index C$columnIndex", bg = Color(0xFFF3E5F5))
            }

            item(
                item = 64,
                span = 2,
            ) { item, index, columnIndex, columnCounts ->
                ColoredItem(label = "C#$index C$columnIndex", bg = Color(0xFFF3E5F5))
            }
        }
    }
}