package dev.soffits.openplayer.automation;

public final class BodyLanguageInstructionParserTest {
    private BodyLanguageInstructionParserTest() {
    }

    public static void main(String[] args) {
        acceptsSupportedStrictGrammar();
        rejectsUnsupportedGesturesAndFreeFormText();
    }

    private static void acceptsSupportedStrictGrammar() {
        require(BodyLanguageInstructionParser.parseOrNull("") == BodyLanguageInstruction.IDLE,
                "blank body language must mean idle");
        require(BodyLanguageInstructionParser.parseOrNull("wave") == BodyLanguageInstruction.WAVE,
                "wave must be supported");
        require(BodyLanguageInstructionParser.parseOrNull("swing") == BodyLanguageInstruction.SWING,
                "swing must be supported");
        require(BodyLanguageInstructionParser.parseOrNull("crouch") == BodyLanguageInstruction.CROUCH,
                "crouch must be supported");
        require(BodyLanguageInstructionParser.parseOrNull("uncrouch") == BodyLanguageInstruction.UNCROUCH,
                "uncrouch must be supported");
        require(BodyLanguageInstructionParser.parseOrNull("look_owner") == BodyLanguageInstruction.LOOK_OWNER,
                "look_owner must be supported");
    }

    private static void rejectsUnsupportedGesturesAndFreeFormText() {
        require(BodyLanguageInstructionParser.parseOrNull("nod") == null,
                "nod must reject until a safe runtime implementation exists");
        require(BodyLanguageInstructionParser.parseOrNull("shake") == null,
                "shake must reject until a safe runtime implementation exists");
        require(BodyLanguageInstructionParser.parseOrNull("walk over there") == null,
                "body language must not accept movement-like prose");
        require(BodyLanguageInstructionParser.parseOrNull("wave now") == null,
                "body language must use a single strict token");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
