package moderation_system;

public enum Color {
    // these also correspond to punishments
    // red for ban
    // orange for kick
    // and yellow for warn
    RED(0xE60026),
    ORANGE(0xFF8000),
    YELLOW(0xFFDD33);

    public final int value;

    Color(int i) {
        this.value = i;
    }
}
