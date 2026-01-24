package moderation_system;

import mindustry.gen.Player;
import mindustry.net.Administration;

public class PunishmentTarget {
    String plainName;
    String name;
    String uuid;
    String ip;

    public PunishmentTarget(Player player) {
        this.plainName = player.plainName();
        this.name = player.name();
        this.uuid = player.uuid();
        this.ip = player.ip();
    }

    public PunishmentTarget(Administration.PlayerInfo player) {
        this.plainName = player.plainLastName();
        this.name = player.lastName;
        this.uuid = player.id;
        this.ip = player.lastIP;
    }
}
