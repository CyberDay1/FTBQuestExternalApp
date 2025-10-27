package dev.ftbq.editor.ingest;

import java.util.Base64;

final class IconAssets {

    private IconAssets() {
        throw new AssertionError("IconAssets cannot be instantiated");
    }

    static final byte[] DEFAULT_ICON_BYTES = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR4nGNgYAAAAAMAASsJTYQAAAAASUVORK5CYII=");
}
