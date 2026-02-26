# LazyAdaptiveLayout
A high-performance, customizable adaptive grid layout for Android Jetpack Compose with lazy loading and adaptive scroll optimization. Supports multiple layout types including staggered, uniform, full-width, and custom span layouts with an intuitive DSL API.

## Project Structure
This project includes both the **adaptive-grid library module** and a **demo app** to showcase the library's capabilities.

## Demo

https://github.com/user-attachments/assets/214841b7-5178-46c7-afee-fdf20bcd3d72


## Features

- **Multiple Layout Types**: Support for Staggered, Uniform, Full Width, and Custom span layouts
- **DSL API**: Intuitive scope-based DSL similar to LazyColumn and LazyVerticalGrid
- **Lazy Loading**: Efficient rendering with viewport-based item loading
- **Adaptive Scroll Optimization**: Dynamic buffer sizing based on scroll velocity
- **Flexible Configuration**: Customizable spacing, padding, and item dimensions
- **Type-Safe**: Generic implementation supporting any data type
- **Performance Optimized**: Minimal recomposition and efficient memory usage
- **Per-Item Height Control**: Individual height control for each item within groups

## Layout Types

### Staggered Layout (`staggeredItems`)
Items are placed in the shortest column, creating a Pinterest-style masonry layout with varying heights.

### Uniform Grid Layout (`items`)
Items in the same row have consistent heights, creating a uniform grid appearance.

### Full Width Items (`item`)
Individual items that span the entire width of the container.

### Custom Span Layout (`custom`)
Items can span multiple columns based on individual span configuration, perfect for magazine-style collage layouts.

## Quick Start

### Basic Usage

```kotlin
@Composable
fun MyGrid() {
    LazyAdaptiveGrid(
        modifier = Modifier.fillMaxSize(),
        content = {
            // Staggered layout - Pinterest-style grid
            staggeredItems(
                items = (0..24).toList(),
                columns = 3,
                height = { item, index -> 
                    if (index % 2 == 0) 300.dp else 200.dp
                }
            ) { item, index, columnIndex, columns ->
                // Your item content here
                Text("Item $item")
            }
        }
    )
}
```

### Complete Example

```kotlin
@Composable
fun PreviewLazyAdaptiveGrid() {
    val gridState = rememberAdaptiveGridState()

    LazyAdaptiveGrid(
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        content = {
            // Staggered layout - Pinterest-style grid
            staggeredItems(
                items = (0..24).toList(),
                columns = 3,
                height = { item, index -> 
                    if (index % 2 == 0) 300.dp else 200.dp
                },
                edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                contentPadding = ContentPadding(horizontal = 4.dp, vertical = 4.dp)
            ) { item, index, columnIndex, columns ->
                GridCard(item, index, columnIndex, "Staggered")
            }

            // Full-width items - span entire grid width
            for (itemValue in (25..34)) {
                item(
                    item = itemValue,
                    edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    contentPadding = ContentPadding(horizontal = 8.dp, vertical = 8.dp)
                ) { item, index, columnIndex, columns ->
                    GridCard(item, index, columnIndex, "FullWidth")
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
            ) { item, index, columnIndex, columns ->
                GridCard(item, index, columnIndex, "Uniform")
            }

            // Custom layout with different spans - magazine-style layout
            custom(
                columns = 3,
                edgeSpacing = EdgeSpacing(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
                contentPadding = ContentPadding(horizontal = 2.dp, vertical = 2.dp)
            ) {
                item(
                    item = 60,
                    span = 2,
                    height = 300.dp
                ) { item, index, columnIndex, columns ->
                    GridCard(item, index, columnIndex, "Custom")
                }

                item(
                    item = 61,
                    span = 1,
                    height = 150.dp
                ) { item, index, columnIndex, columns ->
                    GridCard(item, index, columnIndex, "Custom")
                }

                item(
                    item = 62,
                    span = 3 // Spans all columns
                ) { item, index, columnIndex, columns ->
                    GridCard(item, index, columnIndex, "Custom")
                }
            }
        }
    )
}
```

## API Reference

### LazyAdaptiveGrid

The main composable that creates the adaptive grid layout.

```kotlin
@Composable
fun <T> LazyAdaptiveGrid(
    modifier: Modifier = Modifier,
    state: AdaptiveGridState = rememberAdaptiveGridState(),
    bufferSize: Int = 2,
    content: AdaptiveGridScope<T>.() -> Unit
)
```

**Parameters:**
- `modifier`: Modifier to be applied to the grid
- `state`: The state object to hold grid state
- `bufferSize`: Number of items to render outside the viewport for smooth scrolling
- `content`: The DSL content that defines different layout types with inline content

### Layout Functions

#### staggeredItems()
Creates a staggered layout where items are placed in the shortest column (Pinterest-style).

```kotlin
fun staggeredItems(
    items: List<T>,
    columns: Int,
    height: (T, Int) -> Dp = { _, _ -> Dp.Unspecified },
    edgeSpacing: EdgeSpacing = EdgeSpacing(),
    contentPadding: ContentPadding = ContentPadding(),
    key: (T, Int) -> Any = { item, _ -> item.hashCode() },
    content: @Composable (T, Int, Int, Int) -> Unit
)
```

#### items()
Creates a uniform grid layout where items in the same row have consistent heights.

```kotlin
fun items(
    items: List<T>,
    columns: Int,
    height: (T, Int) -> Dp = { _, _ -> Dp.Unspecified },
    edgeSpacing: EdgeSpacing = EdgeSpacing(),
    contentPadding: ContentPadding = ContentPadding(),
    key: (T, Int) -> Any = { item, _ -> item.hashCode() },
    content: @Composable (T, Int, Int, Int) -> Unit
)
```

#### item()
Creates a single full-width item that spans the entire width.

```kotlin
fun item(
    item: T,
    height: (T, Int) -> Dp = { _, _ -> Dp.Unspecified },
    edgeSpacing: EdgeSpacing = EdgeSpacing(),
    contentPadding: ContentPadding = ContentPadding(),
    key: (T, Int) -> Any = { item, _ -> item.hashCode() },
    content: @Composable (T, Int, Int, Int) -> Unit
)
```

#### custom()
Creates a custom layout where each item can have individual spans for magazine-style layouts.

```kotlin
fun custom(
    columns: Int,
    height: (T, Int) -> Dp = { _, _ -> Dp.Unspecified },
    edgeSpacing: EdgeSpacing = EdgeSpacing(),
    contentPadding: ContentPadding = ContentPadding(),
    key: (T, Int) -> Any = { item, _ -> item.hashCode() },
    content: CustomGroupScope<T>.() -> Unit
)
```

**Custom Layout Items:**
```kotlin
fun item(
    item: T,
    span: Int,
    height: Dp? = null,
    content: @Composable (T, Int, Int, Int) -> Unit
)
```

### Configuration Classes

#### EdgeSpacing
Controls spacing around the edges of items:
```kotlin
data class EdgeSpacing(
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp
)
```

#### ContentPadding
Controls spacing between items:
```kotlin
data class ContentPadding(
    val horizontal: Dp = 0.dp,
    val vertical: Dp = 0.dp
)
```

## Architecture

The project follows a modular architecture with clear separation of concerns:

```
adaptive-grid/src/main/java/com/lazyadaptivelayout/
├── model/           # Data models and configurations
├── provider/        # Layout providers and interfaces
├── layout/          # Layout measurement and calculation
├── placement/       # Item placement logic
└── state/           # State management

app/src/main/java/com/lazyadaptivelayout/
└── MainActivity.kt  # Demo app showcasing all layout types
```

### Key Components

- **LazyAdaptiveGrid**: Main composable implementing the grid layout
- **AdaptiveGridScope**: DSL interface for defining grid groups
- **AdaptiveGridState**: State management for scroll position and caching
- **GridItemConfig**: Configuration for individual items
- **ItemPosition**: Position and size information for layout calculations

## Performance Features

- **Lazy Loading**: Only renders items visible in the viewport
- **Adaptive Buffer**: Dynamic buffer sizing based on scroll velocity
- **Memory Efficient**: Minimal object allocation and intelligent caching
- **Smooth Scrolling**: Optimized for 60fps performance on all devices
- **Two-Pass Measurement**: Efficient height calculation for wrap content items
- **Layout Optimization**: Separate layouts for different grid patterns
- **Recomposition Avoidance**: Minimal recomposition through stable keys

## Requirements

- Jetpack Compose BOM 2024.09.00 (UI 1.7.x)
- Kotlin 2.0.21
- Android Gradle Plugin 8.9.2
- JDK 21

## Installation

Use via Gradle (preferred):

- Ensure Maven Central is in your repositories (usually default):

```
repositories {
    mavenCentral()
}
```

- Add the dependency to your module build.gradle(.kts):

Kotlin DSL (build.gradle.kts):
```
dependencies {
    implementation("com.lazyadaptivelayout:adaptive-grid:1.0.1")
}
```

Groovy DSL (build.gradle):
```
dependencies {
    implementation 'com.lazyadaptivelayout:adaptive-grid:1.0.1'
}
```

Or run the demo locally:

1. Clone the repository
2. Open in Android Studio
3. Build and run the project
4. The `adaptive-grid` module contains the library code
5. The `app` module contains the demo application

ProGuard/R8: No special rules are required; a consumer ProGuard file is provided in the library module.

## Acknowledgments

- Built with Jetpack Compose
- Inspired by modern grid layout patterns (Pinterest, Instagram, etc.)
- Optimized for mobile performance
- Follows Android library best practices

## Testing

This project includes a comprehensive test suite for the adaptive-grid library.

- Run all JVM unit tests (includes Compose + Robolectric + Paparazzi):
  - ./gradlew :adaptive-grid:testDebugUnitTest
- Run instrumented Android tests (requires a device/emulator):
  - ./gradlew :adaptive-grid:connectedAndroidTest

### Paparazzi snapshot tests (no emulator required)
Paparazzi renders Compose previews on the JVM and compares them to checked-in goldens.

- Record (create/update) snapshots:
  - ./gradlew :adaptive-grid:testDebugUnitTest -Ppaparazzi.record
  - Newly recorded PNGs will be written under adaptive-grid/src/test/snapshots/images/
- Verify against existing snapshots (default when not recording):
  - ./gradlew :adaptive-grid:testDebugUnitTest
- Update goldens after intentional UI changes:
  - Re-run with -Ppaparazzi.record and commit the updated PNGs.
- Inspect failure artifacts:
  - Check adaptive-grid/build/paparazzi/ for diffs and rendered outputs when a test fails.

Snapshots currently cover these previews:
- PreviewStaggeredGrid
- PreviewUniformGrid
- PreviewFullWidthGrid
- PreviewCustomSpanGrid

### Compose previews
For quick visual checks in IDE, use the composables in adaptive-grid/src/main/java/com/lazyadaptivelayout/Previews.kt.

- PreviewStaggeredGrid
- PreviewUniformGrid
- PreviewFullWidthGrid
- PreviewCustomSpanGrid

### Notes
- Unit tests for core logic live under adaptive-grid/src/test (e.g., GridMeasurementTest, PlacementScopesTest, AdaptiveGridStateTest, AdaptiveGridScope tests).
- An instrumentation-based snapshot test also exists under adaptive-grid/src/androidTest for on-device validation.
