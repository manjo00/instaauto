package com.autoinsta.ui.components

import java.io.File

/**
 * Turns whatever we have stored for a media item into something Coil can load.
 *
 * Two shapes reach the UI:
 * - `content://media/picker/...` — a file the user just chose, not yet imported.
 *   Coil loads that from a plain string.
 * - `/data/user/0/com.autoinsta/files/media/<uuid>.jpg` — a file we copied into app
 *   storage. A bare path has no scheme, so Coil cannot resolve it as a URI; it has
 *   to be handed a [File] instead.
 *
 * Getting this wrong shows an empty box rather than an error, which is exactly the
 * kind of silent failure that is hard to notice — hence a single shared helper.
 */
fun mediaModel(stored: String): Any =
    if (stored.startsWith("/")) File(stored) else stored
