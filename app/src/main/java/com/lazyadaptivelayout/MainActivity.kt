package com.lazyadaptivelayout

import android.os.Bundle

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lazyadaptivelayout.model.ContentPadding
import com.lazyadaptivelayout.model.EdgeSpacing

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LazyNavigationApp()
        }
    }
}

/**
 * Reusable card rendering function for grid items
 */
@Composable
fun <T> GridCard(
    item: T,
    index: Int,
    columnIndex: Int,
    itemType: String,
    backgroundColor: Color,
    textColor: Color,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor, shape = RectangleShape),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(
                space = 4.dp,
                alignment = Alignment.CenterVertically
            ),
        ) {
            Text(
                text = itemType,
                color = textColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp)
            )
            Text(
                text = item.toString(),
                textAlign = TextAlign.Center,
                color = textColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp)
            )
            Text(
                text = "Col: $columnIndex",
                color = textColor,
                fontSize = 12.sp,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}

@Composable
fun PreviewLazyAdaptiveGrid(
    modifier: Modifier = Modifier,
    onItemClick: () -> Unit = {}
) {
    val gridState = rememberAdaptiveGridState()

    LazyAdaptiveGrid(
        state = gridState,
        modifier = modifier.fillMaxSize(),
        content = {
            // Staggered layout - Pinterest-style grid
            staggeredItems(
                items = (0..24).toList(),
                columns = 3,
                height = { item, index ->
                    if (index % 2 == 0) 300.dp else 200.dp
                },
                edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                contentPadding = ContentPadding(horizontal = 8.dp, vertical = 8.dp)
            ) { item, index, columnIndex, columnCounts ->
                GridCard(
                    item = item,
                    index = index,
                    columnIndex = columnIndex,
                    itemType = "Staggered",
                    backgroundColor = Color(0xFFE3F2FD),
                    textColor = Color(0xFF1976D2),
                    onClick = onItemClick
                )
            }

            // Full-width items - span entire grid width
            for (itemValue in (25..34)) {
                item(
                    item = itemValue,
                    height = { item, index -> if (index == 33) 0.dp else 120.dp },
                    edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    contentPadding = ContentPadding(horizontal = 8.dp, vertical = 8.dp)
                ) { item, index, columnIndex, columnCounts ->
                    GridCard(
                        item = item,
                        index = index,
                        columnIndex = columnIndex,
                        itemType = "FullWidth",
                        backgroundColor = Color(0xFFFFF3E0),
                        textColor = Color(0xFFF57C00),
                        onClick = onItemClick
                    )
                }
            }

            // Uniform grid layout - consistent row heights
            items(
                items = (35..59).toList(),
                columns = 2,
                height = { item, index ->
                    if (index % 4 == 0) 250.dp else 200.dp
                },
                edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                contentPadding = ContentPadding(horizontal = 4.dp, vertical = 4.dp)
            ) { item, index, columnIndex, columnCounts ->
                GridCard(
                    item = item,
                    index = index,
                    columnIndex = columnIndex,
                    itemType = "Uniform",
                    backgroundColor = Color(0xFFE8F5E8),
                    textColor = Color(0xFF388E3C),
                    onClick = onItemClick
                )
            }

            // Custom layout with different spans - magazine-style layout
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
                    GridCard(
                        item = item,
                        index = index,
                        columnIndex = columnIndex,
                        itemType = "Custom($columnIndex)",
                        backgroundColor = Color(0xFFF3E5F5),
                        textColor = Color(0xFF7B1FA2),
                        onClick = onItemClick
                    )
                }

                item(
                    item = 61,
                    span = 1,
                    height = 150.dp,
                ) { item, index, columnIndex, columnCounts ->
                    GridCard(
                        item = item,
                        index = index,
                        columnIndex = columnIndex,
                        itemType = "Custom($columnIndex)",
                        backgroundColor = Color(0xFFF3E5F5),
                        textColor = Color(0xFF7B1FA2),
                        onClick = onItemClick
                    )
                }

                item(
                    item = 65,
                    span = 1,
                    height = 150.dp,
                ) { item, index, columnIndex, columnCounts ->
                    GridCard(
                        item = item,
                        index = index,
                        columnIndex = columnIndex,
                        itemType = "Custom($columnIndex)",
                        backgroundColor = Color(0xFFF3E5F5),
                        textColor = Color(0xFF7B1FA2),
                        onClick = onItemClick
                    )
                }

                item(
                    item = 62,
                    span = 3, // This item will span all 3 columns
                    height = 100.dp, // Full-width item gets more height
                ) { item, index, columnIndex, columnCounts ->
                    GridCard(
                        item = item,
                        index = index,
                        columnIndex = columnIndex,
                        itemType = "Custom($columnIndex)",
                        backgroundColor = Color(0xFFF3E5F5),
                        textColor = Color(0xFF7B1FA2),
                        onClick = onItemClick
                    )
                }

                item(
                    item = 63,
                    span = 1,
                ) { item, index, columnIndex, columnCounts ->
                    GridCard(
                        item = item,
                        index = index,
                        columnIndex = columnIndex,
                        itemType = "Custom($columnIndex)",
                        backgroundColor = Color(0xFFF3E5F5),
                        textColor = Color(0xFF7B1FA2),
                        onClick = onItemClick
                    )
                }

                item(
                    item = 64,
                    span = 2,
                ) { item, index, columnIndex, columnCounts ->
                    GridCard(
                        item = item,
                        index = index,
                        columnIndex = columnIndex,
                        itemType = "Custom($columnIndex)",
                        backgroundColor = Color(0xFFF3E5F5),
                        textColor = Color(0xFF7B1FA2),
                        onClick = onItemClick
                    )
                }
            }

            // Second staggered layout - 2 columns with varied heights
            staggeredItems(
                items = (70..84).toList(),
                columns = 2,
                height = { item, index ->
                    when {
                        index % 3 == 0 -> 350.dp
                        index % 2 == 0 -> 250.dp
                        else -> 180.dp
                    }
                },
                edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                contentPadding = ContentPadding(horizontal = 4.dp, vertical = 4.dp)
            ) { item, index, columnIndex, columnCounts ->
                GridCard(
                    item = item,
                    index = index,
                    columnIndex = columnIndex,
                    itemType = "Staggered",
                    backgroundColor = Color(0xFFE3F2FD),
                    textColor = Color(0xFF1976D2),
                    onClick = onItemClick
                )
            }

            // Final uniform grid layout
            items(
                items = (85..99).toList(),
                columns = 3,
                height = { item, index ->
                    if (index % 5 == 0) 150.dp else 120.dp
                },
                edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                contentPadding = ContentPadding(horizontal = 4.dp, vertical = 4.dp)
            ) { item, index, columnIndex, columnCounts ->
                GridCard(
                    item = item,
                    index = index,
                    columnIndex = columnIndex,
                    itemType = "Uniform",
                    backgroundColor = Color(0xFFE8F5E8),
                    textColor = Color(0xFF388E3C),
                    onClick = onItemClick
                )
            }
        }
    )
}

@Composable
fun LazyNavigationApp() {
    var currentScreen by remember { mutableStateOf(Screen.Main) }
    
    when (currentScreen) {
        Screen.Main -> {
            MainScreen(
                onNavigateToSecond = { 
                    currentScreen = Screen.Second 
                }
            )
        }
        Screen.Second -> {
            SecondScreen(
                onNavigateBack = { 
                    currentScreen = Screen.Main 
                }
            )
        }
    }
}

enum class Screen {
    Main, Second
}

@Composable
fun MainScreen(onNavigateToSecond: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Navigation header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1976D2))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Main Screen - LazyAdaptiveGrid",
                color = Color.White,
                fontSize = 18.sp
            )
        }
        
        // Navigation button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF4CAF50))
                .clickable { onNavigateToSecond() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Navigate to Second Screen",
                color = Color.White,
                fontSize = 16.sp
            )
        }
        
        // Main grid content
        PreviewLazyAdaptiveGrid(
            modifier = Modifier.weight(1f),
            onItemClick = { onNavigateToSecond() }
        )
    }
}

@Composable
fun SecondScreen(onNavigateBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Navigation header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF7B1FA2))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Second Screen - LazyAdaptiveGrid",
                color = Color.White,
                fontSize = 18.sp
            )
        }
        
        // Navigation button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFF9800))
                .clickable { onNavigateBack() }
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Navigate Back to Main Screen",
                color = Color.White,
                fontSize = 16.sp
            )
        }
        
        // Second grid content
        SecondActivityLazyAdaptiveGrid(
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SecondActivityLazyAdaptiveGrid(
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val gridState = rememberAdaptiveGridState()

    // Pagination state
    var items by remember { mutableStateOf((0..24).toMutableList()) }
    var isLoading by remember { mutableStateOf(false) }
    val loadMoreThreshold = 4 // start loading when 4 items from the end

    // Trigger load more when approaching the end
    val lastVisible = gridState.lastVisibleItemIndex
    LaunchedEffect(lastVisible, items.size) {
        val shouldLoadMore =
            !isLoading && items.isNotEmpty() && lastVisible >= items.size - loadMoreThreshold - 1
        if (shouldLoadMore) {
            isLoading = true
            // Simulate loading next page by appending 25 more items
            val start = items.lastOrNull()?.plus(1) ?: 0
            val newItems = (start until start + 25).toList()
            items = (items + newItems).toMutableList()
            isLoading = false
        }
    }

    // Create a content lambda keyed by the current item count so the library (which caches by lambda)
    // rebuilds its internal groups when pagination appends data.
    val gridContent: AdaptiveGridScope<Int>.() -> Unit = remember(items.size) {
        {
            // Staggered layout - Pinterest-style grid
            staggeredItems(
                items = items,
                columns = 2,
                height = { _, index ->
                    if (index % 2 == 0) 300.dp else 200.dp
                },
                edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                contentPadding = ContentPadding(horizontal = 4.dp, vertical = 4.dp)
            ) { item, index, columnIndex, _ ->
                GridCard(
                    item = item,
                    index = index,
                    columnIndex = columnIndex,
                    itemType = "Staggered",
                    backgroundColor = Color(0xFFE3F2FD),
                    textColor = Color(0xFF1976D2),
                    onClick = onClick
                )
            }
        }
    }

    LazyAdaptiveGrid(
        state = gridState,
        modifier = modifier.fillMaxSize(),
        bufferSize = items.size,
        content = gridContent
    )
}