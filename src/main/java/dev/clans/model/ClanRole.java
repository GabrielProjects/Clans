package dev.clans.model;

public enum ClanRole {
    LEADER,
    OFFICER,
    MEMBER;

    public boolean isAtLeast(ClanRole other) {
        return this.ordinal() <= other.ordinal();
    }
}
