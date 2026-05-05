package dev.soffits.openplayer.character;

import java.io.IOException;

final class UnsafeCharacterPathException extends IOException {
    UnsafeCharacterPathException(String message) {
        super(message);
    }
}
