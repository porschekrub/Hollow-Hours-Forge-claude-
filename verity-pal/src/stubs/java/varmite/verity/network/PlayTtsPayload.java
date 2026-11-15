package varmite.verity.network;

/** Stand-in (see README.md): "say this aloud" for the entity with that id (Verity, or the player when she is in a pocket). */
public record PlayTtsPayload(int entityId, String text) {}
