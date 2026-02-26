package com.lazyadaptivelayout

import app.cash.paparazzi.Paparazzi
import org.junit.Rule
import org.junit.Test

class LazyAdaptiveGridPaparazziTest {

    @get:Rule
    val paparazzi = Paparazzi()

    @Test
    fun snapshot_staggered_preview() {
        paparazzi.snapshot {
            PreviewStaggeredGrid()
        }
    }

    @Test
    fun snapshot_uniform_preview() {
        paparazzi.snapshot {
            PreviewUniformGrid()
        }
    }

    @Test
    fun snapshot_full_width_preview() {
        paparazzi.snapshot {
            PreviewFullWidthGrid()
        }
    }

    @Test
    fun snapshot_custom_span_preview() {
        paparazzi.snapshot {
            PreviewCustomSpanGrid()
        }
    }
}
