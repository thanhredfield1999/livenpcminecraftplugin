package vn.heomc.livingnpc;

record ProfessionDiagnostic(Level level, String message) {
    enum Level {
        OK,
        WAITING,
        ERROR
    }
}
